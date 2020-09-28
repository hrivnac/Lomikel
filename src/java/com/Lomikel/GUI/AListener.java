package com.Lomikel.GUI;

// Java
import static java.lang.Math.floor;

// AWT
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

// Log4J
import org.apache.log4j.Logger;

/** <code>AListener</code> implements {@link ActionListener}.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public final class AListener implements ActionListener {

  /** The {@link ActionEvent} has hapened,
    * analyse it and perform appropriate reaction.
    * @param ae The {@link ActionEvent} to be an analysed. */
  public final void actionPerformed(ActionEvent ae) {
    String name = ((Component)ae.getSource()).getName();
    String value = ae.getActionCommand();
    if (value.equals("Exit")) {
      System.exit(0);
      }      
    }
 
  /** Logging . */
  private static Logger log = Logger.getLogger(AListener.class);

  }
