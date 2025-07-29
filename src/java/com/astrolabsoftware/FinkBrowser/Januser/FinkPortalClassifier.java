package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.Utils.LomikelException;
import com.astrolabsoftware.FinkBrowser.FinkPortalClient.FPC;

// org.json
import org.json.JSONArray;
import org.json.JSONObject;

// Java
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;
import java.util.TreeMap;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>FinkPortalClassifier</code> classifies sources according to
  * <a href="https://api.fink-portal.org/api">Fink Portal</a> REST service.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class FinkPortalClassifier extends Classifier {
  
  @Override
  public void classify(FinkGremlinRecipies recipies,
                       String              oid,
                       boolean             enhance,
                       String              columns) throws LomikelException {
    JSONArray ja;
    JSONObject jo;
    Map<String, Set<Double>> classes; // cl -> [jd]
    String cl;
    double jd;
    Set<Double> jds;
    String key;
    Set<Double> val;
    double weight;
    double totalWeight;
    ja = FPC.objects(new JSONObject().put("objectId",      oid   ).
                                      put("output-format", "json"));
    classes =  new TreeMap<>();
    // get all alerts (jd) and their classes
    for (int i = 0; i < ja.length(); i++) {
      jo = ja.getJSONObject(i);
      cl = jo.getString("v:classification");
      jd = jo.getDouble("i:jd");
      if (!cl.equals("Unknown")) {
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
    totalWeight = 0;
    for (Map.Entry<String, Set<Double>> cls : classes.entrySet()) {
      totalWeight += cls.getValue().size();
      }
    for (Map.Entry<String, Set<Double>> cls : classes.entrySet()) {
      key = cls.getKey();
      val = cls.getValue();
      weight = val.size() / totalWeight;
      recipies.registerSoI(Classifiers.FINK_PORTAL, key, oid, weight, val, enhance, columns);
      }
    }

  /** Logging . */
  private static Logger log = LogManager.getLogger(FinkPortalClassifier.class);
  
  }
           
           
