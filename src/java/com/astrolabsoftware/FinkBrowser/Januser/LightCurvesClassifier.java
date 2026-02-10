package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.Utils.LomikelException;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>FeaturesClassifier</code> classifies sources according to
  * HBase <tt>lc_features_*</tt> field.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// BUG: jd should be String or long
public class LightCurvesClassifier extends ZTFClassifier {
  
  @Override
  public void classify(FinkGremlinRecipies recipies,
                       String              oid) throws LomikelException {
    // TBD
    }
    
  /** Logging . */
  private static Logger log = LogManager.getLogger(LightCurvesClassifier.class);
  
  }
           
           
