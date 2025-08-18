package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.Utils.LomikelException;

// Java
import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;

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
    * @param oid        The <em>source</em> <tt>objectId</tt>.
    * @param tag        The tag to be assigned. It should be already registered as a valid tag.
    * @param value      The tag value. */
  public void classify(FinkGremlinRecipies recipies,
                       String              oid,
                       String              tag,
                       double              value) throws LomikelException {
    classify(recipies, oid, tag, value, null, null, null);
    }

  /** Tag <em>source</em>.
    * @param oid        The <em>source</em> <tt>objectId</tt>.
    * @param tag        The tag to be assigned. It should be already registered as a valid tag.
    * @param value      The tag value.
    * @param author     The tag author. May be <tt>null</tt>.
    * @param annotation The tag annotation. May be <tt>null</tt>.
    * @param ref        The tag reference (URL). May be <tt>null</tt>.*/
  public void classify(FinkGremlinRecipies recipies,
                       String              oid,
                       String              tag,
                       double              value,
                       String              author,
                       String              annotation,
                       String              ref) throws LomikelException {
    if (!recipies.g().V().has("lbl",        "SoI").
                          has("classifier", name()).
                          has("flavor",     flavor()).
                          has("cls",        tag).
                          hasNext()) {
      log.error("SoI " + tag + " of " + name() + "[" + flavor() + "] does not exist, create it first");
      return;
      }
    Map<String, String> attributes = new HashMap<>();
    attributes.put("weight", "" + value);
    if (author != null) {
      attributes.put("author", author);
      }
    if (annotation != null) {
      attributes.put("annotation", annotation);
      }
    if (ref != null) {
      attributes.put("ref", ref);
      }
    recipies.registerSoI(this, tag, oid, attributes, false);
    }
    
  public void createTag(FinkGremlinRecipies recipies,
                        String              tag) {
    if (recipies.g().V().has("lbl",          "SoI").
                         has("classifier",   name()).
                         has("flavor",       flavor()).
                         has("cls",          tag).
                         hasNext()) {
      log.error("SoI " + tag + " of " + name() + "[" + flavor() + "] already exists");
      }
    else {
      recipies.g().addV("SoI").property("lbl",        "SoI").
                               property("classifier", name()).
                               property("flavor",     flavor()).
                               property("cls",        tag).
                               iterate();
      recipies.commit();
      log.info("SoI " + tag + " of " + name() + "[" + flavor() + "] created");
      }
    }
    
  /** Logging . */
  private static Logger log = LogManager.getLogger(TagClassifier.class);
  
  }
           
           
