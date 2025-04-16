package org.digitalmodular.maruneko.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;

import org.digitalmodular.utilities.ConfigurationFile;
import org.digitalmodular.utilities.FileUtilities;
import org.digitalmodular.utilities.graphics.swing.table.IconTableCellRenderer;
import org.digitalmodular.utilities.graphics.swing.table.MutableTableModel;
import static org.digitalmodular.utilities.ValidatorUtilities.requireNonNull;

import org.digitalmodular.maruneko.MaruNekoController;
import org.digitalmodular.maruneko.database.FileEntry;
import org.digitalmodular.maruneko.database.FileType;

/**
 * @author Mark Jeronimus
 */
// Created 2023-10-14
public class MaruNekoSearchPanel extends JPanel implements DatabaseResultsListener {
	private static final ImageIcon DIRECTORY_ICON    = new ImageIcon("material-folder-24.png");
	private static final ImageIcon REGULAR_FILE_ICON = new ImageIcon("material-file-24.png");
	private static final ImageIcon SYMLINK_ICON      = new ImageIcon("material-symlink-24.png");
	private static final ImageIcon OTHER_ICON        = new ImageIcon("material-question-24.png");

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_DATE_TIME;

	private final MaruNekoController controller;

	private final MutableTableModel tableModel = new MutableTableModel("",
	                                                                   "Path",
	                                                                   "Size",
	                                                                   "Date");

	@SuppressWarnings("FieldCanBeLocal")
	private final JButton                           loadButton  = new JButton("Load...");
	private final JTextField                        searchField = new JTextField(40);
	private final JTable                            table       = new JTable(tableModel);
	private final JScrollPane                       scroll      = new JScrollPane(table) {
		@Override
		public Dimension getPreferredSize() {
			return new Dimension(table.getPreferredSize().width, 500);
		}
	};
	private final TableRowSorter<MutableTableModel> sorter      = new TableRowSorter<>(tableModel);

	private final ConfigurationFile config = new ConfigurationFile("MaruNeko.config");

