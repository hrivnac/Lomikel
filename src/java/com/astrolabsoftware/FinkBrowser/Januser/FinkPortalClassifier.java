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
import java.util.List;
import java.util.ArrayList;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>FinkPortalClassifier</code> classifies sources according to
  * <a href="https://api.ztf.fink-portal.org/api">Fink Portal</a> REST service.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class FinkPortalClassifier extends ZTFClassifier {
  
  @Override
  public void classify(FinkGremlinRecipies recipies,
                       String              oid) throws LomikelException {
    JSONArray ja;
    JSONObject jo;
    Map<String, Set<String>> allInstances; // cl -> [jd]
    Map<String, Double>      allWeights;   // jd -> w
    String cl;
    String jd;
    Set<String> jds;
    String key;
    ja = FPC.objects(new JSONObject().put("objectId",      oid   ).
                                      put("output-format", "json"));
    allInstances = new TreeMap<>();
    allWeights   = new TreeMap<>();
    // get all alerts (jd) and their classes
    for (int i = 0; i < ja.length(); i++) {
      jo = ja.getJSONObject(i);
      cl = jo.getString("v:classification");
      jd = String.valueOf(jo.getDouble("i:jd"));
      if (!cl.equals("Unknown")) {
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
      }
    // rearrange instances and weights and register
    double weight;
    double totalWeight;
    double w;
    totalWeight = 0;
    List<String> instancesL;
    List<Double> weightsL;
    for (Map.Entry<String, Set<String>> cls : allInstances.entrySet()) {
      for (String instance : cls.getValue()) {
        totalWeight += allWeights.get(instance);
        }
      }
    for (Map.Entry<String, Set<String>> cls : allInstances.entrySet()) {
      key = cls.getKey();
      instancesL = new ArrayList<String>(cls.getValue());
      weightsL   = new ArrayList<Double>();
      w = 0;
      for (String instance : instancesL) {
        weightsL.add(allWeights.get(instance));
        w += allWeights.get(instance);
        }
      weight = w / totalWeight;
      recipies.registerOCol(this, key, oid, weight, instancesL, weightsL);
      }
    }

  /** Logging . */
  private static Logger log = LogManager.getLogger(FinkPortalClassifier.class);
  
  }
           
           
