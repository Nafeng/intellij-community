package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

import java.awt.*;

public class BackspaceHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public BackspaceHandler(EditorActionHandler originalHandler){
    myOriginalHandler = originalHandler;
  }

  public void execute(Editor editor, DataContext dataContext){
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    if (lookup == null){
      myOriginalHandler.execute(editor, dataContext);
      return;
    }

    final String prefix = lookup.getAdditionalPrefix();
    if (prefix.length() > 0) {
      lookup.setAdditionalPrefix(prefix.substring(0, prefix.length() - 1));
      if (lookup.isVisible()) {
        Point point = lookup.calculatePosition();
        Dimension preferredSize = lookup.getComponent().getPreferredSize();
        lookup.setBounds(point.x,point.y,preferredSize.width,preferredSize.height);
        lookup.getList().repaint();
      }
    }
    else{
      lookup.setAdditionalPrefix(lookup.getAdditionalPrefix()); //to clear initial prefix
      lookup.hide();
    }

    myOriginalHandler.execute(editor, dataContext);
  }
}
