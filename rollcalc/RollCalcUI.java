package rollcalc;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.math.BigDecimal;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

import rollcalc.Calc.Calc1Result;
import rollcalc.Calc.Calc2Result;
import rollcalc.Calc.Target;


public class RollCalcUI extends JPanel {

	private InputPanel inputPanel;
	private JTable output1;
	private JTable output2;
	
	public RollCalcUI() {
		setLayout(new GridBagLayout());
		setBorder(BorderFactory.createLineBorder(Color.BLACK));
		
		Listener1.instance1 = new Listener1(this);
		
		inputPanel = new InputPanel();
		output1 = new JTable();
		((DefaultTableModel)output1.getModel()).setDataVector(new Object[15][2], new String[] {"Chance", "Damage"});
		output1.setEnabled(false);
		output1.getColumnModel().getColumn(0).setPreferredWidth(250);
		output1.getColumnModel().getColumn(1).setPreferredWidth(50);
		
		Util.addComponent(this, new JLabel("Input"), 0, 0, 1,1, GridBagConstraints.NORTH, GridBagConstraints.NONE);
		Util.addComponent(this, inputPanel, 0, 1, 1,1, GridBagConstraints.NORTH, GridBagConstraints.NONE);
		Util.addComponent(this, new JLabel("Damage chance"), 1, 0, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.NONE);
		JScrollPane p1 = new JScrollPane(output1);
		p1.setMinimumSize(new Dimension(310, 600));
		Util.addComponent(this, p1, 1, 1, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.NONE);
		
		output2 = new JTable();
		((DefaultTableModel)output2.getModel()).setDataVector(new Object[15][2], new String[] {"Shots", "Chance"});
		output2.setEnabled(false);
		output2.getColumnModel().getColumn(0).setPreferredWidth(100);
		output2.getColumnModel().getColumn(1).setPreferredWidth(200);
		JScrollPane p2 = new JScrollPane(output2);
		p2.setMinimumSize(new Dimension(310, 600));
		Util.addComponent(this, new JLabel("Hits needed"), 2, 0, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.NONE);
		Util.addComponent(this, p2, 2, 1, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.NONE);
		
		JButton calc = new JButton("Calculate");
		Util.addComponent(this, calc, 0, 2, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.NONE);
		calc.addActionListener((e1)->calculate2(inputPanel, output2));
		
		calculate1(inputPanel, output1, false);
	}
	
	
	private static class Listener1 implements DocumentListener {
		private static Listener1 instance1;
		final RollCalcUI ui;
		public Listener1(RollCalcUI ui) {
			this.ui = ui;
		}
		public void insertUpdate(DocumentEvent e) {
			try {
				ui.calculate1(ui.inputPanel, ui.output1, false);
			} catch (Exception x) {
			}
		}
		public void removeUpdate(DocumentEvent e) {
			try {
				ui.calculate1(ui.inputPanel, ui.output1, false);
			} catch (Exception x) {
			}
		}
		public void changedUpdate(DocumentEvent e) {
			try {
				ui.calculate1(ui.inputPanel, ui.output1, false);
			} catch (Exception x) {
			}
		}
	}
	
	private Calc1Result calculate1(InputPanel ip, JTable output1, boolean depth) {
		int hp = Util.getRangedInt(ip.hp, 1, 1_000_000_000);
		int armor = Util.getRangedInt(ip.armor, 0, 1_000_000_000);
		int lowLimit = Util.getRangedInt(ip.rollMin, 0, 1_000_000_000);
		int highLimit = Util.getRangedInt(ip.rollMax, lowLimit, 1_000_000_000);
		int dmg = Util.getRangedInt(ip.dmg, 0, 1_000_000_000);
		double hitChance = (double)Util.getIntPercent(ip.hitChance)/100.0d;
		
		int rolls = Util.getRangedInt(ip.rolls, 1, 10);
		
		// TODO -field on UI for roll count
		Calc1Result r = Calc.calc1(new Target(hp, armor), rolls, lowLimit, highLimit, dmg, hitChance);
		
//		if(!depth) {
//			ip.depth.setText("" + r.depth);
//		}
//		BigInteger b1 = BigInteger.valueOf(r.hitChances.length);
		
//		ip.ops.setText("" + b1.pow(Util.getInt(ip.depth)));
		
		Object[][] t1 = new Object[r.hitChances.length][2];
		
		for(int i = 0; i<t1.length; i++) {
			t1[t1.length-i-1] = new Object[] {cut(r.hitChances[i].chance, 16), r.hitChances[i].dmg};
		}
		((DefaultTableModel)output1.getModel()).setDataVector(t1, new String[] {"Chance", "Damage"});
		
		return r;
	}
	
