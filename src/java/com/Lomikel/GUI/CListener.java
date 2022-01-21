package com.Lomikel.GUI;

// Java
import java.awt.Component;
import static java.lang.Math.pow;

// Swing
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/** <code>CListener</code> implements {@link ChangeListener}.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public final class CListener implements ChangeListener {

  /** The state has changed,
    * analyse the {@link ChangeEvent} and perform appropriate action.
    * @param ce The {@link ChangeEvent} to be an analysed. */
  public final void stateChanged(ChangeEvent ce) {
    //String name = ((Component)ce.getSource()).getName();
    //final int value = ((JSlider)ce.getSource()).getValue();
    }

  }
