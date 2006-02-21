package com.intellij.uiDesigner.designSurface;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.core.GridConstraints;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * @author yole
 */
public class DraggedComponentList implements Transferable, ComponentDragObject {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.DraggedComponentList");

  private static DataFlavor ourDataFlavor;

  static {
    try {
      ourDataFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType);
    } catch (ClassNotFoundException e) {
      LOG.error(e);
    }
  }

  private ArrayList<RadComponent> mySelection;
  private GridConstraints[] myOriginalConstraints;
  private Rectangle[] myOriginalBounds;
  private RadContainer[] myOriginalParents;
  private int myDragRelativeColumn = 0;
  private int myComponentUnderMouseColumn;
  private int myComponentUnderMouseRow;
  private int myDragDeltaX = 0;
  private int myDragDeltaY = 0;
  private boolean myHasDragDelta = false;

  private DraggedComponentList(final GuiEditor editor, final Point pnt) {
    getSelectionFromEditor(editor);

    // It's very important to get component under mouse before the components are
    // removed from their parents.

    RadComponent componentUnderMouse = null;
    if (mySelection.size() > 0) {
      int componentUnderMouseIndex = 0;
      if (pnt != null) {
        for(int i=0; i<mySelection.size(); i++) {
          Point aPoint = SwingUtilities.convertPoint(editor.getRootContainer().getDelegee(), pnt,
                                                  myOriginalParents [i].getDelegee());
          if (myOriginalBounds [i].contains(aPoint)) {
            componentUnderMouseIndex = i;
          }
        }
      }

      componentUnderMouse = mySelection.get(componentUnderMouseIndex);
      myComponentUnderMouseColumn = myOriginalConstraints [componentUnderMouseIndex].getColumn();
      myComponentUnderMouseRow = myOriginalConstraints [componentUnderMouseIndex].getRow();
    }

    LOG.debug("myComponentUnderMouseColumn=" + myComponentUnderMouseColumn +
              ", myComponentUnderMouseRow=" + myComponentUnderMouseRow);

    if (mySelection.size() > 1 && componentUnderMouse != null) {
      for(GridConstraints constraints: myOriginalConstraints) {
        myDragRelativeColumn = Math.max(myDragRelativeColumn,
                                        componentUnderMouse.getConstraints().getColumn() - constraints.getColumn());
      }
    }

     for(RadComponent c: mySelection) {
      JComponent delegee = c.getDelegee();
      if (c == componentUnderMouse && pnt != null) {
        if (delegee.getX() > pnt.x && delegee.getX() + delegee.getWidth() < pnt.x) {
          myDragDeltaX = pnt.x - (delegee.getX() + delegee.getWidth() / 2);
        }
        if (delegee.getY() > pnt.y && delegee.getY() + delegee.getHeight() < pnt.y) {
          myDragDeltaY = pnt.y - (delegee.getY() + delegee.getHeight() / 2);
        }
        myHasDragDelta = true;
      }
    }
  }

  private void getSelectionFromEditor(final GuiEditor editor) {
    // Store selected components
    mySelection = FormEditingUtil.getSelectedComponents(editor);
    // sort selection in correct grid order
    Collections.sort(mySelection, new Comparator<RadComponent>() {
      public int compare(final RadComponent o1, final RadComponent o2) {
        if (o1.getParent() == o2.getParent()) {
          int result = o1.getConstraints().getRow() - o2.getConstraints().getRow();
          if (result == 0) {
            result = o1.getConstraints().getColumn() - o2.getConstraints().getColumn();
          }
          return result;
        }
        return 0;
      }
    });

    // Store original constraints and parents. This information is required
    // to restore initial state if drag is canceled.
    myOriginalConstraints = new GridConstraints[mySelection.size()];
    myOriginalBounds = new Rectangle[mySelection.size()];
    myOriginalParents = new RadContainer[mySelection.size()];
    for (int i = 0; i < mySelection.size(); i++) {
      final RadComponent component = mySelection.get(i);
      myOriginalConstraints[i] = component.getConstraints().store();
      myOriginalBounds[i] = component.getBounds();
      myOriginalParents[i] = component.getParent();
    }
  }

  public static DraggedComponentList pickupSelection(final GuiEditor editor, @Nullable Point pnt) {
    return new DraggedComponentList(editor, pnt);
  }

  @Nullable
  public static DraggedComponentList fromTransferable(final Transferable transferable) {
    if (transferable.isDataFlavorSupported(ourDataFlavor)) {
      Object data;
      try {
        data = transferable.getTransferData(ourDataFlavor);
      }
      catch (Exception e) {
        return null;
      }
      if (data instanceof DraggedComponentList) {
        return (DraggedComponentList) data;
      }
    }
    return null;
  }

  public int getDragDeltaX() {
    return myDragDeltaX;
  }

  public int getDragDeltaY() {
    return myDragDeltaY;
  }

  public ArrayList<RadComponent> getComponents() {
    return mySelection;
  }

  public int getComponentCount() {
    return mySelection.size();
  }

  public RadContainer getOriginalParent(final RadComponent c) {
    return myOriginalParents [mySelection.indexOf(c)];
  }

  /**
   * Returns a copy of the original constraints array.
   */
  public GridConstraints[] getOriginalConstraints() {
    GridConstraints[] result = new GridConstraints[myOriginalConstraints.length];
    for(int i=0; i<myOriginalConstraints.length; i++) {
      result [i] = myOriginalConstraints [i].store();
    }
    return result;
  }

  public Rectangle[] getOriginalBounds() {
    return myOriginalBounds;
  }

  public Rectangle getOriginalBounds(final RadComponent c) {
    return myOriginalBounds [mySelection.indexOf(c)];
  }

  public DataFlavor[] getTransferDataFlavors() {
    return new DataFlavor[] { ourDataFlavor };
  }

  public boolean isDataFlavorSupported(DataFlavor flavor) {
    return flavor.equals(ourDataFlavor);
  }

  public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
    return this;
  }

  public int getHSizePolicy() {
    int result = 0;
    for(GridConstraints c: myOriginalConstraints) {
      result |= c.getHSizePolicy();
    }
    return result;
  }

  public int getVSizePolicy() {
    int result = 0;
    for(GridConstraints c: myOriginalConstraints) {
      result |= c.getVSizePolicy();
    }
    return result;
  }

  public int getRelativeRow(int componentIndex) {
    return myOriginalConstraints [componentIndex].getRow() - myComponentUnderMouseRow;
  }

  public int getRelativeCol(int componentIndex) {
    return myOriginalConstraints [componentIndex].getColumn() - myComponentUnderMouseColumn;
  }

  public int getRowSpan(int componentIndex) {
    return myOriginalConstraints [componentIndex].getRowSpan();
  }

  public int getColSpan(int componentIndex) {
    return myOriginalConstraints [componentIndex].getColSpan();
  }

  public Point getDelta(int componentIndex) {
    return null;
  }

  public RadContainer[] getOriginalParents() {
    return myOriginalParents;
  }

  public boolean hasDragDelta() {
    return myHasDragDelta;
  }
}
