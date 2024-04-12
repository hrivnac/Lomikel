package com.Lomikel.WebService;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>Profile</code> handles bootstrap profile.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Profile {
    
  public void setProfile(String profile) {
    _profile = profile;
    }
    
  public String profile() {
    return _profile;
    }
    
  private String _profile;
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(Profile.class);

  }
