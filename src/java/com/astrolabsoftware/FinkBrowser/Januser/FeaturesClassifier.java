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
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>FeaturesClassifier</code> classifies sources according to
  * HBase <tt>lc_features_*</tt> field.
  * <em>flavor</em> points to resource carrying model. 
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// BUG: jd should be String or long
public class FeaturesClassifier extends Classifier {

  @Override
  public void classify(FinkGremlinRecipies recipies,
                       String              oid) throws LomikelException {
    String jd;
    String cl;
    Map<String, String> value;
    String[] featuresS;
    double[] featuresD;
    String fg;
    String fr;
    Map<String, Set<String>> allInstances; // cl -> [jd]
    Map<String, Double>      allWeights;   // jd -> w
    Set<String> jds;
    String key;
    Map<String, Map<String, String>> alerts = recipies.fhclient().scan(null,
                                                                       "key:key:" + oid + ":prefix",
                                                                       "i:jd,d:lc_features_g,d:lc_features_r",
                                                                       0,
                                                                       0,
                                                                       false,
                                                                       false);
    allInstances = new TreeMap<>();
    allWeights   = new TreeMap<>();
    // get all alerts (jd) and their classses
    boolean isClassified = false;
    for (Map.Entry<String, Map<String, String>> entry : alerts.entrySet()) {
      value = entry.getValue();
      jd = value.get("i:jd");
      if (value.containsKey("d:lc_features_g") &&
          value.containsKey("d:lc_features_r")) {
        fg = value.get("d:lc_features_g").replaceFirst("\\[", "").replaceAll("]$", "");
        fr = value.get("d:lc_features_r").replaceFirst("\\[", "").replaceAll("]$", "");
        // BUG: some models replace by mean
        featuresS = (fg + "," + fr).replaceAll("null", "0.0").
                                    replaceAll("NaN",  "0.0").
                                    split(",");
        featuresD = Arrays.stream(featuresS).
                           mapToDouble(Double::parseDouble).
                           toArray();
        cl = String.valueOf(finder().transformAndPredict(featuresD));                  
        if (!cl.equals("-1")) {
          if (allInstances.containsKey(cl)) {
            jds = allInstances.get(cl);
            jds.add(jd);
            }
          else {
            jds = new TreeSet<String>();
            jds.add(jd);
            allInstances.put(cl, jds);
            }
          allWeights.put(jd, 1.0);
          }
        isClassified = true;
        }
      else {
        //log.warn("Alert " + entry.getKey() + " has no features");
        }
      }
    // rearrange instances and weights and register
    double weight;
    double totalWeight;
    double w;
    totalWeight = 0;
    List<String> instancesL;
    List<Double> weightsL;
    log.info(allWeights);
    for (Map.Entry<String, Set<String>> cls : allInstances.entrySet()) {
      for (String instance : cls.getValue()) {
        log.info("" + allWeights + " " + instance + " " + allWeights.containsKey(instance));
        if (allWeights.containsKey(instance)) {
          totalWeight += allWeights.get(instance);
          }
        }
      }
    for (Map.Entry<String, Set<String>> cls : allInstances.entrySet()) {
      key = "FC-" + cls.getKey();
      instancesL = new ArrayList<String>(cls.getValue());
      weightsL   = new ArrayList<Double>();
      w = 0;
      for (String instance : instancesL) {
        if (allWeights.containsKey(instance)) {
          weightsL.add(allWeights.get(instance));
          w += allWeights.get(instance);
          }
        else {
          weightsL.add(0.0);
          }
        }
      weight = w / totalWeight;
      recipies.registerSoI(this, key, oid, weight, instancesL, weightsL);
      }
    if (!isClassified) {
      log.warn("Source " + oid + " cannot be classified because his alerts have no LC features");
      }
    }
    
  /** Give {@link ClusterFinder} to current database. Singleton.
    * @return The corresponding {@link ClusterFinder}. 
    * @throws LomikelExceltion If {@link ClusterFinder} cannot be created. */
  private ClusterFinder finder() throws LomikelException {
    if (_finder == null) {
      try {
         ClassLoader classLoader = getClass().getClassLoader();
        _finder = new ClusterFinder(classLoader.getResource(flavor() + "/scaler_params.json"),
                                    classLoader.getResource(flavor() + "/pca_params.json"),
                                    classLoader.getResource(flavor() + "/cluster_centers.json"));
        }
      catch (IOException e) {
        throw new LomikelException("Cannot create Cluster Finder", e);
        }
      }
    return _finder;
    }
  
  private ClusterFinder _finder;

  /** Logging . */
  private static Logger log = LogManager.getLogger(FeaturesClassifier.class);
  
  }
           
           
