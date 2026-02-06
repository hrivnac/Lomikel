package com.astrolabsoftware.FinkBrowser.Januser;

// Tinker Pop
import org.apache.tinkerpop.gremlin.structure.Vertex;

/** <code>FullClass</code> captures alert class together with its
  * {@link Classifier}. It allows handling classes with same names for different
  * {@link Classifier}s.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class FullClass implements Comparable<FullClass> {

  /** Create with class and {@link Classifier}.
    * @param ocol The <em>OCol</em> {@link Vertex}. */
  public FullClass(Vertex ocol) {
    _classifier = ocol.property("survey"    ).value().toString() + "-"
                + ocol.property("classifier").value().toString() + "-"
                + ocol.property("flavor"    ).value().toString();
    _cls        = ocol.property("cls"       ).value().toString();
    }

  @Override
    public int compareTo(FullClass o) {
      return this.hashCode() - o.hashCode();
      }    
    
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
      }
    if (!(o instanceof FullClass)) {
      return false;
      }
    return o.hashCode() == this.hashCode();
    }
    
  @Override
  public int hashCode() {
    if (_hash == 0) {
      _hash = (_cls + _classifier).hashCode();
      }
    return _hash;
    }
    
  /** Give contained alarm class.
    * @return The contained alarm class. */
  public String cls() {
    return _cls;
    }
    
  @Override
  public String toString() {
    return _cls + " of " + _classifier;
    }
    
  private String _classifier;
  
  private String _cls;
  
  private int _hash = 0;
      
  }