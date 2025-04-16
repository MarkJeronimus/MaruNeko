package org.digitalmodular.maruneko.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Arc2D;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import static java.awt.BasicStroke.CAP_BUTT;
import static java.awt.BasicStroke.JOIN_MITER;
import javax.swing.JPanel;

import org.jetbrains.annotations.Nullable;

import org.digitalmodular.utilities.NumberUtilities;
import org.digitalmodular.utilities.constant.NumberConstants;
import org.digitalmodular.utilities.graphics.GraphicsUtilities;
import org.digitalmodular.utilities.graphics.StaticStrokes;
import org.digitalmodular.utilities.graphics.color.Color3f;
import org.digitalmodular.utilities.graphics.color.ColorUtilities;
import org.digitalmodular.utilities.graphics.swing.TextShape;
import static org.digitalmodular.utilities.ValidatorUtilities.requireAbove;
import static org.digitalmodular.utilities.ValidatorUtilities.requireAtLeast;
import static org.digitalmodular.utilities.ValidatorUtilities.requireBelow;
import static org.digitalmodular.utilities.ValidatorUtilities.requireNonNull;
import static org.digitalmodular.utilities.ValidatorUtilities.requireNotDegenerate;

import org.digitalmodular.maruneko.dataView.FileDataFacade;
import org.digitalmodular.maruneko.dataView.FileNode;
import org.digitalmodular.maruneko.dataView.FileNode.SizeFunction;
import org.digitalmodular.maruneko.dataView.FileNode.SortFunction;

/**
 * @author Mark Jeronimus
 */
// Created 2022-11-17
@SuppressWarnings({"AccessingNonPublicFieldOfAnotherObject", "UseOfSystemOutOrSystemErr", "ProhibitedExceptionThrown"})
public class PieChartPanel extends JPanel implements MouseListener, MouseMotionListener, ComponentListener {
	public static final Color CENTER_COLOR = new Color(99, 99, 99);

	/**
	 * @author Mark Jeronimus
	 */
	// Created 2022-11-17
	public enum DisplayParameter {
		FILES_COUNT(SizeFunction.TREE_SIZE, SortFunction.OCCUPIED_SIZE),
		OCCUPIED_SIZE(SizeFunction.OCCUPIED_SIZE, SortFunction.OCCUPIED_SIZE);

		private final SizeFunction sizeFunction;
		private final SortFunction sortFunction;

		DisplayParameter(SizeFunction sizeFunction, SortFunction sortFunction) {
			this.sizeFunction = sizeFunction;
			this.sortFunction = sortFunction;
		}

		public SizeFunction getSizeFunction() {
			return sizeFunction;
		}

		public SortFunction getSortFunction() {
			return sortFunction;
		}
	}

	private static class Slice {
		private final FileNode fileNode;
		private final int      x;
		private final int      y;
		private final int      radius1;
		private final int      radius2;
		private final double   fromAngle; // Degrees
		private final double   angleSpan; // Degrees
		private final Color3f  hsl;

		Slice(FileNode fileNode,
		      int x,
		      int y,
		      int radius1,
		      int radius2,
		      double fromAngle,
		      double angleSpan,
		      Color3f hsl) {
			this.fileNode  = fileNode;
			this.x         = x;
			this.y         = y;
			this.radius1   = radius1;
			this.radius2   = radius2;
			this.fromAngle = requireNotDegenerate(fromAngle, "fromAngle");
			this.angleSpan = requireNotDegenerate(angleSpan, "angleSpan");
			this.hsl       = hsl;
		}
	}

	private final FileDataFacade fileData;

	private double           centerSize       = 1.6;
	private int              numShells        = 4;
	private double           angleThreshold   = 0.2;
	private DisplayParameter displayParameter = DisplayParameter.OCCUPIED_SIZE;

	private FileNode root = null;

	private @Nullable Slice selectedSlice = null;

	private final Collection<Slice> slices = new ArrayList<>(1024);

	@SuppressWarnings("OverridableMethodCallDuringObjectConstruction")
	public PieChartPanel(FileDataFacade fileData) {
		super(null);
		this.fileData = requireNonNull(fileData, "fileData");

		setBackground(Color.BLACK);

		setRoot(fileData.getRoot());

		addMouseListener(this);
		addMouseMotionListener(this);
		addComponentListener(this);
	}

	public void setRoot(FileNode root) {
		this.root = root;

		refreshTree();
	}

	public @Nullable FileNode getRoot() {
		return root;
	}

	public double getCenterSize() {
		return centerSize;
	}

