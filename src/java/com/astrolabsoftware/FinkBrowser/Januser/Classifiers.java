package com.astrolabsoftware.FinkBrowser.Januser;

/** <code>Classifiers</code> specifies how sources and alerts are classified.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public enum Classifiers {
  FINK_PORTAL {
    @Override
    public Classifier instance() {
      return new FinkPortalClassifier();
      }
    },
  UNKNOWN {
    @Override
    public Classifier instance() {
      return new FinkPortalClassifier();
      }
    };
  
  /** Give the instance of the appropriate {@link Classifier}.
    * @return The instance of the appropriate {@link Classifier}. */
  public abstract Classifier instance();
  
  }
