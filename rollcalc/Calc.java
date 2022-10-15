package rollcalc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
		
		@Override
		public String toString() {
			return "[dmg:" + dmg + ", chance:" + chance + "]";
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
	
	public static Calc2Result calcHitsFaster(Calc1Result r) {
		long t1 = System.currentTimeMillis();
		HitChance[] shots = adjust(r.target.hp + r.target.armor, r.hitChances);
		HitChance[] prev = adjust(r.target.hp + r.target.armor, r.hitChances);
		HitChance[] next = new HitChance[r.target.hp];
		double killChance = 0;
		double[] results = new double[r.depth+1];
		
		for(int i = 1; i<r.depth; i++) {
			for(HitChance h : shots) {
				if(h==null) {
					continue;
				}
				for(HitChance p : prev) {
					if(p==null) {
						p = new HitChance(1d, 0);
					}
					int dmg = h.dmg + p.dmg;
					double chance = h.chance * p.chance;
					if(dmg>=r.target.hp) {
						killChance += chance;
					} else {
						HitChance n = next[dmg];
						if(n==null) {
							n = new HitChance(chance, dmg);
							next[dmg] = n;
						} else {
							n.chance += chance;
						}
					}
				}
			}
			results[i+1] = killChance;
			killChance = 0;
			prev = next;
			next = new HitChance[r.target.hp];
		}
		
		double noKill = 0;
		for(int i = 1; i<results.length; i++) {
			System.out.println("Hits: " + i + " chance " + results[i]);
			noKill += results[i];
		}
		noKill = 1d-noKill;
		results[0] = noKill;
		System.out.println("Hits more than " + r.depth + " chance " + results[0]);
//		sanityCheck += results[0];
		
		// should be lim(1)
//		System.out.println(sanityCheck);
		
		Calc2Result result = new Calc2Result();
		result.chances = results;
		result.accuracy = 1d;
		
		long t2 = System.currentTimeMillis();
		System.out.println("took " + (t2-t1) + "ms");
		
		return result;
	}
	

	private static HitChance[] adjust(int max, HitChance[] hitChances) {
		Map<Integer, Double> map = new HashMap<>();
		for(HitChance c : hitChances) {
			int dmg = Math.min(c.dmg, max);
			Double d = map.get(dmg);
			if(d==null) {
				d = 0d;
			}
			d += c.chance;
			map.put(dmg, d);
		}
		List<Map.Entry<Integer, Double>> entries = new ArrayList<>(map.entrySet());
		Collections.sort(entries, (o1, o2) -> o2.getKey()-o1.getKey());
		HitChance[] result = new HitChance[entries.size()];
		for(int i = 0; i<result.length; i++) {
			Map.Entry<Integer, Double> e = entries.get(i);
			result[i] = new HitChance(e.getValue(), e.getKey());
		}
		return result;
	}
	
	public static void main(String[] args) throws Exception {
		
		Target t = new Target(40, 10);
		
		
		int lowLimit = 0;
		int highLimit = 200;
		
		int dmg = 20;
		
		double hitChance = 0.5d;
		
		Calc1Result r = calc1(t, 1, lowLimit, highLimit, dmg, hitChance);
		
		Calc2Result r2 = calcHitsFaster(r);
	}

}
