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
		int salvo;
	}
	
	public static class Calc2Result {
		double[] chances;
		double accuracy;
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
	
	public static Calc1Result calc1(Target t, int rolls, int lowLimit, int highLimit, int dmg, double hitChance, int salvo) {
		
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
		
		calcRolls(lowDmg, highDmg, t.armor, 0, chancePerRoll, 0, rolls, 1, dmgToOccurrence);


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
		r.salvo = salvo;
		return r;
	}
	
	public static int guessDepth(int chances) {
		return (int) (Math.ceil(Math.log(1_000_000_000_000L) / Math.log(chances)));
	}
	
	public static Calc2Result calcHitsFaster(Calc1Result r) {
		Target target = r.target;
		HitChance[] shots = adjust(target.hp + target.armor, r.hitChances);
		double[] accumulatedDamage = new double[target.hp+1];
		accumulatedDamage[0] = 1;

		double[] results = new double[r.depth+1];
		
		for(int depth = 1; depth<=r.depth; depth++) {
			
			for(int i = 0; i<r.salvo; i++) {
			double[] next = new double[target.hp+1];
			

				for(HitChance shot : shots) {
					for(int idx = 0; idx<accumulatedDamage.length; idx++) {
						
						int dmg = Math.min(idx+shot.dmg, target.hp);
		                next[dmg] += accumulatedDamage[idx] * shot.chance;
					}
				}
			
				accumulatedDamage = next;
			}
	        results[depth] = accumulatedDamage[target.hp];
		}
		
		double remaining = 1;
		for(int i = results.length-1; i>0; i--) {
			results[i] -= results[i-1];
			remaining -= results[i];
		}
		results[0] = remaining;
		for(double d : results) {
			System.out.println(d);
		}
		Calc2Result res = new Calc2Result();
		res.chances = results;
		res.accuracy = 1;
		return res;
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
		
		Target t = new Target(1, 0);
		
		
		int lowLimit = 100;
		int highLimit = 100;
		
		int dmg = 1;
		
		double hitChance = 0.5d;
		
		Calc1Result r = calc1(t, 1, lowLimit, highLimit, dmg, hitChance, 2);
		
		Calc2Result r2 = calcHitsFaster(r);
	}

}