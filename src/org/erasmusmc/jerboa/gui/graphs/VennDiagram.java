/***********************************************************************************
 *                                                                                 *
 * Copyright (C) 2017  Erasmus MC, Rotterdam, The Netherlands                      *
 *                                                                                 *
 * This file is part of Jerboa.                                                    *
 *                                                                                 *
 * This program is free software; you can redistribute it and/or                   *
 * modify it under the terms of the GNU General Public License                     *
 * as published by the Free Software Foundation; either version 2                  *
 * of the License, or (at your option) any later version.                          *
 *                                                                                 *
 * This program is distributed in the hope that it will be useful,                 *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                  *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                   *
 * GNU General Public License for more details.                                    *
 *                                                                                 *
 * You should have received a copy of the GNU General Public License               *
 * along with this program; if not, write to the Free Software                     *
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. *
 *                                                                                 *
 ***********************************************************************************/

package org.erasmusmc.jerboa.gui.graphs;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;


@SuppressWarnings("serial")
public class VennDiagram extends JPanel implements MouseListener {

	private AffineTransform at;
	private Shape shape;
	private Graphics2D g2;

	//set dimensions
	private static double scale = 0.8;
	private static double SET_WIDTH = 350;
	private static double SET_HEIGHT = 150;

	//panel dimensions
	public static  int PANEL_WIDTH = 600;
	public static  int PANEL_HEIGHT = 600;

	//TODO: decide if wanted
//	private static final double MAX_SET_WIDTH = SET_WIDTH*2;
//	private static final double MAX_SET_HEIGHT = SET_HEIGHT*2;
//	private static final double MIN_SET_WIDTH = SET_WIDTH/3;
//	private static final double MIN_SET_HEIGHT = SET_HEIGHT/3;

//	private static  int MIN_WIDTH = 200;
//	private static  int MIN_HEIGHT = 300;
//	private static double SHAPE_RESIZE_X = SET_WIDTH;
//	private static double SHAPE_RESIZE_Y = SET_HEIGHT;

	private static final double WIDTH_RATIO = PANEL_WIDTH/SET_WIDTH;
	private static final double HEIGHT_RATIO = PANEL_HEIGHT/SET_HEIGHT;
	private static double SCREEN_RATIO = 1;

	//labels and colors
	public static int nbSets;
	public static final String[] setLabels = { "A", "B", "C", "D", "E"};
	public static final Color[] setColors = { Color.red, Color.blue,
		Color.green, Color.yellow, Color.cyan};

	//set combinations and values
	public static TreeMap<String, Color> sets;
	public static TreeMap<String, Integer> values;

	//debugging
	private JFrame  frame;
	private BufferedImage image;

	//used in the patch in result viewer superclass
	public int nbSets_;
	public TreeMap<String, Integer> values_;

	//MAIN FOR TESTING AND DEBUGGING
	public static void main(String[] args) {
		//	new VennDiagram(2, null);
		new VennDiagram(3);
	}


	//TODO: fix the resizing not to move the center ofthe shapes around


	//CONSTRUCTORS
	/**
	 * Constructor from a Venn diagram object.
	 * @param vd - the Venn diagram
	 */
	public VennDiagram(VennDiagram vd){
		this(vd.nbSets_, vd.values_);
	}

	/**
	 * Initializes the shape of the Venn diagram components
	 * based on the number of sets that are to be displayed.
	 * If 2 or 3 sets are needed then a circular shape is used.
	 * For 4 or 5 sets, the shapes used will be ellipses.
	 * If any other number of sets is passed as argument,
	 *  an error pop-up message is displayed
	 * @param nbSets - number of Venn diagram components
	 * @param values - the values corresponding to each combination of the sets
	 */
	public VennDiagram(int nbSets, TreeMap<String, Integer> values){
		super();

		//check if legal number of sets is passed
		if (nbSets >= 2 && nbSets <= 5){
			//prepare the looks
			this.setBackground(Color.white);
			this.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Diagram"));
			this.at = new AffineTransform();
			this.setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
			this.setSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));

