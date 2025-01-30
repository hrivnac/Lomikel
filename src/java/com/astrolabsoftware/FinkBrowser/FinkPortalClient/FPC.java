package com.astrolabsoftware.FinkBrowser.FinkPortalClient;

import com.Lomikel.Utils.SmallHttpClient;
import com.Lomikel.Utils.LomikelException;

// org.json
import org.json.JSONArray;
import org.json.JSONObject;

// Java
import java.text.SimpleDateFormat;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>FPC</code> is a client for <a href="https://api.fink-portal.org/api">Fink Science Portal</a>.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class FPC {
    
  /** Call <em>Fink Science Portal <b>objects</b></em> Web Service.
    * <a href="https://api.fink-portal.org/api">https://api.fink-portal.org/api/v1</a>.
    * @param request  The requested formulated as {@link JSONObject}.
    * @param endpoint The service endpoint.
    * @return         The answer formulated as {@link JSONArray}.
    * @throws LomikelException If call fails. */
  public static JSONArray objects(JSONObject request) throws LomikelException {
    return call(request, OBJECTS_WS);
    }
     
  /** Call <em>Fink Science Portal <b>latests</b></em> Web Service.
    * <a href="https://api.fink-portal.org/api/v1/latests">https://api.fink-portal.org/api/v1/latests</a>.
    * @param request  The requested formulated as {@link JSONObject}.
    * @param endpoint The service endpoint.
    * @return         The answer formulated as {@link JSONArray}.
    * @throws LomikelException If call fails. */
  public static JSONArray latests(JSONObject request) throws LomikelException {
    return call(request, LATESTS_WS);
    }
    
  /** Call <em>Fink Science Portal <b>anomaly</b></em> Web Service.
    * <a href="https://api.fink-portal.org/api/v1/anomaly">https://api.fink-portal.org/api/v1/anomaly</a>.
    * @param request  The requested formulated as {@link JSONObject}.
    * @param endpoint The service endpoint.
    * @return         The answer formulated as {@link JSONArray}.
    * @throws LomikelException If call fails. */
  public static JSONArray anomaly(JSONObject request) throws LomikelException {
    return call(request, ANOMALY_WS);
    }
   
  /** Call <em>Fink Science Portal</em> Web Service.
    * @param request  The requested formulated as {@link JSONObject}.
    * @param endpoint The service endpoint.
    * @return         The answer formulated as {@link JSONArray}.
    * @throws LomikelException If call fails. */
  private static JSONArray call(JSONObject request,
                                String     endpoint) throws LomikelException {
     String answer = shc.postJSON(FINK_SCIENCE_PORTAL + "/" + endpoint,
                                  request.toString(),
                                  null,
                                  null);
     JSONArray ja = new JSONArray(answer);
     return ja;
    }

  public static SmallHttpClient shc = new SmallHttpClient();
  
  private static String FINK_SCIENCE_PORTAL = "https://api.fink-portal.org/api/v1";
  private static String OBJECTS_WS = "objects";
  private static String LATESTS_WS = "latests";
  private static String ANOMALY_WS = "anomaly";
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(FPC.class);
                                                
  }

