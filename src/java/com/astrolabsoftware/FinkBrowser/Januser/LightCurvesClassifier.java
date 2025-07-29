package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.HBaser.HBaseClient;
import com.Lomikel.Utils.LomikelException;
import com.astrolabsoftware.FinkBrowser.FinkPortalClient.FPC;
import com.astrolabsoftware.FinkBrowser.HBaser.Clusteriser.ClusterFinder;

// org.json
import org.json.JSONArray;
import org.json.JSONObject;

// Java
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;
import java.util.TreeMap;
import java.io.IOException;

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
public class LightCurvesClassifier extends Classifier {
  
  public LightCurvesClassifier(String flavor) {
    super(flavor);
    }
  
  public LightCurvesClassifier() {
    super();
    }
  
  @Override
  public void classify(FinkGremlinRecipies recipies,
                       String              oid,
                       boolean             enhance,
                       String              columns) throws LomikelException {
    double jd;
    String cl;
    Map<String, String> value;
    String[] featuresS;
    double[] featuresD;
    String fg;
    String fr;
    Map<String, Set<Double>> classes; // cl -> [jd]
    Set<Double> jds;
    String key;
    Set<Double> val;
    double weight;
    double totalWeight;
    Map<String, Map<String, String>> alerts = recipies.fhclient().scan(null,
                                                                       "key:key:" + oid + ":prefix",
                                                                       "i:jd,d:lc_features_g,d:lc_features_r",
                                                                       0,
                                                                       0,
                                                                       false,
                                                                       false);
    classes = new TreeMap<>();
    // get all alerts (jd) and their classses
    for (Map.Entry<String, Map<String, String>> entry : alerts.entrySet()) {
      value = entry.getValue();
      jd = Double.parseDouble(value.get("i:jd"));
      if (value.containsKey("d:lc_features_g") &&
          value.containsKey("d:lc_features_r")) {
        fg = value.get("d:lc_features_g").replaceFirst("\\[", "").replaceAll("]$", "");
        fr = value.get("d:lc_features_r").replaceFirst("\\[", "").replaceAll("]$", "");
        featuresS = (fg + "," + fr).replaceAll("null", "0.0").
                                    replaceAll("NaN", "0.0").
                                    split(",");
        featuresD = Arrays.stream(featuresS).
                           mapToDouble(Double::parseDouble).
                           toArray();
        cl = String.valueOf(finder().transformAndPredict(featuresD));                  
        if (!cl.equals("-1")) {
          if (classes.containsKey(cl)) {
            jds = classes.get(cl);
            jds.add(jd);
            }
          else {
            jds = new TreeSet<Double>();
            jds.add(jd);
            classes.put(cl, jds);
            }
          }
        }
      }
    totalWeight = 0;
    for (Map.Entry<String, Set<Double>> cls : classes.entrySet()) {
      totalWeight += cls.getValue().size();
      }
    for (Map.Entry<String, Set<Double>> cls : classes.entrySet()) {
      key = "FC-" + cls.getKey();
      val = cls.getValue();
      weight = val.size() / totalWeight;
      recipies.registerSoI(Classifiers.FEATURES, key, oid, weight, val, enhance, columns);
      }
    }
    
  /** Give {@link ClusterFinder} to current database. Singleton.
    * @return The corresponding {@link ClusterFinder}. 
    * @throws LomikelExceltion If {@link ClusterFinder} cannot be created. */
  private ClusterFinder finder() throws LomikelException {
    if (_finder == null) {
      if (_dirName == null) {
        _dirName = "/tmp";
        }
      try {
        _finder = new ClusterFinder(_dirName + "/scaler_params.json",
                                    _dirName + "/pca_params.json",
                                    _dirName + "/cluster_centers.json");
        }
      catch (IOException e) {
        throw new LomikelException("Cannot create Cluster Finder", e);
        }
      }
    return _finder;
    }
    
  /** Set the directory for model json files
    * <tt>scaler_params.json, pca_params.json, cluster_centers.json</tt>.
    * If not set, <tt>/tmp</tt> will be used.
    * @param dirName The directory for model json files. */
  public void setModelDirectory(String dirName) {
    _dirName = dirName;
    }
  
  private static ClusterFinder _finder;
  
  private static String _dirName;

  /** Logging . */
  private static Logger log = LogManager.getLogger(LightCurvesClassifier.class);
  
  }
           
           