	public void setCenterSize(double centerSize) {
		this.centerSize = NumberUtilities.clamp(centerSize, 0.25, 4);
		System.out.println("centerSize=" + centerSize);

		refreshTree();
	}

	public int getNumShells() {
		return numShells;
	}

	public void setNumShells(int numShells) {
		this.numShells = requireAtLeast(1, numShells, "depth");
		System.out.println("numShells=" + numShells);

		refreshTree();
	}

	public double getAngleThreshold() {
		return angleThreshold;
	}

	public void setAngleThreshold(double angleThreshold) {
		requireAbove(0.0, angleThreshold, "threshold");
		this.angleThreshold = requireBelow(90.0, angleThreshold, "threshold");
		System.out.println("angleThreshold=" + angleThreshold);

		refreshTree();
	}

	public DisplayParameter getDisplayParameter() {
		return displayParameter;
	}

	public void setDisplayParameter(DisplayParameter displayParameter) {
		this.displayParameter = requireNonNull(displayParameter, "displayParameter");

		refreshTree();
		updateTitle();

	}

	private void refreshTree() {
		if (root != null) {
			sortTree(root, 1);
			makeSlices();
			repaint();
		}
	}

	private void sortTree(FileNode fileNode, int depth) {
		if (fileNode.getNumChildren() == 0) {
			return;
		}

		displayParameter.getSortFunction().sortChildren(fileNode);

		if (depth < numShells) {
			//noinspection ParameterNameDiffersFromOverriddenParameter
			for (FileNode child : fileNode.getChildren()) {
				sortTree(child, depth + 1);
			}
		}
	}

	private void makeSlices() {
		Point center = new Point(getWidth() / 2, getHeight() / 2);
		int   size   = Math.min(getWidth(), getHeight());

		int centerRadius = (int)Math.floor(size * centerSize * 0.5 / (numShells * 2 + 1));

		slices.clear();
		if (root != null) {
			Color3f hsl = new Color3f(0.0f, 2.0f, 0.5f);

			makeSlice(root, center, centerRadius, 0, 360, 0, hsl);
		}

		selectedSlice = null;
	}

	@SuppressWarnings("MethodWithTooManyParameters")
	private void makeSlice(FileNode fileNode,
	                       Point center,
	                       int centerRadius,
	                       double fromAngle,
	                       double angleSpan,
	                       int depth,
	                       Color3f hsl) {
		boolean doChildren =
				addSlice(fileNode, center, centerRadius, fromAngle, depth == 0 ? 361 : angleSpan, depth, hsl);

		if (doChildren && depth != numShells && fileNode.getNumChildren() > 0) {
			List<FileNode> children = fileNode.getChildren();
			long           sum      = 0;

			for (FileNode child : children) {
				long size = displayParameter.sizeFunction.apply(child);

				if (size == 0) {
					size = 1;
				}

				sum += size;
			}

			double mul = angleSpan / sum;
			sum = 0;

			int numChildren = children.size();
			if (numChildren > 0) {
				double power = 1;
				if (numChildren >= 3) {
					long lo = SizeFunction.OCCUPIED_SIZE.apply(children.get(0));
					long hi = SizeFunction.OCCUPIED_SIZE.apply(children.get((numChildren + 1) * 3 / 4 - 1));
					if (lo > 0) {
						power = Math.min(16, hi / (double)lo);
					}
				}

				int   dimension    = Math.min(getWidth(), getHeight());
				int   shellSpacing = (dimension / 2 - centerRadius) / numShells;
				float radius       = centerRadius + shellSpacing * (depth + 0.5f);
				float minArcLength = radius * (float)Math.toRadians(angleThreshold);

				for (int i = 0; i < numChildren; i++) {
					FileNode child = children.get(i);
					long     size  = displayParameter.getSizeFunction().apply(child);

					double fromAngle2 = fromAngle + sum * mul;
					double angleSpan2 = size * mul;
					sum += size;

					float pos        = (float)Math.pow(i / (double)numChildren, power);
					float hueOffset  = pos * hsl.g / 2 - 0.25f;
					float arcLength  = radius * (float)Math.toRadians(angleSpan2);
					float sizeFactor = NumberUtilities.unLerp(minArcLength, 16, arcLength);
					sizeFactor = Math.min(1.0f, sizeFactor);
					float   brightness = (float)Math.pow(sizeFactor, 0.5);
					Color3f hsl2       = new Color3f(hsl.r - hueOffset, hsl.g / 2, hsl.b * brightness);

					makeSlice(child, center, centerRadius, fromAngle2, angleSpan2, depth + 1, hsl2);
				}
			}
		}
	}

