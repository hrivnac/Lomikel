package com.astrolabsoftware.FinkBrowser.Januser;

import java.util.Objects;

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
    int comparison = _survey.compareTo(o._survey);
    if (comparison == 0) {
      comparison = _classifier.compareTo(o._classifier);
      }
    if (comparison == 0) {
      comparison = _flavor.compareTo(o._flavor);
      }
    if (comparison == 0) {
      comparison = _cls.compareTo(o._cls);
      }
    return comparison;
    }
    
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
      }
    if (!(o instanceof OCol)) {
      return false;
      }
    OCol other = (OCol)o;
    return Objects.equals(_survey,     other._survey)     &&
           Objects.equals(_classifier, other._classifier) &&
           Objects.equals(_flavor,     other._flavor)     &&
           Objects.equals(_cls,        other._cls);
    }
    
  @Override
  public int hashCode() {
    return Objects.hash(_survey, _classifier, _flavor, _cls);
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
  
  }