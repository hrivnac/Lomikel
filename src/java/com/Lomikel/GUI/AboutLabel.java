package com.Lomikel.GUI;

// Swing
import javax.swing.JLabel;
import javax.swing.ImageIcon;

/** About {@link JLabel}.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public final class AboutLabel extends JLabel {

  public AboutLabel(ImageIcon icon,
                    String toolTip) {
    setIcon(icon);
    setToolTipText(toolTip);
    }

  }
