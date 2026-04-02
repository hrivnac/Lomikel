package com.astrolabsoftware.FinkBrowser.FinkPortalClient;

import com.Lomikel.Utils.SmallHttpClient;
import com.Lomikel.Utils.LomikelException;

// org.json
import org.json.JSONArray;
import org.json.JSONObject;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>FPC</code> is a client for <a href="https://api.ztf.fink-portal.org/api">Fink Science Portal</a>.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// BUG: make everything for ZTF/LSST
public class FPC {
  
  /** Create for a survey.
    * @param survey The survey: <em>ZTF</em> or <em>LSST</em>. */
  public FPC(String survey) throws LomikelException {
    switch (survey) {
      case "ZTF":
        _restUrl = "https://api.ztf.fink-portal.org/api/v1";
        break;
      case "LSST":
        _restUrl = "https://api.lsst.fink-portal.org/api/v1";
        break;
      default:
        throw new LomikelException("Unknown Classifier survey " + survey);
      }  
    }
    
  /** Call <em>Fink Science Portal <b>objects</b></em> Web Service.
    * <a href="https://api.ztf.fink-portal.org/api">https://api.ztf.fink-portal.org/api/v1</a>.
    * @param request  The requested formulated as {@link JSONObject}.
    * @param endpoint The service endpoint.
    * @return         The answer formulated as {@link JSONArray}.
    * @throws LomikelException If call fails. */
  public JSONArray objects(JSONObject request) throws LomikelException {
    return call(request, OBJECTS_WS);
    }
     
  /** Call <em>Fink Science Portal <b>latests</b></em> Web Service.
    * <a href="https://api.ztf.fink-portal.org/api/v1/latests">https://api.ztf.fink-portal.org/api/v1/latests</a>.
    * @param request  The requested formulated as {@link JSONObject}.
    * @param endpoint The service endpoint.
    * @return         The answer formulated as {@link JSONArray}.
    * @throws LomikelException If call fails. */
  public JSONArray latests(JSONObject request) throws LomikelException {
    return call(request, LATESTS_WS);
    }
    
  /** Call <em>Fink Science Portal <b>anomaly</b></em> Web Service.
    * <a href="https://api.ztf.fink-portal.org/api/v1/anomaly">https://api.ztf.fink-portal.org/api/v1/anomaly</a>.
    * @param request  The requested formulated as {@link JSONObject}.
    * @param endpoint The service endpoint.
    * @return         The answer formulated as {@link JSONArray}.
    * @throws LomikelException If call fails. */
  public JSONArray anomaly(JSONObject request) throws LomikelException {
    return call(request, ANOMALY_WS);
    }
   
  /** Call <em>Fink Science Portal</em> Web Service.
    * @param request  The requested formulated as {@link JSONObject}.
    * @param endpoint The service endpoint.
    * @return         The answer formulated as {@link JSONArray}.
    * @throws LomikelException If call fails. */
  private JSONArray call(JSONObject request,
                         String     endpoint) throws LomikelException {
     String answer = shc.postJSON(_restUrl + "/" + endpoint,
                                  request.toString(),
                                  null,
                                  null);
     JSONArray ja = new JSONArray(answer);
     return ja;
    }

  public static SmallHttpClient shc = new SmallHttpClient();
  
  private static String OBJECTS_WS = "objects";
  private static String LATESTS_WS = "latests";
  private static String ANOMALY_WS = "anomaly";
  
  private String _restUrl;

  /** Logging . */
  private static Logger log = LogManager.getLogger(FPC.class);
                                                
  }