			VennDiagram.nbSets = nbSets;
			VennDiagram.values = values;
			initSets();

			combinations(sets);

			//DEBUG
			nbSets_ = nbSets;
			values_ = values;

			PANEL_WIDTH = this.getWidth();
			PANEL_HEIGHT = this.getHeight();

			Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
			SCREEN_RATIO = screenSize.getWidth()/screenSize.getHeight();


		}else{
			JOptionPane.showMessageDialog(this, "Incorrect number of sets. Only values" +
					" between 2 and 5 are allowed", "Venn Diagram Error", JOptionPane.ERROR_MESSAGE);
		}

	}

	//CONNSTRUCTOR FOR DEBUG
	public VennDiagram(int nbSets){

		if (nbSets >= 2 && nbSets <= 5){

			this.setBackground(Color.white);
			this.at = new AffineTransform();
			this.setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
			this.setSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));

			VennDiagram.nbSets = nbSets;
			initFrame();
			initSets();
			combinations(sets);

			//DEBUG - patch in result viewer
			nbSets_ = nbSets;
			values_ = values;

			PANEL_WIDTH = this.getWidth();
			PANEL_HEIGHT = this.getHeight();

			Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
			SCREEN_RATIO = screenSize.getWidth()/screenSize.getHeight();

		}else{
			JOptionPane.showMessageDialog(this, "Incorrect number of sets. Only values" +
					" between 2 and 5 are allowed", "Venn Diagram Error", JOptionPane.ERROR_MESSAGE);
		}
		//DEBUG color component related
		//		 for (Entry<String, Color> entry : colorMap.entrySet()) {
		//		 System.out.println("combination: "+entry.getKey()+" -- red: "+entry.getValue().getRed()
		//				 +" green: "+entry.getValue().getGreen()+" blue: "+entry.getValue().getBlue()+" alpha: "+entry.getValue().getAlpha());
		//	 }
	}


	/**
	 * Draws the shapes on this panel.
	 */
	public void paintComponent(Graphics g) {
		super.paintComponent(g);

		g2 = (Graphics2D) g;
		if (this.image != null)
			g2.drawImage(this.image, this.getX(),
					this.getY(), null);

		switch (nbSets){
		case 2 :
			setShapeSize();
			shape = new Ellipse2D.Double(0, 0, SET_WIDTH, SET_WIDTH);
			int[] Xoffset = {-80,80};
			int[] Yoffset = {0,0};
			for (int i = 0; i < nbSets; i++){
				center();
				g2.setPaint(setColors[i]);
				drawShape(Xoffset[i], Yoffset[i], 2.0f);
			}
			drawLabels2sets(Xoffset, Yoffset);
			break;
		case 3 :
			setShapeSize();
			shape = new Ellipse2D.Double(0, 0, SET_WIDTH, SET_WIDTH);
			Xoffset = new int[]{-80,80,0};
			Yoffset = new int[]{-70,-70,80};
			for (int i = 0; i < nbSets; i++){
				center();
				g2.setPaint(setColors[i]);
				drawShape(Xoffset[i], Yoffset[i], 2.0f);
			}
			drawLabels3sets(Xoffset, Yoffset);
			break;
		case 4 :
			setShapeSize(300, 100, 400, 200);
			shape = new Ellipse2D.Double(0, 0, SET_WIDTH, SET_HEIGHT);
			Xoffset = new int[]{-17,-17,-10,-10};
			Yoffset = new int[]{110,-110,50,-50};
			boolean angle = true;
			for (int i = 0; i < nbSets; i ++){
				center();
				g2.setPaint(setColors[i]);
				rotate(angle ? 45 : 135);
				drawShape(Xoffset[i], Yoffset[i], 2.0f);
				angle = !angle;
			}
			drawLabels4sets();
			break;
		case 5 :
			shape = new Ellipse2D.Double(0, 0, 450, 220);
			Xoffset = new int[]{-50,50,-50,50,-50};
			Yoffset = new int[]{-20,20,-20,20,-20};
			for (int i = 0; i < nbSets; i ++){
				center();
				g2.setPaint(setColors[i]);
				rotate(180/nbSets*i+15);
				drawShape(Xoffset[i], Yoffset[i], 2.0f);
			}
			drawLabels5sets();
			break;

		}

		//draw the center as reference
		//		shape = new Ellipse2D.Double(0,0,10,10);
		//		center();
		//		g2.setPaint(Color.black);
		//		drawShape(0,0);

	}

	/**
	 * Draws one shape on the canvas with a specific offset on X and Y axis
	 * from the center of the canvas.
	 * @param widthOffset - the offset on the X axis
	 * @param heightOffset - the offset on the Y axis
	 */
	private void drawShape(int widthOffset, int heightOffset, float outlineWidth){

		//set the stroke.
		g2.setStroke(new BasicStroke(outlineWidth));

		//get shape borders
		Rectangle r = shape.getBounds();

		// put the shape in the middle of the canvas with some offset (if needed)
		AffineTransform savedForm = g2.getTransform();
		AffineTransform toCenterAt = new AffineTransform();
		toCenterAt.concatenate(at);
		toCenterAt.translate(-(r.width/2)+(int)(widthOffset), -(r.height/2)+(int)(heightOffset));
		g2.transform(toCenterAt);

		//get the old solid composite
		Composite old = g2.getComposite();

		//add transparency
		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));

		//draw the shape and outline
		g2.fill(shape);
		g2.setComposite(old);
		g2.setColor(Color.black);
		g2.draw(shape);

		//put it back to the old position
		g2.setTransform(savedForm);

	}

	/**
	 * Translates the shape in the center of the canvas.
	 */
	private void center(){
		at.setToIdentity();
		at.translate(this.getWidth()/2, this.getHeight()/2);
	}

	/**
	 * Rotates the shape by a certain angle.
	 * @param angle - the rotation angle
	 */
	private void rotate(double angle){
		at.rotate(Math.toRadians(angle));
	}

	/**
	 * Method creating a string out of all set labels (only one character)
	 * and calling the recursive method to create combinations.
	 * @param sets - the sets and their color
	 */
	private static void combinations(TreeMap<String, Color> sets) {
		String concatenated = "";
		Iterator<String> it = sets.keySet().iterator();
		for (int i = 0 ; i < nbSets; i ++)
			concatenated += it.next();
		combinations("", concatenated);
	}

	/**
	 * Recursive method to create all possible combinations
	 * between the sets.
	 * @param prefix - the prefix of the combination
	 * @param s - the string containing the rest of the sets
	 */
	private static void combinations(String prefix, String s) {
		if (prefix != "")
			sets.put(prefix, getCombinationColor(prefix));
		for (int i = 0; i < s.length(); i++)
			combinations(prefix + s.charAt(i), s.substring(i + 1));
	}

	/**
	 * Converts a string of characters into a shape object.
	 * Used to draw the labels.
	 * @param f - the desired font
	 * @param s - the desired string of characters
	 * @return - a shape object with the outline of the text
	 */
	private Shape stringToShape(Font f, String s){
		GlyphVector v = f.createGlyphVector(getFontMetrics(f).getFontRenderContext(), s);
		return v.getOutline();
	}

	//********************** LABEL DRAWING *********************************//

	/**
	 * Puts the labels of the sets and set overlap on the Venn diagram
	 * composed of two sets.
	 * @param g - the graphics component
	 */
	private void drawLabels2sets(int[] Xoffset, int[] Yoffset){
		g2.setColor(Color.black);
		float stroke = 2.0f;

		Font f = new Font("TrueType", Font.BOLD, getFontSize(40));
		shape = stringToShape(f, getLabel("A")); drawShape(getLabelXOffset(Xoffset[0]+50), 20, stroke);
		shape = stringToShape(f, getLabel("B"));  drawShape(getLabelXOffset(Xoffset[1]-50), 20, stroke);

		f = new Font("TrueType", Font.BOLD, getFontSize(35));
		shape = stringToShape(f, getLabel("AB"));  drawShape(0, 20, stroke);

	}

	/**
	 * Puts the labels of the sets and set overlap on the Venn diagram
	 * composed of three sets.
	 * @param g - the graphics component
	 */
	private void drawLabels3sets(int[] Xoffset, int[] Yoffset){
		g2.setColor(Color.black);
		float stroke = 2.0f;

		Xoffset = new int[]{-80,80,0};
		Yoffset = new int[]{-70,-70,80};

		Font f = new Font("TrueType", Font.BOLD, getFontSize(40));
		shape = stringToShape(f, getLabel("A"));  drawShape(getLabelXOffset(Xoffset[0]+50), Yoffset[0], stroke);
		shape = stringToShape(f, getLabel("B"));  drawShape(getLabelXOffset(Xoffset[1]-50) , Yoffset[1], stroke);
		shape = stringToShape(f, getLabel("C"));  drawShape(0 , getLabelXOffset(Yoffset[2]-30), stroke);

		f= new Font("TrueType", Font.BOLD, getFontSize(32));
		shape = stringToShape(f, getLabel("AB"));  drawShape(0 , -getLabelXOffset(), stroke);
		shape = stringToShape(f, getLabel("AC"));  drawShape(40-getLabelXOffset() , getLabelXOffset()-90, stroke);
		shape = stringToShape(f, getLabel("BC"));  drawShape(getLabelXOffset()-40 , getLabelXOffset()-90, stroke);

		shape = stringToShape(f, getLabel("ABC"));  drawShape(0 , 0, stroke);

	}

	/**
	 * Puts the labels of the sets and set overlap on the Venn diagram
	 * composed of four sets.
	 */
	private void drawLabels4sets(){
		g2.setColor(Color.black);
		float stroke = 1.2f;
		//		double ratio = 5.0;

		//avoid having labels rotated like the ellipses
		center();

		Font f = new Font("TrueType", Font.BOLD, getFontSize(30));
		shape = stringToShape(f, getLabel("A")); drawShape(-100 ,-50, stroke);
		shape = stringToShape(f, getLabel("B")); drawShape(100,-50, stroke);
		shape = stringToShape(f, getLabel("C")); drawShape(150, 60, stroke);
		shape = stringToShape(f, getLabel("D")); drawShape(-150 , 60, stroke);

		f = new Font("TrueType", Font.BOLD, getFontSize(25));
		shape = stringToShape(f, getLabel("AB")); drawShape(0 , 20, stroke);
		shape = stringToShape(f, getLabel("AD")); drawShape(-100 , 30, stroke);
		shape = stringToShape(f, getLabel("BC")); drawShape(100 , 30, stroke);
		shape = stringToShape(f, getLabel("CD")); drawShape(0 , 190, stroke);

		f = new Font("TrueType", Font.BOLD, getFontSize(18));
		shape = stringToShape(f, getLabel("ABC")); drawShape(40 , 70, stroke);
		shape = stringToShape(f, getLabel("ABD")); drawShape(-40 , 70, stroke);

		f = new Font("TrueType", Font.BOLD, getFontSize(16));
		shape = stringToShape(f, getLabel("AC")); drawShape(70 , 140, stroke);
		shape = stringToShape(f, getLabel("BD")); drawShape(-70 , 140, stroke);
		shape = stringToShape(f, getLabel("ABCD")); drawShape(0 , 120, stroke);

		f = new Font("TrueType", Font.BOLD, getFontSize(12));
		shape = stringToShape(f, getLabel("ACD")); drawShape(30 , 160, stroke);
		shape = stringToShape(f, getLabel("BCD")); drawShape(-30 , 160, stroke);

	}

	/**
	 * Puts the labels of the sets and set overlap on the Venn diagram
	 * composed of five sets.
	 */
	private void drawLabels5sets(){
		g2.setColor(Color.black);
		float stroke = 1.2f;

		//avoid having labels rotated like the ellipses
		center();

		Font f = new Font("TrueType", Font.BOLD, getFontSize(35));
		shape = stringToShape(f, getLabel("A")); drawShape(10 , -210, stroke);
		shape = stringToShape(f, getLabel("B")); drawShape(215 , -40, stroke);
		shape = stringToShape(f, getLabel("C")); drawShape(120 , 220, stroke);
		shape = stringToShape(f, getLabel("D")); drawShape(-150 , 200, stroke);
		shape = stringToShape(f, getLabel("E")); drawShape(-215 , -60, stroke);

		f = new Font("TrueType", Font.BOLD, getFontSize(20));
		shape = stringToShape(f, getLabel("AC")); drawShape(45 , 155, stroke);
		shape = stringToShape(f, getLabel("AD")); drawShape(45 , -130, stroke);
		shape = stringToShape(f, getLabel("BD")); drawShape(-130 , 95, stroke);
		shape = stringToShape(f, getLabel("BE")); drawShape(145 , 20, stroke);
		shape = stringToShape(f, getLabel("CE")); drawShape(-120 , -80, stroke);

		shape = stringToShape(f, getLabel("ABCDE")); drawShape(0 , 0, stroke);

		stroke = 1.2f;
		f = new Font("TrueType", Font.BOLD, getFontSize(14));
		shape = stringToShape(f, getLabel("ABCD")); drawShape(-25 , 115, stroke);
		shape = stringToShape(f, getLabel("ABCE")); drawShape(85 , 75, stroke);
		shape = stringToShape(f, getLabel("ABDE")); drawShape(70 , -70, stroke);
		shape = stringToShape(f, getLabel("BCDE")); drawShape(-113 , 20, stroke);
		shape = stringToShape(f, getLabel("ACDE")); drawShape(-45 , -90, stroke);

		f = new Font("TrueType", Font.BOLD, getFontSize(12));
		shape = stringToShape(f, getLabel("ABD")); drawShape(90 , -100, stroke);
		shape = stringToShape(f, getLabel("ACD")); drawShape(-15 , 150, stroke);
		shape = stringToShape(f, getLabel("ACE")); drawShape(-75 , -115, stroke);
		shape = stringToShape(f, getLabel("BCE")); drawShape(130 , 65, stroke);
		shape = stringToShape(f, getLabel("BDE")); drawShape(-140 , 45, stroke);

		shape = stringToShape(f, getLabel("ABC")); drawShape(60 , 110, stroke);
		shape = stringToShape(f, getLabel("ABE")); drawShape(112 , -20, stroke);
		shape = stringToShape(f, getLabel("ADE")); drawShape(5 , -110, stroke);
		shape = stringToShape(f, getLabel("BCD")); drawShape(-85 , 90, stroke);
		shape = stringToShape(f, getLabel("CDE")); drawShape(-110 , -35, stroke);

		shape = stringToShape(f, getLabel("AB")); drawShape(115 , -80, stroke);
		shape = stringToShape(f, getLabel("AE")); drawShape(-65 , -135, stroke);
		shape = stringToShape(f, getLabel("BC")); drawShape(118 , 92, stroke);
		shape = stringToShape(f, getLabel("CD")); drawShape(-45 , 148, stroke);
		shape = stringToShape(f, getLabel("DE")); drawShape(-145 , 10, stroke);

	}

	//****************** END OF LABEL DRAWING***************************//

	//GETTERS AND SETTERS
	public String[] getSetlabels() {
		return setLabels;
	}

	public Color[] getSetcolors() {
		return setColors;
	}

	/**
	 * Sets the size of a shape based on the resizing of this panel.
	 * It makes use of the scaling factor defined in this class.
	 */
	private void setShapeSize(){
		SET_WIDTH = Math.min(this.getWidth(), this.getHeight())/WIDTH_RATIO*scale;
		SET_HEIGHT = Math.min(this.getWidth(), this.getHeight())/HEIGHT_RATIO*scale;
	}

	/**
	 * Sets the new width and height of a shape based on the resizing of this panel
	 * and the scale factor. It takes as arguments upper and lower limits for each of the two shape attributes.
	 * @param minWidth - the minimum width that this shape can have
	 * @param minHeight - the minimum height that this shape can have
	 * @param maxWidth - the maximum width that this shape can have
	 * @param maxHeight - the maximum height that this shape can have
	 */
	private void setShapeSize(int minWidth, int minHeight, int maxWidth, int maxHeight){

		double current = this.getWidth()/WIDTH_RATIO*scale;
		SET_WIDTH = current > maxWidth ? maxWidth :
			(current < minWidth ? minWidth : current);

		current = this.getHeight()/HEIGHT_RATIO*scale;
		SET_HEIGHT = current > maxHeight ? maxHeight :
			(current < minHeight ? minHeight : current);
	}

	/**
	 * Retrieves the offset on the X axis of a label based on
	 * the shape resize on X axis and a desired offset.
	 * @param offset - the desired offset
	 * @return - the offset of a label on X axis
	 */
	private int getLabelXOffset(int offset){
		return offset >= 0 ? (int)((offset+SET_WIDTH/2)) :
			(int)((offset-SET_WIDTH/2));
	}

	/**
	 * Retrieves the offset on the Y axis of a label based on
	 * the shape resize on Y axis and a desired offset.
	 * @param offset - the desired offset
	 * @return - the offset of a label on Y axis
	 */
	@SuppressWarnings("unused")
	private int getLabelYOffset(int offset){
		return offset >= 0 ? (int)((offset+SET_HEIGHT/2)*SCREEN_RATIO) :
			(int)((offset-SET_HEIGHT/2)*SCREEN_RATIO);
	}

	/**
	 * Retrieves the offset on the X axis of a label based on
	 * the shape resize on X axis and scaling factor.
	 * @return - the offset of a label on Y axis
	 */
	private int getLabelXOffset(){
		return (int)(SET_WIDTH/2*scale);
	}

	/**
	 * Retrieves the offset on the Y axis of a label based on
	 * the shape resize on Y axis and scaling factor.
	 * The screen ratio (height/width) is taken into consideration.
	 * @return - the offset of a label on Y axis
	 */
	@SuppressWarnings("unused")
	private int getLabelYOffset(){
		return (int)(SET_WIDTH/2*SCREEN_RATIO*scale);
	}

	/**
	 * Calculates the font size based on the scaling factor.
	 * @param desiredSize - what size the font should be
	 * @return - the calculated font size based on scale
	 */
	private int getFontSize(int desiredSize){
		int newSize = (int)(desiredSize*scale);
		return newSize > 10 ? newSize : 10;
	}

	/**
	 * Returns the sorted set of set combinations for this diagram.
	 * @return
	 */
	public static Set<String> getSetCombinations(){
		if (sets == null || sets.isEmpty())
			combinations(sets);
		TreeSet<String> set = new TreeSet<String>();
		set.addAll(sets.keySet());
		return set;
	}

	/**
	 * Returns a value corresponding to set (which can be a combination
	 * of multiple sets also) if the values map is not null or the set
	 * itself otherwise.
	 * @param set - the set or combination of sets
	 * @return - the corresponding value or the set string itself if values is null
	 */
	private static String getLabel(String set){
		return values != null && values.get(set) != null ?
				String.valueOf(values.get(set)) : set;
	}

	//UNDER DEVELOPMENT
	/**
	 * Puts together the color components of a set combination
	 * based on the individual colors of each set.
	 * Each component (RGB) is averaged by the number of sets in
	 * each combination.
	 * @param combination - the combination for which the color has to be computed.
	 * @return - a color specific to this combination
	 */
	private static Color getCombinationColor(String combination){
		char[] subsets = combination.toCharArray();
		int red = 0, green = 0, blue = 0, alpha = 0;
		for (int i = 0; i < subsets.length; i ++){
			String set = String.valueOf(subsets[i]);
			red   = (int)(red*0.5 + sets.get(set).getRed()*0.5);
			green += (int)(green*0.5 + sets.get(set).getGreen()*0.5);
			blue  += (int)(blue*0.5 + sets.get(set).getBlue()*0.5);
			alpha += sets.get(set).getAlpha();
		}

		red = red/combination.length();
		green = green/combination.length();
		blue = blue/combination.length();
		alpha = alpha/combination.length();

		return new Color(red,green,blue,alpha);
	}

	/**
	 * Returns an array holding the three color components of this color.
	 * @param color - the color in question
	 * @return - the values of the RGB components
	 */
	public int[] getColorComponents(Color color){
		return new int[] {color.getRed(),
				color.getGreen(), color.getBlue()};
	}

	/**
	 * Retrieve a key from a map based on it's value.
	 * @param map - the map containing the entries
	 * @param value - the value associated with the key
	 * @return - the key mapped to this value
	 */
	public static <T, E> T getKeyByValue(TreeMap<T, E> map, E value) {
		for (Entry<T, E> entry : map.entrySet())
			if (value.equals(entry.getValue()))
				return entry.getKey();
		return null;
	}

	//TESTING AND DEBUG
	/**
	 * Initializes the frame of the Venn diagram.
	 * Most probably will be changed once implemented in Jerboa.
	 *
	 */
	private void initFrame(){

		frame = new JFrame("Venn Diagram");
		frame.setLayout(new BorderLayout());
		frame.setSize(600, 600);
		frame.setPreferredSize(new Dimension(600, 600));
		frame.add(this, BorderLayout.CENTER);
		frame.addMouseListener(this);

		Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
		int x = (int) ((dimension.getWidth() - frame.getWidth())/2);
		int y = (int) ((dimension.getHeight() - frame.getHeight())/2);
		frame.setLocation(x, y);

		frame.pack();
		frame.setVisible(true);

		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e){
				System.exit(0);
			}
		});
	}

	private void initSets(){
		sets = new TreeMap<String, Color>();
		for (int i = 0 ; i < nbSets; i ++){
			sets.put(setLabels[i], setColors[i]);
		}
	}

	@Override
	/**
	 * Getting the X and Y coordinates relative to the center of the frame.
	 * Used to place labels.
	 */
	public void mouseClicked(MouseEvent e) {
		//try {
		//	Robot robot = new Robot();
		//	Color col = robot.getPixelColor((int)e.getX(), (int)e.getY());
		System.out.println("X: "+(e.getX()-frame.getWidth()/2)+
				" Y: "+(e.getY()-frame.getHeight()/2)); //+" corresponds to: "+key);

		AffineTransform at = g2.getTransform();
		at.scale(0.5, 0.5);
		g2.setTransform(at);

		repaint();

		//} catch (AWTException e1) {
		//	e1.printStackTrace();
		//}
	}

	//DEPENDENCY STUBS
	@Override
	public void mouseEntered(MouseEvent arg0) {}
	@Override
	public void mouseExited(MouseEvent arg0) {}
	@Override
	public void mousePressed(MouseEvent arg0) {}
	@Override
	public void mouseReleased(MouseEvent arg0) {}

}