	@SuppressWarnings("MethodWithTooManyParameters")
	private boolean addSlice(FileNode fileNode,
	                         Point center,
	                         int centerRadius,
	                         double fromAngle,
	                         double angleSpan,
	                         int depth,
	                         Color3f hsl) {
		if (angleSpan < angleThreshold) {
			return false;
		}

		int dimension    = Math.min(getWidth(), getHeight());
		int shellSpacing = (dimension / 2 - centerRadius) / numShells;

		int radius2 = centerRadius + shellSpacing * depth;
		int radius1 = radius2 - shellSpacing;

		Slice slice = new Slice(fileNode, center.x, center.y, radius1, radius2, fromAngle, angleSpan, hsl);
		slices.add(slice);
		return true;
	}

	private void updateTitle() {
		@Nullable FileNode fileNode = selectedSlice != null ? selectedSlice.fileNode : root;
		Frame              frame    = (Frame)getTopLevelAncestor();

		String path = makePath(fileNode);

		if (path.isEmpty()) {
			frame.setTitle(getClass().getSimpleName() + " | " + displayParameter);
		} else {
			frame.setTitle(path + " | " + displayParameter);
		}
	}

	private void updateToolTip() {
		if (selectedSlice == null) {
			setToolTipText(null);
		} else {
			setToolTipText(sliceText(selectedSlice));
		}
	}

