package org.digitalmodular.maruneko;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.SQLException;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.digitalmodular.utilities.graphics.GraphicsUtilities;

import org.digitalmodular.maruneko.dataView.FileDataFacade;
import org.digitalmodular.maruneko.gui.PieChartPanel;

/**
 * @author Mark Jeronimus
 */
// Created 2012-11-07
public class SpaceAnalyzerMain extends JPanel {
	private final PieChartPanel visualizer;

	public static void main(String... args) {
		SwingUtilities.invokeLater(() -> {
			GraphicsUtilities.setNiceLookAndFeel();

			JFrame f = new JFrame();
			f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

			f.setContentPane(new SpaceAnalyzerMain());

			f.pack();
			f.setLocationRelativeTo(null);
			f.setVisible(true);
		});
	}

	@SuppressWarnings("OverridableMethodCallDuringObjectConstruction")
	public SpaceAnalyzerMain() {
		super(new BorderLayout());

		try {
//			FileDataFacade fileData = new FileDataFacade(Paths.get("root.maru"));
//			FileDataFacade fileData = new FileDataFacade(Paths.get("home.maru"));
//			FileDataFacade fileData = new FileDataFacade(Paths.get("nasu.maru"));
//			FileDataFacade fileData = new FileDataFacade(Paths.get("naz.maru"));
//			FileDataFacade fileData = new FileDataFacade(Paths.get("nas.maru"));

//			FileDataFacade fileData = new FileDataFacade(Paths.get("Cecile-muziek.maru"));
//			FileDataFacade fileData = new FileDataFacade(Paths.get("Cecile-Systeem.maru"));
//			FileDataFacade fileData = new FileDataFacade(Paths.get("Cecile-werk.maru"));
//			FileDataFacade fileData = new FileDataFacade(Paths.get("Cecile-hutspot.maru"));
//			FileDataFacade fileData = new FileDataFacade(Paths.get("Cecile-rommel.maru"));
//			FileDataFacade fileData = new FileDataFacade(Paths.get("Cecile-film.maru"));
//			FileDataFacade fileData = new FileDataFacade(Paths.get("Cecile-Elements.maru"));
//			FileDataFacade fileData = new FileDataFacade(Paths.get("Cecile-USB-HDD.maru"));

			FileDataFacade fileData = new FileDataFacade(Paths.get("Dump-Packard Bell.maru"));
//			FileDataFacade fileData = new FileDataFacade(Paths.get("Dump-DATA.maru"));

			visualizer = new PieChartPanel(fileData);
			Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
			int       size   = Math.min(bounds.width, bounds.height) * 3 / 4;
			visualizer.setPreferredSize(new Dimension(size, size));
			add(visualizer, BorderLayout.CENTER);
		} catch (IOException | SQLException ex) {
			throw new RuntimeException(ex);
		}
	}
}
