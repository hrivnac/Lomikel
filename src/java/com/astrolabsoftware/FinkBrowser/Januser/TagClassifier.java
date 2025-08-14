package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.Utils.LomikelException;

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
  
  @Override
  public void classify(FinkGremlinRecipies recipies,
                       String              oid) throws LomikelException {
    log.warn("Cannot classify automatically, use tag method to classify.");
    }

  /** Tag <em>source</em>.
    * @param oid   The <em>source</em> <tt>objectId</tt>.
    * @param tag   The tag to be assigned. It should be already registered as a valid tag.
    * @param value The tag value. */
  public void classify(FinkGremlinRecipies recipies,
                       String              oid,
                       String              tag,
                       double              value) throws LomikelException {
    if (!recipies.g().V().has("lbl",    "SoI").
                          has("name",   name()).
                          has("flavor", flavor()).
                          has("cls",    tag).
                          hasNext()) {
      log.error("SoI " + tag + " of " + name() + "[" + flavor() + "] does not exist, create it first");
      return;
      }
    recipies.registerSoI(this, tag, oid, value, "", "");
    }
    
  public void createTag(FinkGremlinRecipies recipies,
                        String              tag) {
    if (recipies.g().V().has("lbl",    "SoI").
                         has("name",   name()).
                         has("flavor", flavor()).
                         has("cls",    tag).
                         hasNext()) {
      log.error("SoI " + tag + " of " + name() + "[" + flavor() + "] already exists");
      }
    else {
      recipies.g().addV("SoI").property("lbl",    "SoI").
                               property("name",   name()).
                               property("flavor", flavor()).
                               property("cls",    tag).
                               iterate();
      recipies.commit();
      log.info("SoI " + tag + " of " + name() + "[" + flavor() + "] created");
      }
    }
    
  /** Logging . */
  private static Logger log = LogManager.getLogger(TagClassifier.class);
  
  }
           
           