	private void depth() {
		if(Util.nullOrEmpty(inputPanel.depth.getText())) {
			return;
		}
		// high depth would not terminate in a lifetime
		// very high depth can even take ages just to calculate the ops
		Util.getRangedInt(inputPanel.depth, 1, 100000000);
		calculate1(inputPanel, output1, true);
	}
	private void calculate2(InputPanel ip, JTable output2) {
		Calc1Result r1 = calculate1(inputPanel, output1, true);
		r1.depth = Util.getRangedInt(inputPanel.depth, 1, 100000000);
		
		Calc2Result r2 = null;
		try {
			r2 = Calc.calcHitsFaster(r1);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		Object[][] model = createModel(r2, r1.depth);
		((DefaultTableModel)output2.getModel()).setDataVector(model, new String[] {"Shots", "Chance"});
	}


	private Object[][] createModel(Calc2Result r2, int depth) {
		if(r2.chances.length==0) {
			return new Object[][] {{depth + "+", "1.0"}};
		}
		
//		int cutOff = calcCutOff(r2.accuracy);
		Object[][] result = new Object[r2.chances.length][2];

		for(int i = 0; i<r2.chances.length; i++) {
			result[i] = new Object[] {i, cut(r2.chances[i], 16)};
//			result[i] = new Object[] {i, r2.chances[i]};
		}
		result[0][0] = (r2.chances.length-1)+"+";
		return result;
	}
	
	private static String cut(double d, int length) {
		String s = new BigDecimal(d).toString();
		return s.substring(0, Math.min(s.length(), length));
//		String s = String.format("%." + (length-2) + "f", d);
//		return s.substring(0, Math.min(s.length(), length));
	}
//
//
//	private int calcCutOff(double accuracy) {
//		String s = "" + accuracy;
//		char t = 'x';
//		if(s.startsWith("1")) {
//			t = '0';
//		} else if(s.startsWith("0")) {
//			t = '9';
//		} else {
//			throw new RuntimeException("wtf? accuracy " + accuracy);
//		}
//		for(int i = 2; i<s.length(); i++) {
//			if(s.charAt(i)!=t) {
//				return i;
//			}
//		}
//		return s.length();
//	}


	private class InputPanel extends JPanel {
		private JTextField hp;
		private JTextField armor;
		
		private JTextField rollMin;
		private JTextField rollMax;
		private JTextField rolls;
		
		private JTextField hitChance;
		private JTextField dmg;
		
		private JTextField depth;
//		private JLabel ops;
		
		public InputPanel() {
			setLayout(new GridBagLayout());
			setBorder(BorderFactory.createLineBorder(Color.BLACK));
			setMinimumSize(new Dimension(300, 150));
			
			hp = Util.addLabeledTextField("Target HP", 0, this);
			hp.setText("40");
			hp.getDocument().addDocumentListener(Listener1.instance1);
			armor = Util.addLabeledTextField("Target Armor", 1, this);
			armor.setText("10");
			armor.getDocument().addDocumentListener(Listener1.instance1);
			
			rollMin = Util.addLabeledTextField("Roll min %", 2, this);
			rollMin.setText("0");
			rollMin.getDocument().addDocumentListener(Listener1.instance1);
			rollMax = Util.addLabeledTextField("Roll max %", 3, this);
			rollMax.setText("200");
			rollMax.getDocument().addDocumentListener(Listener1.instance1);
			rolls = Util.addLabeledTextField("Roll count", 4, this);
			rolls.setText("1");
			rolls.getDocument().addDocumentListener(Listener1.instance1);
			
			hitChance = Util.addLabeledTextField("Hit Chance %", 5, this);
			hitChance.setText("50%");
			hitChance.getDocument().addDocumentListener(Listener1.instance1);
			dmg = Util.addLabeledTextField("Dmg", 6, this);
			dmg.setText("20");
			dmg.getDocument().addDocumentListener(Listener1.instance1);
			
			depth = Util.addLabeledTextField("Search Depth", 7, this);
			depth.setText("15");
			depth.getDocument().addDocumentListener(new DocumentListener() {
				public void removeUpdate(DocumentEvent e) {
					depth();
				}
				public void insertUpdate(DocumentEvent e) {
					depth();
				}
				public void changedUpdate(DocumentEvent e) {
					depth();
				}
			});
//			ops = Util.addLabeledLabel("Ops", 8, this);
//			ops.setMinimumSize(new Dimension(100, 20));
//			ops.setPreferredSize(new Dimension(100, 20));
			
		}
		
	}


	public static void main(String[] args) {

		JFrame frame = new JFrame();
		frame.setSize(900, 700);

		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		RollCalcUI gui = new RollCalcUI();

		frame.setVisible(true);
		
		frame.add(gui);

		frame.revalidate();

	}
}
