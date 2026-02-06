package com.astrolabsoftware.FinkBrowser.Januser;

/** <code>FullClass</code> captures alert class together with its
  * {@link Classifier}. It allows handling classes with same names for different
  * {@link Classifier}s.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class FullClass {

  /** Create with class and {@link Classifier}.
    * @param classifier The {@link Classifier}.
    * @param cls        The class. */
  public FullClass(Classifier classifier,
                   String     cls) {
    _classifier = classifier;
    _cls        = cls;
    }
    
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
      }
    if (!(o instanceof FullClass)) {
      return false;
      }
    return o.hashCode() == hashCode();
    }
    
  @Override
  public int hashCode() {
    if (_hash == 0) {
      _hash = (_cls + _classifier.name() + _classifier.name() + _classifier.flavor()).hashCode();
      }
    return _hash;
    }
    
    
  private Classifier _classifier;
  
  private String _cls;
  
  private int _hash = 0;
      
  }