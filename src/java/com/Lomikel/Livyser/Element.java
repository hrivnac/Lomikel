package com.Lomikel.Livyser;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>Element</code> is a top level entity.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Element {

  /** Create new Element.
    * @param name The Element name. */
  public Element(String  name) {
    _name = name;
    }
    
  /** Give the Element name.
    * @return The Element name. */
  public String name() {
    return _name;
    }
    
  @Override
  public int hashCode() {
    return (getClass().getName() +  "#" + name()).hashCode();
    }
    
  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
      }
    if (o == this) {
      return true;
      }
    if (getClass() != o.getClass()) {
      return false;
      }     
    return (this.name().equals(((Element)o).name())); 
    }
    
  @Override
  public String toString() {
    return _name;
    }
    
  private String _name;
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(Element.class);

  }
