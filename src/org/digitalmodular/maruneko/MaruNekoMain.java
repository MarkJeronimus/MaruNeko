package org.digitalmodular.maruneko;

import java.awt.Frame;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.digitalmodular.utilities.DebugUtilities;
import org.digitalmodular.utilities.graphics.GraphicsUtilities;

import org.digitalmodular.maruneko.gui.MaruNekoSearchPanel;

/**
 * @author Mark Jeronimus
 */
// Created 2022-11-22 2023-10-14
public final class MaruNekoMain {
	public static void main(String... args) {
		SwingUtilities.invokeLater(() -> {
			GraphicsUtilities.setNiceLookAndFeel();

			JFrame f = new JFrame("Maruneko 丸猫 v0.1.0");
			f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

			f.setContentPane(new MaruNekoSearchPanel(new MaruNekoController()));

			f.pack();
			f.setLocationRelativeTo(null);

			if (!DebugUtilities.isDebugging()) {
				f.setExtendedState(Frame.MAXIMIZED_BOTH);
			}

			f.setVisible(true);
		});
	}
}
