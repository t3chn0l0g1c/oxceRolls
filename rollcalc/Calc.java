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
				double percent = (i+prevPercent)/100d;
//				int rolledDmg = (dmg * (i+prevPercent))/100;
				int rolledDmg = (int)(percent*dmg);
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
	
	private static void calcRolls(double hitChance, int low, int high, int armor, Map<Integer, Double> dmgToOccurrence) {
		
		int rolls = high-low;
		double chancePerRoll = hitChance;
		if(rolls>0) {
			chancePerRoll = hitChance/rolls;
		}
		for(int i = low; i<=high; i++) {
			int healthDmg = Math.max(0, i-armor);
			addToMap(healthDmg, chancePerRoll, dmgToOccurrence);
		}
		
	}
	
	private static void addToMap(int dmg, double chance, Map<Integer, Double> dmgToOccurrence) {
		Double c = dmgToOccurrence.get(dmg);
		if(c==null) {
			c = 0d;
		}
		c += chance;
		dmgToOccurrence.put(dmg, c);
	}
	
	private static void calcRolls(int low, int high, int armor, int dmg, double chancePerRoll, double branchChance, int rolls, int i, Map<Integer, Double> dmgToOccurrence) {
		for(int i1 = low; i1<=high; i1++) {
			int d2 = i1+dmg;
			if(i==rolls) {
				addToMap(Math.max(0, d2-armor), chancePerRoll+branchChance, dmgToOccurrence);
			} else {
				calcRolls(low, high, armor, d2, chancePerRoll, chancePerRoll+branchChance, rolls, i+1, dmgToOccurrence);
			}
		}
	}
	
	public static Calc1Result calc1(Target t, int rolls, int lowLimit, int highLimit, int dmg, double hitChance) {
		
		Map<Integer, Double> dmgToOccurrence = new HashMap<>();
		
		if(hitChance<1d) {
			double missChance = 1d-hitChance;
			addToMap(0, missChance, dmgToOccurrence);
		}
		
		int lowDmg = dmg * lowLimit / 100;
		int highDmg = dmg * highLimit / 100;
		
		int dmgRolls = highDmg-lowDmg + 1;
		
		dmgRolls = (int) Math.pow(dmgRolls, rolls);
		
		double chancePerRoll = hitChance/dmgRolls;
		
		// TODO roll count roll^rolls
		// needs recursive
//		.
		calcRolls(lowDmg, highDmg, t.armor, 0, chancePerRoll, 0, rolls, 1, dmgToOccurrence);

//		for(int i1 = lowDmg; i1<=highDmg; i1++) {
//			int healthDmg = Math.max(0, i1-t.armor);
//			addToMap(healthDmg, chancePerRoll, dmgToOccurrence);
//		}

		double chancesSum = 0;
		System.out.println("-----------");
		for(Map.Entry<Integer, Double> i : dmgToOccurrence.entrySet()) {
			System.out.println("dmg: " + i.getKey() + " chance " + i.getValue());
			chancesSum += i.getValue();
		}
		System.out.println("-----------");
		System.out.println("chances sum= " + chancesSum);
		
		// convert to fractional chance
		List<HitChance> chances = new ArrayList<>();
		for(Map.Entry<Integer, Double> i : dmgToOccurrence.entrySet()) {
			chances.add(new HitChance(i.getValue(), i.getKey()));
		}
		
		Collections.sort(chances, (o1, o2)->Integer.compare(o2.dmg, o1.dmg));
		
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
		
		for(HitChance c : prev) {
			if(c.dmg>=r.target.hp) {
				killChance += c.chance;
			}
		}
		results[1] = killChance;
		double remaining = 1-killChance;
		killChance = 0;
		
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
			killChance *= remaining;
			remaining -= killChance;
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
