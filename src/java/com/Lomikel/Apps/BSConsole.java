package com.Lomikel.Apps;

// AWT
import java.awt.Color;
import java.awt.Font;
import java.awt.Dimension;

// Swing
import javax.swing.JScrollPane;
import javax.swing.BorderFactory;

// Bean Shell
import bsh.util.JConsole;

// Log4J
import org.apache.log4j.Logger;

/** Bean Shell {@link JConsole}.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public final class BSConsole extends JConsole {

  /** Create. */
  public BSConsole() {
    _this = this;
    setFont(new Font("Helvetica", Font.PLAIN, 15));
    setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
    setBorder(BorderFactory.createEtchedBorder());
    setSize(                     Integer.MAX_VALUE, 600);
    setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
    setMinimumSize(new Dimension(1200,              600));
    setWaitFeedback(true);
    }    
    
  /** Add text to {@link JConsole}.
    * Write to <em>stdout</em> if {@link JConsole} not yet initialised.
    * @param text The text to be added to {@link JConsole}. */
  public static void setText(String text) {
    if (_this == null) {
      //System.out.println(text);
      }
    else {
      _this.print(text + "\n", new java.awt.Font("Helvetica", java.awt.Font.PLAIN, 20), java.awt.Color.blue);
      }
    }
    
  /** Add error text to {@link JConsole}.
    * Write to <em>stderr</em> if {@link JConsole} not yet initialised.
    * @param text The error text to be added to  {@link JConsole}. */
  public static void setError(String text) {
    if (_this == null) {
      //System.err.println(text);
      }
    else {
      _this.print(text + "\n", new java.awt.Font("Helvetica", java.awt.Font.PLAIN, 20), java.awt.Color.red);
      }
    }
  
  private static BSConsole _this;
    
  /** Logging . */
  private static Logger log = Logger.getLogger(BSConsole.class);

  }
