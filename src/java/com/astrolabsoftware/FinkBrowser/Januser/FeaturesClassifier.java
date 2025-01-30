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
// BUG: jd should be String or long
public class FeaturesClassifier implements Classifier {
  
  @Override
  public void classify(FinkGremlinRecipies recipies,
                       String              oid,
                       String              hbaseUrl,
                       boolean             enhance,
                       String              columns) throws LomikelException {
    double jd;
    String cl;
    Map<String, String> value;
    String[] featuresS;
    double[] featuresD;
    Map<String, Set<Double>> classes; // cl -> [jd]
    Map<String, Double>      totals;  // cl -> total weight
    Set<Double> jds;
    String key;
    Set<Double> val;
    double weight;
    Map<String, Map<String, String>> alerts = client(hbaseUrl).scan(null,
                                                                    "key:key:" + oid + ":prefix",
                                                                    "i:jd,d:lc_features_g,d:lc_features_r",
                                                                    0,
                                                                    0,
                                                                    false,
                                                                    false);
    classes = new TreeMap<>();
    totals  = new TreeMap<>();
    // get all alerts (jd) and their features (classses)
    for (Map.Entry<String, Map<String, String>> entry : alerts.entrySet()) {
      value = entry.getValue();
      jd = Double.parseDouble(value.get("i:jd"));
      for (String c : new String[]{"r", "g"}) {
        if (value.containsKey("d:lc_features_" + c)) {
          featuresS = value.get("d:lc_features_" + c).
                            replaceFirst("\\[", "").
                            replaceAll("]$", "").
                            replaceAll("null", "0.0").
                            split(",");
          featuresD = Arrays.stream(featuresS).
                             mapToDouble(Double::parseDouble).
                             toArray();
          for (int i = 0; i < 26; i++) {
            if (!Double.isNaN(featuresD[i])) {
              cl = c + i;
              if (classes.containsKey(cl)) {
                jds = classes.get(cl);
                jds.add(jd);
                totals.put(cl, totals.get(cl) + featuresD[i]);
                }
              else {
                jds = new TreeSet<Double>();
                jds.add(jd);
                classes.put(cl, jds);
                totals.put(cl, featuresD[i]);
                }
              }
            }    
          }
        }
      }
    for (Map.Entry<String, Set<Double>> cls : classes.entrySet()) {
      key = cls.getKey();
      val = cls.getValue();
      weight = totals.get(key) / val.size();
      log.info("\t" + key + " = " + weight + " for alerts");
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
           
           
