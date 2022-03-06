package rollcalc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Calc {
	
	public static class Target {
		int hp;
		int armor;

		public Target(int hp, int armor) {
			this.hp = hp;
			this.armor = armor;
		}
		
		
	}
	
	public static class HitChance {
		double chance;
		int dmg;
		public HitChance(double chance, int dmg) {
			this.chance = chance;
			this.dmg = dmg;
		}
	}

	private static void calculate(double nodeChance, int prevDmg, int depth, HitChance[] chanceArray, double[] results, int hp, int depthLimit) {
		int newDepth = depth+1;
		for(HitChance c : chanceArray) {
			int totalDmg = prevDmg + c.dmg;
			double branchChance = nodeChance * c.chance;
			if(totalDmg>=hp){
				results[newDepth] += branchChance;
			} else if(newDepth==depthLimit){
				results[0] += branchChance;
			} else {
				calculate(branchChance, totalDmg, newDepth, chanceArray, results, hp, depthLimit);
			}
		}
	}
	
	
	private static class Branch implements Callable<double[]> {

		private final double bNodeChance;
		private final int bDmg;
		private final int bDepth;
		private final HitChance[] chanceArray;
		private final int hp;
		private final int depthLimit;
		
		public Branch(double bNodeChance, int bDmg, int bDepth, HitChance[] chanceArray, int hp, int depthLimit) {
			this.bNodeChance = bNodeChance;
			this.bDmg = bDmg;
			this.bDepth = bDepth;
			this.chanceArray = chanceArray;
			this.hp = hp;
			this.depthLimit = depthLimit;
		}

		@Override
		public double[] call() throws Exception {
			double[] results = new double[depthLimit+1];
			calculate(bNodeChance, bDmg, bDepth, chanceArray, results, hp, depthLimit);
			return results;
		}

	}
	
	private static List<Branch> split(int cores, double[] results, HitChance[] chanceArray, int hp, int depthLimit) {
		// unroll at least 4*cores count callables
		// be noted, 4*cores is an arbitrary chosen number
		int chances = chanceArray.length;
		
		int targetDepth = 1;
		while(Math.pow(chances, targetDepth)<cores*4) {
			targetDepth++;
		}
		
		List<Branch> result = new ArrayList<>();
		
		split(1.0, 0, 0, chanceArray, results, hp, depthLimit, targetDepth, result);
		System.out.println("Branching to " + result.size() + " for MT");
		return result;
	}

	private static void split(double nodeChance, int prevDmg, int depth, HitChance[] chanceArray, double[] results, int hp, int depthLimit, int targetDepth, List<Branch> branches) {
		int newDepth = depth+1;
		for(HitChance c : chanceArray) {
			int totalDmg = prevDmg + c.dmg;
			double branchChance = nodeChance * c.chance;
			if(totalDmg>=hp){
				results[newDepth] += branchChance;
			} else if(newDepth==depthLimit){
				results[0] += branchChance;
			} else if(newDepth<targetDepth) {
				split(branchChance, totalDmg, newDepth, chanceArray, results, hp, depthLimit, targetDepth, branches);
			} else if(newDepth==targetDepth) {
				Branch b = new Branch(branchChance, totalDmg, newDepth, chanceArray, hp, depthLimit);
				branches.add(b);
			}
		}
	}

	public static class Calc1Result {
		int depth;
		HitChance[] hitChances;
		Target target;
	}
	
	public static class Calc2Result {
		double[] chances;
		double accuracy;
	}
	
	private static void rollInternal(int depth, int limit, int prevPercent, int low, int high, int dmg, int armor, Map<Integer, Integer> dmgToOccurrence) {
		for(int i = low; i<=high; i++) {
			if(depth==limit) {
				// convert dmg with %
				int rolledDmg = (dmg * (i+prevPercent))/100;
				// subtract armor
				int healthDmg = Math.max(0, rolledDmg-armor);
				// map to health dmg
				// every % getting same dmg value adds up to % value
				Integer c = dmgToOccurrence.get(healthDmg);
				if(c==null) {
					c = 0;
				}
				c = c+1;
				dmgToOccurrence.put(healthDmg, c);
			} else {
				rollInternal(depth+1, limit, prevPercent+i, low, high, dmg, armor, dmgToOccurrence);
			}
		}
	}
	
	public static Calc1Result calc1(Target t, int rolls, int lowLimit, int highLimit, int dmg, double hitChance) {
		
		Map<Integer, Integer> dmgToOccurrence = new HashMap<>();
		
		rollInternal(1, rolls, 0, lowLimit, highLimit, dmg, t.armor, dmgToOccurrence);
		
		int sum = 0;
		System.out.println("chances at 100%:");
		for(Map.Entry<Integer, Integer> i : dmgToOccurrence.entrySet()) {
			System.out.println("dmg: " + i.getKey() + " chance " + i.getValue());
			sum += i.getValue();
		}
		System.out.println("-----------");
		
		double toZeroDmg = (1.0 / hitChance) * (double)sum - (double)sum;
		System.out.println("toZero (hit chance) " + toZeroDmg + " ( sum " + sum + ")");
		Integer k = dmgToOccurrence.get(0);
		if(k==null) {
			k = 0;
		}
		k = k+(int)toZeroDmg;
		dmgToOccurrence.put(0, k);
		sum += toZeroDmg;
		
		// convert to fractional chance
		double c = 0;
		List<HitChance> chances = new ArrayList<>();
		for(Map.Entry<Integer, Integer> i : dmgToOccurrence.entrySet()) {
			double chance = ((double)i.getValue())  / sum;
			chances.add(new HitChance(chance, i.getKey()));
			c += chance;
		}
		
		Collections.sort(chances, (o1, o2)->Integer.compare(o2.dmg, o1.dmg));
		
		// should be lim(1) (sanity check)
		System.out.println("sum: " + c);
		
		HitChance[] chanceArray = chances.toArray(new HitChance[0]);
		
		int depthLimit = Math.min(guessDepth(chances.size()), 999);
		System.out.println("depthLimit = " + depthLimit);
		
		Calc1Result r = new Calc1Result();
		r.depth = depthLimit;
		r.hitChances = chanceArray;
		r.target = t;
		return r;
	}
	
	public static int guessDepth(int chances) {
		return (int) (Math.ceil(Math.log(1_000_000_000_000L) / Math.log(chances)));
	}
	
	public static Calc2Result calcHits(Calc1Result r) throws Exception {
		if(Math.pow(r.hitChances[0].dmg, r.depth)<r.target.hp) {
			System.out.println("Not possible to kill target with " + r.depth + " shots.");
			Calc2Result result = new Calc2Result();
			result.accuracy = 1;
			result.chances = new double[0];
			return result;
		}

		int cores = Runtime.getRuntime().availableProcessors()-1;
		ThreadPoolExecutor tpe = new ThreadPoolExecutor(cores, cores, 1, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());
		
		double[] results = new double[r.depth+1];
		
		long t1 = System.currentTimeMillis();
		
		// build tree with % chances
//		calculate(1.0, 0, 0, chanceArray, results, t.hp, depthLimit);
		List<Branch> branches = split(cores, results, r.hitChances, r.target.hp, r.depth);
		
		List<Future<double[]>> futures = tpe.invokeAll(branches);
		
		for(Future<double[]> f : futures) {
			double[] d = f.get();
			for(int i = 0; i<d.length; i++) {
				results[i] += d[i];
			}
		}
		
		long t2 = System.currentTimeMillis();
		System.out.println("took " + (t2-t1) + "ms");
		
		tpe.shutdown();
		
		double sanityCheck = 0;
		for(int i = 1; i<results.length; i++) {
			System.out.println("Hits: " + i + " chance " + results[i]);
			sanityCheck += results[i];
		}
		System.out.println("Hits more than " + r.depth + " chance " + results[0]);
		sanityCheck += results[0];
		
		// should be lim(1)
		System.out.println(sanityCheck);
		
		Calc2Result result = new Calc2Result();
		result.chances = results;
		result.accuracy = sanityCheck;
		return result;
	}
	
	
	public static void main(String[] args) throws Exception {
		
		Target t = new Target(40, 10);
		
		
		int lowLimit = 0;
		int highLimit = 200;
		
		int dmg = 20;
		
		double hitChance = 0.5d;
		
		Calc1Result r = calc1(t, 1, lowLimit, highLimit, dmg, hitChance);
		
		Calc2Result r2 = calcHits(r);
	}

}
