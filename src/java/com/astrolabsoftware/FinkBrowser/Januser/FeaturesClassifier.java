package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.HBaser.HBaseClient;
import com.Lomikel.Utils.LomikelException;
import com.astrolabsoftware.FinkBrowser.FinkPortalClient.FPC;

// org.json
import org.json.JSONArray;
import org.json.JSONObject;

// Java
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;
import java.util.TreeMap;

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
public class FeaturesClassifier implements Classifier {
  
  @Override
  public void classify(FinkGremlinRecipies recipies,
                       String              oid,
                       String              hbaseUrl,
                       boolean             enhance,
                       String              columns) throws LomikelException {
    double jd;
    String cl;
    String[] features;
    double[] featuresR;
    double[] featuresG;
    Map<String, Set<Double>> classes; // cl -> [jd]
    Set<Double> jds;
    String key;
    Set<Double> val;
    int weight;
    Map<String, Map<String, String>> alerts = client(hbaseUrl).scan(null,
                                                                    "key:key:" + oid + ":prefix",
                                                                    "i:jd,d:lc_features_g,d:lc_features_r",
                                                                    0,
                                                                    0,
                                                                    false,
                                                                    false);
    classes =  new TreeMap<>();
    // get all alerts (jd) and their features (classses)
    for (Map.Entry<String, Map<String, String>> entry : alerts.entrySet()) {
      jd = Double.parseDouble(entry.getValue().get("i:jd"));
      features = entry.getValue().get("d:lc_features_r").replaceFirst("[", "").replaceAll("]$", "").split(",");
      featuresR = Arrays.stream(features).mapToDouble(Double::parseDouble).toArray();
      features = entry.getValue().get("d:lc_features_g").replaceFirst("[", "").replaceAll("]$", "").split(",");
      featuresG = Arrays.stream(features).mapToDouble(Double::parseDouble).toArray();
      for (int i = 0; i < 26; i++) {
        if (featuresR[i] != Double.NaN) {
          cl = "r" + i;
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
        if (featuresG[i] != Double.NaN) {
          cl = "g" + i;
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
    for (Map.Entry<String, Set<Double>> cls : classes.entrySet()) {
      key = cls.getKey();
      val = cls.getValue();
      weight = val.size();
      log.info("\t" + key + " in " + weight + " alerts: " + val);
      recipies.registerSourcesOfInterest(Classifiers.FEATURES, key, oid, weight, val, hbaseUrl, enhance, columns);
      }
    }
    
  /** TBD */
  private HBaseClient client(String hbaseUrl) throws LomikelException {
    if (_client == null) {
      String[] hbaseUrlA = hbaseUrl.split(":");
      if (hbaseUrlA.length < 4) {
        throw new LomikelException("Cannot create HBase client, hbaseUrl uncomplete: " + hbaseUrl);
        }
      _client = new HBaseClient(hbaseUrlA[0], hbaseUrlA[1]);
      _client.connect(hbaseUrlA[2], hbaseUrlA[3]);
      }
    return _client;
    }
    
  private static HBaseClient _client;

  /** Logging . */
  private static Logger log = LogManager.getLogger(FeaturesClassifier.class);
  
  }
           
           
