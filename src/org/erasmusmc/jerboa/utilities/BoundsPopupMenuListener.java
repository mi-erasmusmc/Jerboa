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

/******************************************************************************************
 * Jerboa software version 3.0 - Copyright Erasmus University Medical Center, Rotterdam   *
 *																				          *
 * Author: Marius Gheorghe (MG) - department of Medical Informatics						  *
 * 																						  *
 * $Rev:: 4850              $:  Revision of last commit                                   *
 * $Author:: MG     $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities;

 import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicComboPopup;

 /**
  * This class is an utility to display the text of the items of the combo boxes, that are wider
  * than the actual width of the combo box itself.
  * All rights reserved to the developer.
  * Class copied from:
  * @see <a href="https://github.com/NiceSystems/hrider/blob/master/src/main/java/hrider/ui/controls/BoundsPopupMenuListener.java">
  * https://github.com/NiceSystems/hrider/blob/master/src/main/java/hrider/ui/controls/BoundsPopupMenuListener.java</a>
  *
  */
 public class BoundsPopupMenuListener  implements PopupMenuListener {

	//region variables
   private boolean scrollBarRequired = true;
   private boolean popupWider;
   private int maximumWidth = -1;
   private boolean popupAbove;
   private JScrollPane scrollPane;

   /**
    * Convenience constructor to allow the display of a horizontal scrollbar
    * when required.
    */
   public BoundsPopupMenuListener(){
     this(true, false, -1, false);
   }

   /**
    * Convenience constructor that allows you to display the popup
    * wider and/or above the combo box.
    *
    * @param popupWider when true, popup width is based on the popup
    *                   preferred width
    * @param popupAbove when true, popup is displayed above the combobox
    */
   public BoundsPopupMenuListener(boolean popupWider, boolean popupAbove){
     this(false, popupWider, -1, popupAbove);
   }

   /**
    * Convenience constructor that allows you to display the popup
    * wider than the combo box and to specify the maximum width
    *
    * @param maximumWidth the maximum width of the popup. The
    *                     popupAbove value is set to "true".
    */
   public BoundsPopupMenuListener(int maximumWidth){
     this(true, true, maximumWidth, false);
   }

   /**
    * General purpose constructor to set all popup properties at once.
    *
    * @param scrollBarRequired display a horizontal scrollbar when the
    *                          preferred width of popup is greater than width of scrollPane.
    * @param popupWider        display the popup at its preferred with
    * @param maximumWidth      limit the popup width to the value specified
    *                          (minimum size will be the width of the combo box)
    * @param popupAbove        display the popup above the combo box
    */
   public BoundsPopupMenuListener(boolean scrollBarRequired, boolean popupWider, int maximumWidth, boolean popupAbove){
     setScrollBarRequired(scrollBarRequired);
     setPopupWider(popupWider);
     setMaximumWidth(maximumWidth);
     setPopupAbove(popupAbove);
   }

   /**
    * Return the maximum width of the popup.
    *
    * @return the maximumWidth value
    */
   public int getMaximumWidth(){
     return this.maximumWidth;
   }

   /**
    * Set the maximum width for the popup. This value is only used when
    * setPopupWider( true ) has been specified. A value of -1 indicates
    * that there is no maximum.
    *
    * @param maximumWidth the maximum width of the popup
    */
   public void setMaximumWidth(int maximumWidth){
     this.maximumWidth = maximumWidth;
   }

   /**
    * Determine if the popup should be displayed above the combo box.
    *
    * @return the popupAbove value
    */
   public boolean isPopupAbove(){
     return this.popupAbove;
   }

   /**
    * Change the location of the popup relative to the combo box.
    *
    * @param popupAbove true display popup above the combo box,
    *                   false display popup below the combo box.
    */
   public void setPopupAbove(boolean popupAbove){
     this.popupAbove = popupAbove;
   }

   /**
    * Determine if the popup might be displayed wider than the combo box
    *
    * @return the popupWider value
    */
   public boolean isPopupWider(){
     return this.popupWider;
   }

   /**
    * Change the width of the popup to be the greater of the width of the
    * combo box or the preferred width of the popup. Normally the popup width
    * is always the same size as the combo box width.
    *
    * @param popupWider true adjust the width as required.
    */
   public void setPopupWider(boolean popupWider){
     this.popupWider = popupWider;
   }

   /**
    * Determine if the horizontal scroll bar might be required for the popup
    *
    * @return the scrollBarRequired value
    */
   public boolean isScrollBarRequired(){
     return this.scrollBarRequired;
   }

   /**
    * For some reason the default implementation of the popup removes the
    * horizontal scrollBar from the popup scroll pane which can result in
    * the truncation of the rendered items in the popop. Adding a scrollBar
    * back to the scrollPane will allow horizontal scrolling if necessary.
    *
    * @param scrollBarRequired true add horizontal scrollBar to scrollPane
    *                          false remove the horizontal scrollBar
    */
   public void setScrollBarRequired(boolean scrollBarRequired){
     this.scrollBarRequired = scrollBarRequired;
   }

   /**
    * Alter the bounds of the popup just before it is made visible.
    */
   public void popupMenuWillBecomeVisible(PopupMenuEvent e){
     @SuppressWarnings("rawtypes")
	JComboBox comboBox = (JComboBox)e.getSource();
     if (comboBox.getItemCount() == 0) {
       return;
     }
     final Object child = comboBox.getAccessibleContext().getAccessibleChild(0);
     if ((child instanceof BasicComboPopup)){
       SwingUtilities.invokeLater(new Runnable(){
         public void run(){
           BoundsPopupMenuListener.this.customizePopup((BasicComboPopup)child);
         }
       });
     }
   }

   protected void customizePopup(BasicComboPopup popup){
     this.scrollPane = getScrollPane(popup);
     if (this.popupWider){
       popupWider(popup);
     }
     checkHorizontalScrollBar(popup);

     Component comboBox = popup.getInvoker();
     Point location = comboBox.getLocationOnScreen();
     if (this.popupAbove){
       int height = popup.getPreferredSize().height;
       popup.setLocation(location.x, location.y - height);
     }else{
       int height = comboBox.getPreferredSize().height;
       popup.setLocation(location.x, location.y + height - 1);
       popup.setLocation(location.x, location.y + height);
     }
   }

   /**
    *  Adjust the width of the scrollpane used by the popup.
    *  @param popup - the combo box popup
    */
   protected void popupWider(BasicComboPopup popup){
     @SuppressWarnings("rawtypes")
	JList list = popup.getList();

     //  Determine the maximimum width to use:
     //  a) determine the popup preferred width
     //  b) limit width to the maximum if specified
     //  c) ensure width is not less than the scroll pane width
     int popupWidth = list.getPreferredSize().width +
       5 +
       getScrollBarWidth(popup, this.scrollPane);
     if (this.maximumWidth != -1) {
       popupWidth = Math.min(popupWidth, this.maximumWidth);
     }
     Dimension scrollPaneSize = this.scrollPane.getPreferredSize();
     popupWidth = Math.max(popupWidth, scrollPaneSize.width);
     scrollPaneSize.width = popupWidth;
     this.scrollPane.setPreferredSize(scrollPaneSize);
     this.scrollPane.setMaximumSize(scrollPaneSize);
   }

   /**
    * This method is called every time:
    *  - to make sure the viewport is returned to its default position
    *  - to remove the horizontal scrollbar when it is not wanted
    *  @param popup - the combo box popup
    */
   private void checkHorizontalScrollBar(BasicComboPopup popup){
     JViewport viewport = this.scrollPane.getViewport();
     Point p = viewport.getViewPosition();
     p.x = 0;
     viewport.setViewPosition(p);
     if (!this.scrollBarRequired)
     {
       this.scrollPane.setHorizontalScrollBar(null);
       return;
     }
     JScrollBar horizontal = this.scrollPane.getHorizontalScrollBar();
     if (horizontal == null)
     {
       horizontal = new JScrollBar(0);
       this.scrollPane.setHorizontalScrollBar(horizontal);
       this.scrollPane.setHorizontalScrollBarPolicy(30);
     }
     if (horizontalScrollBarWillBeVisible(popup, this.scrollPane))
     {
       Dimension scrollPaneSize = this.scrollPane.getPreferredSize();
       scrollPaneSize.height += horizontal.getPreferredSize().height;
       this.scrollPane.setPreferredSize(scrollPaneSize);
       this.scrollPane.setMaximumSize(scrollPaneSize);
       this.scrollPane.revalidate();
     }
   }

   /**
    *  Get the scroll pane used by the popup so its bounds can be adjusted.
    *  @param popup - the combo box popup
    *  @return - the scroll pane of the combo box popup
    */
   @SuppressWarnings("rawtypes")
   protected JScrollPane getScrollPane(BasicComboPopup popup){
     JList list = popup.getList();
     Container c = SwingUtilities.getAncestorOfClass(JScrollPane.class, list);

     return (JScrollPane)c;
   }

   /**
    *  I can't find any property on the scrollBar to determine if it will be
    *  displayed or not so use brute force to determine this.
    *  @param popup - the combo box popup
    *  @param scrollPane - the panel that allows scrolling
    *  @return - the width of the scroll bar
    */
   @SuppressWarnings("rawtypes")
   protected int getScrollBarWidth(BasicComboPopup popup, JScrollPane scrollPane){
     int scrollBarWidth = 0;
     JComboBox comboBox = (JComboBox)popup.getInvoker();
     if (comboBox.getItemCount() > comboBox.getMaximumRowCount()){
       JScrollBar vertical = scrollPane.getVerticalScrollBar();
       scrollBarWidth = vertical.getPreferredSize().width;
     }
     return scrollBarWidth;
   }

   /**
    *  I can't find any property on the scrollBar to determine if it will be
    *  displayed or not so use brute force to determine this.
    *  @param popup - the combo box popup
    *  @param scrollPane - the panel that allows scrolling
    *  @return - true if the scroll bar will be visible; false otherwise
    */
   @SuppressWarnings("rawtypes")
   protected boolean horizontalScrollBarWillBeVisible(BasicComboPopup popup, JScrollPane scrollPane){
     JList list = popup.getList();
     int scrollBarWidth = getScrollBarWidth(popup, scrollPane);
     int popupWidth = list.getPreferredSize().width + scrollBarWidth;

     return popupWidth > scrollPane.getPreferredSize().width;
   }

   public void popupMenuCanceled(PopupMenuEvent e) {}

   public void popupMenuWillBecomeInvisible(PopupMenuEvent e){
	 //  In its normal state the scrollpane does not have a scrollbar
     if (this.scrollPane != null) {
       this.scrollPane.setHorizontalScrollBar(null);
     }
   }

 }
