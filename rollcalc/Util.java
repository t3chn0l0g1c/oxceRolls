package rollcalc;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JTextField;

public class Util {

	public static JTextField[] addComponent(String name, String value, Container container,
			Map<JTextField, JTextField> values, int y) {
		JTextField label = new JTextField(12);
		label.setText(name);
		JTextField tf = new JTextField(12);
		tf.setText(value);
		addComponent(container, label, 0, y);
		addComponent(container, tf, 1, y);
		values.put(label, tf);
		return new JTextField[] { label, tf };
	}

	public static JTextField addLabeledTextField(String str, int gridy, Container container) {
		JLabel label = new JLabel(str);
		JTextField tf = new JTextField(8);
		addComponent(container, label, 0, gridy);
		addComponent(container, tf, 1, gridy);
		return tf;
	}
	
	public static JLabel addLabeledLabel(String str, int gridy, Container container) {
		JLabel label = new JLabel(str);
		JLabel tf = new JLabel("");
		addComponent(container, label, 0, gridy);
		addComponent(container, tf, 1, gridy);
		return tf;
	}

	private static final Insets insets = new Insets(0, 0, 0, 0);

	public static <T extends Component> T addComponent(Container container, T component, int gridx, int gridy) {
		return addComponent(container, component, gridx, gridy, 1, 1, GridBagConstraints.CENTER,
				GridBagConstraints.BOTH);
	}

	public static <T extends Component> T addComponent(Container container, T component, int gridx, int gridy,
			int gridwidth, int gridheight) {
		return addComponent(container, component, gridx, gridy, gridwidth, gridheight, GridBagConstraints.CENTER,
				GridBagConstraints.BOTH);
	}

	public static <T extends Component> T addComponent(Container container, T component, int gridx, int gridy,
			int gridwidth, int gridheight, int anchor, int fill) {
		GridBagConstraints gbc = new GridBagConstraints(gridx, gridy, gridwidth, gridheight, 1.0, 1.0, anchor, fill,
				insets, 0, 0);
		container.add(component, gbc);
		return component;
	}
	
	public static boolean nullOrEmpty(String s) {
		return s == null || s.trim().isEmpty();
	}
	
	public static int getInt(JTextField f) {
		String s = f.getText();
		try {
			int i = Integer.valueOf(s);
			f.setBackground(Color.WHITE);
			return i;
		} catch (Exception e) {
			f.setBackground(Color.RED);
			throw new RuntimeException();
		}
	}
	
	public static int getRangedInt(JTextField f, int min, int max) {
		String s = f.getText();
		try {
			int i = Integer.valueOf(s);
			if(i<min || i>max) {
				f.setBackground(Color.RED);
				throw new RuntimeException();
			}
			f.setBackground(Color.WHITE);
			return i;
		} catch (Exception e) {
			f.setBackground(Color.RED);
			throw new RuntimeException();
		}
	}
	
	public static int getIntPercent(JTextField f) {
		String s = f.getText();
		if(!s.endsWith("%")) {
			f.setBackground(Color.RED);
			throw new RuntimeException();
		}
		try {
			int i = Integer.valueOf(s.substring(0, s.length()-1));
			if(i>100 || i<0) {
				f.setBackground(Color.RED);
				throw new RuntimeException();
			}
			f.setBackground(Color.WHITE);
			return i;
		} catch (Exception e) {
			f.setBackground(Color.RED);
			throw new RuntimeException();
		}
	}
	
	public static double getDouble(JTextField f) {
		String s = f.getText();
		try {
			double i = Double.valueOf(s);
			f.setBackground(Color.WHITE);
			return i;
		} catch (Exception e) {
			f.setBackground(Color.RED);
			throw new RuntimeException();
		}
	}
	
}