	private String sliceText(Slice slice) {
		String name   = slice.fileNode.getFileEntry().name();
		long   amount = displayParameter.getSizeFunction().apply(slice.fileNode);

		if (displayParameter == DisplayParameter.OCCUPIED_SIZE) {
			if (amount > 1099511627776L) {
				return name + " (" + String.format("%5.3f", amount / 1099511627776.0) + " TB)";
			} else if (amount > 1073741824L) {
				return name + " (" + String.format("%5.3f", amount / 1073741824.0) + " GB)";
			} else if (amount > 1048576L) {
				return name + " (" + String.format("%5.3f", amount / 1048576.0) + " MB)";
			} else if (amount > 1024L) {
				return name + " (" + String.format("%5.3f", amount / 1024.0) + " kB)";
			} else {
				return name + " (" + amount + ')';
			}
		} else {
			return name + " (" + amount + ')';
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		GraphicsUtilities.setAntialiased((Graphics2D)g, true);
		((Graphics2D)g).setStroke(StaticStrokes.DEFAULT_SQUARE_STROKE);

		for (Slice slice : slices) {
			drawSlice(slice, false, (Graphics2D)g);
		}

		if (selectedSlice != null) {
			drawSlice(selectedSlice, true, (Graphics2D)g);
		}
	}

	private void drawSlice(Slice slice, boolean selected, Graphics2D g) {
		double textX     = slice.x;
		double textY     = slice.y;
		double textAngle = 0;

		if (slice.angleSpan > 360.0) {
			int x        = slice.x - slice.radius2;
			int y        = slice.y - slice.radius2;
			int diameter = slice.radius2 * 2 + 1;

			g.setPaint(CENTER_COLOR);
			g.fillOval(x, y, diameter, diameter);

			g.setPaint(selected ? Color.WHITE : Color.BLACK);
			g.drawOval(x, y, diameter, diameter);
		} else {
			double radius    = (slice.radius1 + slice.radius2) * 0.5;
			int    thickness = slice.radius2 - slice.radius1;
			double x         = slice.x - radius;
			double y         = slice.y - radius;
			double diameter  = radius * 2 + 1;

			Shape arc = new Arc2D.Double(
					x, y, diameter, diameter, slice.fromAngle + 90.0, slice.angleSpan, Arc2D.OPEN);
			Stroke stroke  = new BasicStroke(thickness, CAP_BUTT, JOIN_MITER);
			Shape  segment = stroke.createStrokedShape(arc);

			g.setPaint(ColorUtilities.hsl2rgb(slice.hsl).toColor());
			g.fill(segment);

			g.setPaint(selected ? Color.WHITE : Color.BLACK);
			g.draw(segment);

			textAngle = Math.toRadians(slice.fromAngle + slice.angleSpan * 0.5);
			double arcLength = radius * Math.toRadians(slice.angleSpan);

			textX -= Math.sin(textAngle) * radius;
			textY -= Math.cos(textAngle) * radius;

			if (arcLength < 16.0) {
				return;
			}

			if (arcLength < thickness) {
				textAngle += NumberConstants.TAU025;
				if (textAngle < NumberConstants.TAU075) {
					textAngle -= NumberConstants.TAU05;
				}
			} else {
				if (textAngle > NumberConstants.TAU025 && textAngle < NumberConstants.TAU075) {
					textAngle += NumberConstants.TAU05;
				}
			}
		}

		String text = sliceText(slice);
		if (text.length() > 20) {
			text = text.substring(0, text.lastIndexOf(" ("));
		}
		TextShape textShape = new TextShape(text, textX, textY, 0.5, 0.5);
		textShape.setRotation(-textAngle);

		if (selected) {
			g.setPaint(Color.GRAY);
			textShape.draw(g);
		}

		g.setPaint(Color.BLACK);
		textShape.fill(g);
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		if (root == null) {
			return;
		}

		int x = e.getX() - getWidth() / 2;
		int y = e.getY() - getHeight() / 2;

		double r = Math.sqrt(x * x + y * y);
		double a = Math.toDegrees(Math.atan2(x, y)) + 180.0;

		selectedSlice = findSlice(r, a);

		updateTitle();
		updateToolTip();
		repaint();
	}

	@Override
	public void mousePressed(MouseEvent e) {
		mouseMoved(e);
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		if ((e.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) != 0) {
			setCenterSize(e.getX() / (double)getWidth());
		} else if ((e.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) != 0) {
			setAngleThreshold(e.getX() / (double)getWidth());
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {
	}

	@SuppressWarnings("ObjectEquality")
	@Override
	public void mouseClicked(MouseEvent e) {
		if (root == null) {
			return;
		}

		if (e.getButton() == MouseEvent.BUTTON1) {
			if (selectedSlice == null) {
				DisplayParameter[] values  = DisplayParameter.values();
				int                ordinal = (displayParameter.ordinal() + 1) % values.length;
				setDisplayParameter(values[ordinal]);
			} else if (selectedSlice.fileNode == root) {
				try {
					Desktop.getDesktop().browse(Paths.get(makePath(root)).toUri());
//					Desktop.getDesktop().browse(new URI(makePath(root)));
				} catch (IOException ex) {
					throw new RuntimeException(ex);
				}
			} else {
				setRoot(selectedSlice.fileNode);
				mouseMoved(e);
			}
		} else if (e.getButton() == MouseEvent.BUTTON3) {
			if (selectedSlice == null) {
				DisplayParameter[] values  = DisplayParameter.values();
				int                ordinal = (displayParameter.ordinal() + values.length - 1) % values.length;
				setDisplayParameter(values[ordinal]);
			} else {
				@Nullable FileNode parent = root.getParent();
				if (parent != null) {
					setRoot(parent);
					mouseMoved(e);
				}
			}
		} else if (e.getButton() == MouseEvent.BUTTON2) {
			if (e.getClickCount() == 2) {
				if (selectedSlice != null) {
					delTree();
					refreshTree();
					mouseMoved(e);
				}
			}
		}
	}

	private static String makePath(@Nullable FileNode fileNode) {
		if (fileNode == null) {
			return "";
		}

		StringBuilder path = new StringBuilder(288);

		do {
			String name = fileNode.getFileEntry().name();
			if (name.equals("/")) {
				path.insert(0, '/');
			} else {
				path.insert(0, name + '/');
			}

			fileNode = fileNode.getParent();
		} while (fileNode != null);

		return path.toString();
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		setNumShells(numShells);
	}

	@Override
	public void mouseExited(MouseEvent e) {
	}

	private @Nullable Slice findSlice(double r, double a) {
		for (Slice slice : slices) {
			if (r >= slice.radius1 &&
			    r <= slice.radius2 &&
			    a >= slice.fromAngle &&
			    a <= slice.fromAngle + slice.angleSpan) {
				return slice;
			}
		}

		return null;
	}

	private void delTree() {
		assert selectedSlice != null;

		FileNode fileNode = selectedSlice.fileNode;
		//noinspection ObjectEquality
		if (fileNode == root) {
			return;
		}

		delete(fileNode);

		@Nullable FileNode parent = fileNode.getParent();
		if (parent != null) {
			parent.removeChild(fileNode);
		}

		selectedSlice = null;
	}

	private static void delete(FileNode fileNode) {
		for (FileNode child : fileNode.getChildren()) {
			delete(child);
			System.out.println(child.getFileEntry().name());
		}

		fileNode.removeChildren();
	}

	@Override
	public void componentResized(ComponentEvent e) {
		refreshTree();
	}

	@Override
	public void componentMoved(ComponentEvent e) {
	}

	@Override
	public void componentShown(ComponentEvent e) {
	}

	@Override
	public void componentHidden(ComponentEvent e) {
	}
}