	@SuppressWarnings("OverridableMethodCallDuringObjectConstruction")
	public MaruNekoSearchPanel(MaruNekoController controller) {
		super(new BorderLayout());
		this.controller = requireNonNull(controller, "controller");

		config.setDefault("lastDir", ".");
		config.setDefault("LastSearch", "");
		config.setDefault("RecentMaruFileCount", "0");
		try {
			config.load();
		} catch (IOException ex) {
			ex.printStackTrace();
		}

		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.add(loadButton, BorderLayout.LINE_START);
		topPanel.add(searchField, BorderLayout.CENTER);
		add(topPanel, BorderLayout.PAGE_START);
		add(scroll, BorderLayout.CENTER);
		scroll.setViewportView(table);

		searchField.setText(config.get("LastSearch"));

		loadButton.addActionListener(ignored -> askFiles());
		searchField.addActionListener(ignored -> search());

		searchField.addAncestorListener(new AncestorListener() {
			@Override
			public void ancestorAdded(AncestorEvent event) {
				searchField.requestFocusInWindow();
			}

			@Override
			public void ancestorRemoved(AncestorEvent event) {
			}

			@Override
			public void ancestorMoved(AncestorEvent event) {
			}
		});

		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		table.setRowHeight(24);
		table.getColumnModel().getColumn(0).setCellRenderer(new IconTableCellRenderer());
		table.getColumnModel().getColumn(0).setMinWidth(24);
		table.getColumnModel().getColumn(0).setMaxWidth(24);
		table.getColumnModel().getColumn(0).setCellRenderer(new FileEntryTableCellRenderer());
		table.getColumnModel().getColumn(1).setPreferredWidth(966);
		table.getColumnModel().getColumn(2).setPreferredWidth(112);
		table.getColumnModel().getColumn(3).setPreferredWidth(166);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		table.setRowSorter(sorter);

		table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
			@Override
			public void columnAdded(TableColumnModelEvent e) {
			}

			@Override
			public void columnRemoved(TableColumnModelEvent e) {
			}

			@Override
			public void columnMoved(TableColumnModelEvent e) {
			}

			@Override
			public void columnMarginChanged(ChangeEvent e) {
				resizeNameColumn();
			}

			@Override
			public void columnSelectionChanged(ListSelectionEvent e) {
			}
		});
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int row = sorter.convertRowIndexToModel(table.rowAtPoint(e.getPoint()));

				if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
					openLocation(tableModel.getValueAt(row, 1).toString(), false);
				} else if (e.getButton() == MouseEvent.BUTTON2 && e.getClickCount() == 1) {
					openLocation(tableModel.getValueAt(row, 1).toString(), true);
				} else if (e.getButton() == MouseEvent.BUTTON3 && e.getClickCount() == 1) {
					findSubDir((FileEntry)tableModel.getValueAt(row, 0));
				}
			}
		});

		scroll.getViewport().addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				resizeNameColumn();
			}
		});

		ForkJoinPool.commonPool().submit(() -> controller.setListener(this));

		Timer timer = new Timer(200, ignored -> loadRecentFiles());
		timer.setRepeats(false);
		timer.start();
	}

	private void resizeNameColumn() {
		int width = scroll.getViewport().getWidth();

		for (int i = 0; i < table.getColumnCount(); i++) {
			int columnWidth = table.getColumnModel().getColumn(i).getWidth();
//			System.out.print(columnWidth + " ");

			if (i != 1) {
				width -= columnWidth;
			}
		}
//		System.out.println();

		TableColumn       column   = table.getColumnModel().getColumn(1);
		Object            header   = column.getHeaderValue();
		TableCellRenderer renderer = table.getTableHeader().getDefaultRenderer();

		Component component = renderer.getTableCellRendererComponent(table, header, false, false, -1, 1);
		width = Math.max(width, component.getPreferredSize().width);

//		System.out.println("Available width: " + width + " setting width to: " + width);

		table.getColumnModel().getColumn(1).setWidth(width);
		table.getColumnModel().getColumn(1).setMinWidth(width);
		table.getColumnModel().getColumn(1).setMaxWidth(width);
		table.getColumnModel().getColumn(1).setPreferredWidth(width);
	}

	private void askFiles() {
		File lastDir = new File(config.get("lastDir"));

		List<File> files = FileUtilities.askFilesForLoading(this, lastDir, "Select Maru files", "sqlite", "maru");
		if (files.isEmpty()) {
			return;
		}

		load(files);

		for (int i = 0; i < files.size(); i++) {
			//noinspection StringConcatenationMissingWhitespace
			config.set("RecentMaruFile" + i, files.get(i).toString());
		}

		config.set("RecentMaruFileCount", String.valueOf(files.size()));
		try {
			config.save();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	private void loadRecentFiles() {
		int n = config.getInt("RecentMaruFileCount");

		List<File> recents = new ArrayList<>(n);

		for (int i = 0; i < n; i++) {
			//noinspection StringConcatenationMissingWhitespace
			config.setDefault("RecentMaruFile" + i, "");
			String recent = config.get("RecentMaruFile" + i);

			if (!recent.isEmpty()) {
				File file = new File(recent);
				if (file.exists()) {
					recents.add(file);
				}
			}
		}

		load(recents);
	}

	private void load(List<File> files) {
		Set<File> filesToOpen = new TreeSet<>(files);

		tableModel.clear();

		controller.closeDatabases();

		for (File file : filesToOpen) {
			Path path = file.toPath();

			controller.openDatabase(path);
		}

		searchField.requestFocusInWindow();
		searchField.selectAll();
	}

	private void search() {
		String regex = searchField.getText().toLowerCase();

		if (regex.isEmpty()) {
			return;
		}

		searchField.setText(regex);

		config.set("LastSearch", regex);
		try {
			config.save();
		} catch (IOException ex) {
			ex.printStackTrace();
		}

		clearSearchResults();

		ForkJoinPool.commonPool().submit(() -> controller.performSearch(regex));
		searchField.selectAll();
	}

	private void openLocation(String name, boolean forceFolder) {
		if (name.startsWith("//")) {
			name = "/media/zom-b" + name.substring(1);
		}

		Path path = Paths.get(name);
		if (!Files.exists(path)) {
			Path root = getRoot(path);
			if (Files.exists(root)) {
				JOptionPane.showMessageDialog(this,
				                              "Directory does not exist anymore",
				                              "Unable to open location",
				                              JOptionPane.ERROR_MESSAGE);
			} else {
				JOptionPane.showMessageDialog(this,
				                              "Media not inserted",
				                              "Unable to open location",
				                              JOptionPane.ERROR_MESSAGE);
			}
			return;
		}

		if (Files.isRegularFile(path) && forceFolder) {
			path = path.getParent();
		}

		try {
			FileUtilities.openURL(path.toString());
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	private Path getRoot(Path path) {
		requireNonNull(path, "path");

		while (true) {
			Path parent = path.getParent();
			if (parent == null) {
				return path;
			}

			path = parent;
		}
	}

	private void findSubDir(FileEntry entry) {
		while (entry != null && entry.fileTypeID() != FileType.DIRECTORY.id()) {
			entry = MaruNekoController.getParent(entry);
		}

		if (entry == null) {
			return;
		}

		clearSearchResults();

		FileEntry finalEntry = entry;
		ForkJoinPool.commonPool().submit(() -> controller.performPathSearch(finalEntry));
	}

	@Override
	public void databaseOpened(Path file, FileEntry root) {
	}

	@Override
	public void databaseOpenError(Path file, String error) {
	}

	@Override
	public void databaseClosed(Path file) {
	}

	public void clearSearchResults() {
		tableModel.clear();
	}

	@Override
	public void offerSearchResult(List<FileEntry> fileTree) {
		StringBuilder path = new StringBuilder(512);

		for (int i = fileTree.size() - 1; i >= 0; i--) {
			FileEntry entry = fileTree.get(i);
			String    name  = entry.name();

			if (!path.isEmpty() && !name.startsWith("/") && path.charAt(path.length() - 1) != '/') {
				path.append('/');
			}

			path.append(name);
		}

		FileEntry entry = fileTree.get(0);

		String name = path.toString();
		if (name.startsWith("/media/zom-b/")) {
			name = '/' + name.substring(12);
		}

		tableModel.add(entry,
		               name,
		               entry.fileTypeID() == FileType.DIRECTORY.id() ? -1 : entry.size(),
		               humanTime(entry.creationTimestamp()));
	}

	private static class FileEntryTableCellRenderer implements TableCellRenderer {
		private static final JLabel icon = new JLabel();

		static {
			icon.setOpaque(true);
			icon.setHorizontalAlignment(SwingConstants.CENTER);
		}

		@Override
		public Component getTableCellRendererComponent(JTable table,
		                                               Object obj,
		                                               boolean isSelected,
		                                               boolean hasFocus,
		                                               int row,
		                                               int column) {
			icon.setBackground(UIManager.getColor(isSelected ? "Table.selectionBackground" : "Table.background"));
			icon.setBorder(hasFocus ? UIManager.getBorder("Table.focusCellHighlightBorder") : null);

			icon.setIcon(getIcon((FileEntry)obj));
			return icon;
		}

	}

	private static ImageIcon getIcon(FileEntry entry) {
		int type = entry.fileTypeID();

		if (type == FileType.DIRECTORY.id()) {
			return DIRECTORY_ICON;
		} else if (type == FileType.REGULAR_FILE.id()) {
			return REGULAR_FILE_ICON;
		} else if (type == FileType.SYMLINK.id()) {
			return SYMLINK_ICON;
		} else {
			return OTHER_ICON;
		}
	}

	private static String humanTime(long epochTime) {
		long    second  = epochTime / 1000;
		long    nano    = epochTime % 1000 * 1_000_000;
		Instant instant = Instant.ofEpochSecond(second, nano);

		return TIME_FORMAT.format(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
	}
}
