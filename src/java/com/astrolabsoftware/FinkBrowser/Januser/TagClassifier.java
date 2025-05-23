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

/** <code>TagClassifier</code> classifies sources according to
  * user supplied tag.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class TagClassifier implements Classifier {
  
  /** Tag <em>source</em>.
    * @param oid  The <em>source</em> <tt>objectId</tt>.
    * @param tags The comma-separated tags to be attributed.
    *             Each tag can contain a probablity, separated by doublecolon.
    *             If probablities are missing, they will be attributed automatically.
    *             If probablities are specified, they should be specified
    *             for all tags or for all tags except one.
    *             Example: <tt>tag1:0.5,tag2:0.3,tag3</tt>. */
  public void tag(String oid,
                  String tags) {
    if (!tags.contains(",")) {
      _recipies.registerSourcesOfInterest(Classifiers.TAG, tags, oid, 1.0, "", _hbaseUrl, false, null);
      return;
      }
    Map<String, Double> tagMap = new TreeMap<>();
    String[] t;
    for (String tag : tags.split(",")) {
      if (tag.contains(":")) {
        t = tag.trim().split(":");
        if (t[1].trim().equals("")) {
          t[1] = "0";
          }
        }
      else {
        t = new String[]{tag, "0"};
        } 
      tagMap.put(t[0], Double.parseDouble(t[1]));
      }
    // renormalize
    for (Map.Entry<String, Double> entry : tagMap.entrySet()) {
      _recipies.registerSourcesOfInterest(Classifiers.TAG, entry.getKey(), oid, entry.getValue(), "", _hbaseUrl, false, null);
      }
    }
  
  @Override
  public void classify(FinkGremlinRecipies recipies,
                       String              oid,
                       String              hbaseUrl,
                       boolean             enhance,
                       String              columns) throws LomikelException {
    log.warn("Cannot classify automatically, use tag method to classify.");
    }
    
  public void init(FinkGremlinRecipies recipies,
                   String hbaseUrl) {
    _recipies = recipies;
    _hbaseUrl = hbaseUrl;
    }
    
  private FinkGremlinRecipies _recipies;
  
  private String _hbaseUrl;
    
  /** Logging . */
  private static Logger log = LogManager.getLogger(TagClassifier.class);
  
  }
           
           
