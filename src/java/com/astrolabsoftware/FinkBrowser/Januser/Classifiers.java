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
  FINK {
    @Override
    public Classifier instance() {
      return new FinkClassifier();
      }
    },
  FEATURES {
    @Override
    public Classifier instance() {
      return new FeaturesClassifier();
      }
    },
  LIGHTCURVES {
    @Override
    public Classifier instance() {
      return new LightCurvesClassifier();
      }
    },
  TAG {
    @Override
    public Classifier instance() {
      return new TagClassifier();
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
