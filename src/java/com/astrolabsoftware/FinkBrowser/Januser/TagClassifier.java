package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.Utils.LomikelException;

// org.json

// Java
import java.util.Map;
import java.util.TreeMap;

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
public class TagClassifier extends Classifier {
  
  /** Tag <em>source</em>.
    * @param oid  The <em>source</em> <tt>objectId</tt>.
    * @param tags The comma-separated tags to be attributed.
    *             Each tag should contain a weight, separated by doublecolon.
    *             Example: <tt>tag1:0.5,tag2:0.3,tag3:0.2</tt>. */
  public void tag(String oid,
                  String tags) {
    if (!tags.contains(",")) {
      _recipies.registerSoI(this, tags, oid, 1.0, "", "");
      return;
      }
    Map<String, Double> tagMap = new TreeMap<>();
    String[] t =  new String[]{};
    for (String tag : tags.split(",")) {
      if (tag.contains(":")) {
        t = tag.trim().split(":");
        if (t[1].trim().equals("")) {
          log.warn("Tag " + t[0] + " without weight, will be ignored");
          }
        }
      else {
        log.warn("Tag " + t + " without weight, will be ignored");
        } 
      tagMap.put(t[0], Double.parseDouble(t[1]));
      }
    // renormalize
    for (Map.Entry<String, Double> entry : tagMap.entrySet()) {
      _recipies.registerSoI(this, entry.getKey(), oid, entry.getValue(), "", "");
      }
    }
  
  @Override
  public void classify(FinkGremlinRecipies recipies,
                       String              oid) throws LomikelException {
    log.warn("Cannot classify automatically, use tag method to classify.");
    }
    
  /** TBD */
  public void init(FinkGremlinRecipies recipies) {
    _recipies = recipies;
    }
    
  private FinkGremlinRecipies _recipies;
    
  /** Logging . */
  private static Logger log = LogManager.getLogger(TagClassifier.class);
  
  }
           
           
