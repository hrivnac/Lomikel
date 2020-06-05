package com.Lomikel.GUI;

// Swing
import javax.swing.ImageIcon;

/** Set of used {@link ImageIcon}s.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public final class Icons {
  
  private static final ClassLoader myLoader = (new Icons()).getClass().getClassLoader();

  public static final ImageIcon lomikel = new ImageIcon(myLoader.getResource("com/Lomikel/GUI/images/Lomikel.png"));

  public static final ImageIcon exit    = new ImageIcon(myLoader.getResource("com/Lomikel/GUI/images/Exit.png"));
 
  public static ImageIcon icon(String resource) {
    return new ImageIcon(myLoader.getResource(resource));
    }

  }
