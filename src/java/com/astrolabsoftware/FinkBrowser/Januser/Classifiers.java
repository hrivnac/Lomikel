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
    @Override
    public Classifier instance(String flavor) {
      return new FinkPortalClassifier(flavor);
      }
    },
  FINK {
    @Override
    public Classifier instance() {
      return new FinkClassifier();
      }
    @Override
    public Classifier instance(String flavor) {
      return new FinkClassifier(flavor);
      }
    },
  XMATCH {
    @Override
    public Classifier instance() {
      return new XMatchClassifier();
      }
    public Classifier instance(String flavor) {
      return new XMatchClassifier(flavor);
      }
    },
  FEATURES {
    @Override
    public Classifier instance() {
      return new FeaturesClassifier();
      }
    public Classifier instance(String flavor) {
      return new FeaturesClassifier(flavor);
      }
    },
  LIGHTCURVES {
    @Override
    public Classifier instance() {
      return new LightCurvesClassifier();
      }
    public Classifier instance(String flavor) {
      return new LightCurvesClassifier(flavor);
      }
    },
  TAG {
    @Override
    public Classifier instance() {
      return new TagClassifier();
      }
    public Classifier instance(String flavor) {
      return new TagClassifier(flavor);
      }
    },
  UNKNOWN {
    @Override
    public Classifier instance() {
      return new FinkPortalClassifier();
      }
    public Classifier instance(String flavor) {
      return new FinkPortalClassifier(flavor);
      }
    };
  
  /** Give the instance of the appropriate {@link Classifier}.
    * @return The instance of the appropriate {@link Classifier}. */
  public abstract Classifier instance();
  
  public abstract Classifier instance(String flavor);
  
  }
