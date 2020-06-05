package com.Lomikel.GUI;

// AWT
import java.awt.Color;
import java.awt.Font;

// Swing
import javax.swing.JLabel;

/** Simple {@link JLabel}.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public final class SimpleLabel extends JLabel {

  public SimpleLabel(String name, 
                     Color color, 
                     Font font, 
                     String tip) {
    super(name); 
    setForeground(color);  
    setFont(font);
    if (tip != null) {
      setToolTipText(tip);
      }
    }

  }
