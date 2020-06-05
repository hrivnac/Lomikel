package com.Lomikel.GUI;

// Swing
import javax.swing.JLabel;

/** About {@link JLabel}.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public final class AboutLabel extends JLabel {

  public AboutLabel() {
    setIcon(Icons.lomikel);
    setToolTipText("<h3>http://cern.ch/hrivnac/Activities/Packages/Lomikel</h3></html>");
    }

  }
