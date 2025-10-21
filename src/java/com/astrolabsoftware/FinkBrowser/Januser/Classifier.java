package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.Utils.Pair;
import com.Lomikel.Utils.LomikelException;

// Java
import java.util.Map;
import java.util.HashMap;

/** <code>Classifier</code> classifies <em>objects</em>.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public abstract class Classifier {
  
  public static Classifier instance(Type type) throws LomikelException {
    return instance(type, "");
    }
    
  public static Classifier instance(String name) throws LomikelException {
    String[] fullName = name.split("=");
    if (fullName.length == 2) {
      return instance(fullName[0], fullName[1]);
      }
    else if (fullName.length == 1) {
      return instance(name, "");
      }
    else {
      throw new LomikelException("Unknown Classifier name " + name);
      }
    }
 
  public static Classifier instance(String name,
                                    String flavor) throws LomikelException {
    return instance(Type.valueOf(name), flavor);
    }
    
  /** Give singleton {@link Classifier} for each type and flavor.
    * @param type The {@link Classifier} {@link Type}.
    * @param flavor The {@link Classifier} flavor.
    * @throws LomikelException If {@link Classifier} cannot be created. */
  public static Classifier instance(Type   type,
                                    String flavor) throws LomikelException {
    Pair<Type, String> signature = Pair.of(type, flavor);
    if (!_classifiers.containsKey(signature)) {
      Classifier cl;
      switch (type) {
        case FINK_PORTAL:
          cl = new FinkPortalClassifier();
          break;
        case FINK:
          cl = new FinkClassifier();
          break;
        case XMATCH:
          cl = new XMatchClassifier();
          break;
        case FEATURES:
          cl = new FeaturesClassifier();
          break;
        case LIGHTCURVES:
          cl = new LightCurvesClassifier();
          break;
        case TAG:
          cl = new TagClassifier();
          break;
        default:
          throw new LomikelException("Unknown Classifier Type " + type);
        }
      cl.setType(type);
      cl.setFlavor(flavor);
      classifiers().put(signature, cl);
      }
    return classifiers().get(signature);
    }
    
  /** Classify <em>object</em> and expand them to <em>source</em>s (if requested).
    * It should register classes corresponding to specified <tt>objectId</tt>
    * using {@link FinkGremlinRecipies#registerOCol(Classifiers, String, String, double, String, String, boolean, String)}.
    * @param recipies   The {@link FinkGremlinRecipies} caller.
    * @param oid        The <tt>objectId</tt> of <em>object</em> to be added.
    * @throws LomikelException If anything fails. */
  public abstract void classify(FinkGremlinRecipies recipies,
                                String              oid) throws LomikelException;
  
  /** Set {@link Classifier} flavor.
    * @param flavor The {@link Classifier} flavor. */
  protected void setFlavor(String flavor) {
    _flavor = flavor;
    }  
    
  protected void setType(Type type) {
    _type = type;
    }    
  
  public String flavor() {
    return _flavor;
    }
  
  public String name() {
    return _type.name();
    }
    
  public String toString() {
    return name() + "[" + flavor() + "]";
    }
    
  private Type _type;  
    
  private String _flavor;
    
  protected static Map<Pair<Type, String>, Classifier> classifiers() {
    return _classifiers;
    }
  
  private static Map<Pair<Type, String>, Classifier> _classifiers = new HashMap<>();
  
  /** The type of {@link Classifier}. Each {@link Type} can exist in many flavors. */
  public static enum Type {
    FINK_PORTAL,
    FINK,
    XMATCH,
    FEATURES,
    LIGHTCURVES,
    TAG
    }

  }
