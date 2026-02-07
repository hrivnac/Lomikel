package com.astrolabsoftware.FinkBrowser.Januser;

// Tinker Pop
import org.apache.tinkerpop.gremlin.structure.Vertex;

/** <code>OCol</code> captures <em>OCol</em> {@link Vertex}.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class OCol implements Comparable<OCol> {

  /** Create}.
    * @param ocol The <em>OCol</em> {@link Vertex}. */
  public OCol(Vertex ocol) {
    _survey     = ocol.property("survey"    ).value().toString();
    _classifier = ocol.property("classifier").value().toString();
    _flavor     = ocol.property("flavor"    ).value().toString();
    _cls        = ocol.property("cls"       ).value().toString();
    }

  @Override
    public int compareTo(OCol o) {
      return this.hashCode() - o.hashCode();
      }    
    
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
      }
    if (!(o instanceof OCol)) {
      return false;
      }
    return o.hashCode() == this.hashCode();
    }
    
  @Override
  public int hashCode() {
    if (_hash == 0) {
      _hash = (_survey + _classifier + _flavor + _cls).hashCode();
      }
    return _hash;
    }
    
  /** Give contained classifier survey.
    * @return The contained classifier survey. */
  public String survey() {
    return _survey;
    }
    
  /** Give contained classifier name.
    * @return The contained classifier name. */
  public String classifier() {
    return _classifier;
    }
    
  /** Give contained classifier flavor.
    * @return The contained classifier flavor. */
  public String flavor() {
    return _flavor;
    }
    
  /** Give contained classifier class.
    * @return The contained classifier class. */
  public String cls() {
    return _cls;
    }
    
  @Override
  // as in Classifier
  public String toString() {
    String ts = _cls + " of " + _classifier;
    if (_flavor != null && !_flavor.equals("")) {
      ts += "=" + _flavor;
      }
    ts += "[" + _survey + "]";
    return ts;
    }
  
  private String _survey;
    
  private String _classifier;
  
  private String _flavor;
  
  private String _cls;
  
  private int _hash = 0;
      
  }