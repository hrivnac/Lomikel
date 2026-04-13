package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.HBaser.HBaseClient;
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

/** <code>FinkClassifier</code> classifies sources using Fink according to
  * <a href="https://api.ztf.fink-portal.org/api">Fink Portal</a> REST service.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class FinkLSSTClassifier extends LSSTClassifier {
  
  @Override
  public void classify(FinkGremlinRecipies recipies,
                       String              oid) throws LomikelException {
    // get all alerts (mjd) and their classes (cl)
    Map<String, Map<String, String>> results;
    Map<String, Set<String>> allInstances; // cl -> [mjd]
    Map<String, Double>      allWeights;   // mjd -> w
    String cl;
    String mjd;
    Set<String> jds;
    allInstances = new TreeMap<>();
    allWeights   = new TreeMap<>();
    for (Map.Entry<String, HBaseClient> client : CLIENTS.entrySet()) {
      results = client.getValue().scan(null,
                                       "key:key:" + oid + ":substring",
                                       "r:midpointMjdTai",
                                       0,
                                       false,
                                       false);
      if (results.size() > 0) {
        cl = client.getKey();
        for (Map.Entry<String, Map<String, String>> result : results.entrySet()) {
          mjd = result.getValue().get("r:midpointMjdTai");
          if (allInstances.containsKey(cl)) {
            jds = allInstances.get(cl);
            jds.add(mjd);
            }
          else {
            jds = new TreeSet<String>();
            jds.add(mjd);
            allInstances.put(cl, jds);
            }
          allWeights.put(mjd, 1.0);          
          }
        }
      }
    // rearrange instances and weights and register
    String key;
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
      //log.info(key + " " + oid + " " + weight + " " + instancesL + " " + weightsL);
      recipies.registerOCol(this, key, oid, weight, instancesL, weightsL);
      }
    }
    
  /** Initialise HBase connections. */
  public static init() {
    CLIENTS = new TreeMap<String, HBaseClient>();
    HBaseClient client;
    for (String cls : CLASSES) {
      try {
        client = new HBaseClient("cchbase1.in2p3.fr", 2183);
        client.connect(cls, "schema_4.1_8.39.0");
        CLIENTS.put(cls, client);
        }
      catch (LomikelException e) {
        log.error("Cannot connect to " + cls + " table");
        }
      }
    }  
  
  private static Map<String, HBaseClient> CLIENTS;

  private static Set<String> CLASSES = Set.of("rubin.tag_early_snia_candidate",
                                              "rubin.tag_extragalactic_lt20mag_candidate",
                                              "rubin.tag_extragalactic_new_candidate",
                                              "rubin.tag_good_quality",
                                              "rubin.tag_hostless_candidate",
                                              "rubin.tag_in_tns",
                                              "rubin.tag_sn_near_galaxy_candidate");
   
  /** Logging . */
  private static Logger log = LogManager.getLogger(FinkLSSTClassifier.class);
  
  }
           
           
