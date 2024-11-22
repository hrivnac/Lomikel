package com.Lomikel.Livyser;

import com.Lomikel.Utils.SmallHttpClient;
import com.Lomikel.Utils.LomikelException;

// org.json
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

// Java
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.AbstractMap;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>LivyRESTCLient</code> is the bridge to the <em>Livy</em> REST service:
  * <a href="https://livy.incubator.apache.org/docs/latest/rest-api.html">API</a>.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: handle full answer, check for errors
public class LivyRESTClient {
  
  /** Connect to the server.
    * @param url The url of the server. */
  public LivyRESTClient(String url) {
    log.info("Connecting to Livy Server " + url);
    _url = url;
    }
  
  /** Initiate session on the server.
    * <pre>
    * POST /sessions {"kind":*language*}
    * </pre>
    * @param language  The {@link Language} of the {@link Session}.
    * @param conf      The Sparc configuration (as JSON String).
    * @param tries     How many times to try.
    * @param sleep     How many <tt>s</tt> wait between tries.
    * @return The new session number. */
  // TBD: allow closing session
  public int initSession(Language language,
                         String   conf,
                         int      tries,
                         int      sleep) {
    log.info("Creating Session in " + language);
    boolean success = false;
    int i = 0;
    String result = "";
    while (!success && i++ <= tries) {
      try {
        Thread.sleep(1000 * sleep);
        result = SmallHttpClient.postJSON(_url + "/sessions", "{\"kind\":\"" + language.asSpark() + ",\"conf\":\"" + conf + "\"}", null, null);
        success = true;
        }
      catch (LomikelException e) {
        log.debug("Request has failed", e);
        }
      catch (InterruptedException e) {
        break;
        }
      }
    if (success) {
      log.debug("Result:\n" + result.trim());
      return new JSONObject(result).getInt("id");
      }
    else {
      return -1;
      }
    }

  /** Get list of opened sessions.
    * <pre>
    * GET /sessions
    * </pre>
    * @return The {@link List} of {@link Pair}s of open session numbers and languages. */
  public List<Map.Entry<Integer, Language>> getSessions() {
    List<Map.Entry<Integer, Language>> ss = new ArrayList<>();
    String result = "";
    try {
      result = SmallHttpClient.get(_url + "/sessions", null);
      }
    catch (LomikelException e) {
      LomikelException.reportException("Request has failed", e, log);
      return ss;
      }
    try {
      JSONArray sessions = new JSONObject(result).getJSONArray("sessions");
      for (int i = 0; i < sessions.length(); i++) {
        ss.add(new AbstractMap.SimpleEntry<>(sessions.getJSONObject(i).getInt("id"),
                                             Language.fromSpark(sessions.getJSONObject(i).getString("kind"))));
        getStatements(sessions.getJSONObject(i).getInt("id"));
        }
      }
    catch (JSONException e) {
      LomikelException.reportException("Request has failed", e, log);
      return ss;
      }
    return ss;
    }
    
  /** Get list of opened batches.
    * <pre>
    * GET /batches
    * </pre>
    * @return The {@link List} of open batch session numbers. */
  public List<Integer> getBatches() {
    List<Integer> ss = new ArrayList<>();
    String result = "";
    try {
      result = SmallHttpClient.get(_url + "/batches", null);
      }
    catch (LomikelException e) {
      LomikelException.reportException("Request has failed", e, log);
      return ss;
      }
    try {
      JSONArray sessions = new JSONObject(result).getJSONArray("sessions");
      for (int i = 0; i < sessions.length(); i++) {
        ss.add(sessions.getJSONObject(i).getInt("id"));
        }
      }
    catch (JSONException e) {
      LomikelException.reportException("Request has failed", e, log);
      return ss;
      }
    return ss;
    }
   
  /** Get list of opened statements.
    * <pre>
    * GET /sessions/-idSession-/statements
    * </re>
    * @param  idSession The existing session number.
    * @return           The {@link List} of {@link Integer}s of open statement numbers for a session. */
  public List<Integer> getStatements(int idSession) {
    List<Integer> ss = new ArrayList<>();
    String result = "";
    try {
      result = SmallHttpClient.get(_url + "/sessions/" + idSession + "/statements", null);
      }
    catch (LomikelException e) {
      LomikelException.reportException("Request has failed", e, log);
      return ss;
      }
    JSONArray statements = new JSONObject(result).getJSONArray("statements");
    for (int i = 0; i < statements.length(); i++) {
      ss.add(new Integer(statements.getJSONObject(i).getInt("id")));
      }
    return ss;
    }
   
  /** Delete session.
    * DELETE /sessions/-idSession-
    * </re>
    * @param  idSession The existing session number. */
  public void deleteSession(int idSession) {
    try {
      SmallHttpClient.delete(_url + "/sessions/" + idSession, null);
      }
    catch (LomikelException e) {
      LomikelException.reportException("Request has failed", e, log);
      }
    }
     
  /** Send command to the server.
    * <pre>
    * POST /sessions/-idSession-/statements {"code":-code-}
    * </pre>
    * @param idSession The existing session number.
    * @param code      The <em>scala</code> to be run on the server.
    * @param tries     How many times to try.
    * @param sleep     How many <tt>s</tt> wait between tries.
    * @return          The new statement id. */
  public int sendCommand(int    idSession,
                         String code,
                         int    tries,
                         int    sleep) {
    log.info("Sending command '" + code + "'");
    code = code.trim()
               .replaceAll("\n", "\\\\n")
               .replaceAll("\"", "\\\\\"");
    String result = "";
    boolean success = false;
    int i = 0;
    while (!success && i++ <= tries) {
      try {
        Thread.sleep(1000 * sleep);
        result = SmallHttpClient.postJSON(_url + "/sessions/" + idSession + "/statements", "{\"code\":\"" + code + "\"}", null, null);
        success = true;
        }
      catch (LomikelException e) {
        log.debug("Request has failed", e);
        }
      catch (InterruptedException e) {
        break;
        }
      }
    if (success) {
      log.debug("Result:\n" + result.trim());
      return new JSONObject(result).getInt("id");
      }
    else {
      return -1;
      }
    }
    
  /** Send job to the server.
    * <pre>
    * POST /batches {"file":*file*, "className":*classname*}
    * </pre>
    * @param file           The jar or py filename.
    * @param className      The <em>main</em> className
    *                       or <tt>null</tt> for py file.
    * @param args           The Job args, if any. 
    * @param driverMemory   The Job driver memory or <tt>null</tt>. 
    * @param driverCores    The Job driver cores or <tt>0</tt>.  
    * @param executorMemory The Job executor memory or <tt>null</tt>. 
    * @param executorCores  The Job executor cores or <tt>0</tt>. 
    * @param numExecutors   The Job executots or <tt>0</tt>.
    * @param jars           The Job jars or <tt>null</tt>. 
    * @param pyFiles        The Job pyFiles or <tt>null</tt>. 
    * @param files          The Job files or <tt>null</tt>. 
    * @param archives       The Job archives or <tt>null</tt>. 
    * @param queue          The Job queue or <tt>null</tt>.
    * @param name           The Job name or <tt>null</tt>.
    * @param conf           The Job conf or <tt>null</tt>. 
    * @param proxyUser      The Job proxyUser or <tt>null</tt>. 
    * @param tries          How many times to try.
    * @param sleep          How many <tt>s</tt> wait between tries.
    * @return               The new statement id. */
  public int sendJob(String file,
                     String className,
                     String args,
                     String driverMemory,
                     int    driverCores,
                     String executorMemory,
                     int    executorCores,
                     int    numExecutors,
                     String jars,
                     String pyFiles,
                     String files,
                     String archives,
                     String queue,
                     String name,
                     String conf,
                     String proxyUser,
                     int    tries,
                     int    sleep) {
    if (className == null) {
      log.info("Sending job from '" + file + "'");
      }
    else {
      log.info("Sending job '" + className + "' from '" + file + "'");
      }
    String request = "{\"file\":\"" + file + "\"";
    if (className != null) {
      request += ", \"className\":\"" + className + "\"";
      }
    if (args != null) {
      request += ", \"args\":\"" + args + "\"";
      }
    if (driverMemory != null) {
      request += ", \"driverMemory\":\"" + driverMemory + "\"";
      }
    if (driverCores != 0) {
      request += ", \"driverCores\":\"" + driverCores + "\"";
      }
    if (executorMemory != null) {
      request += ", \"executorMemory\":\"" + executorMemory + "\"";
      }
    if (executorCores != 0) {
      request += ", \"executorCores\":\"" + executorCores + "\"";
      }
    if (numExecutors != 0) {
      request += ", \"numExecutors\":\"" + numExecutors + "\"";
      }
    if (jars != null) {
      request += ", \"jars\":\"" + jars + "\"";
      }
    if (pyFiles != null) {
      request += ", \"pyFiles\":\"" + pyFiles + "\"";
      }
    if (files != null) {
      request += ", \"files\":\"" + files + "\"";
      }
    if (archives != null) {
      request += ", \"archives\":\"" + archives + "\"";
      }
    if (queue != null) {
      request += ", \"queue\":\"" + queue + "\"";
      }
    if (name != null) {
      request += ", \"name\":\"" + name + "\"";
      }
    if (conf != null) {
      request += ", \"conf\":\"" + conf + "\"";
      }   
    if (proxyUser != null) {
      request += ", \"proxyUser\":\"" + proxyUser + "\"";
      }
    request += "\"}";
    String result = "";
    boolean success = false;
    int i = 0;
    while (!success && i++ <= tries) {
      try {
        Thread.sleep(1000 * sleep);
        result = SmallHttpClient.postJSON(_url + "/batches", request, null, null);
        //if (className == null) {
        //  result = SmallHttpClient.postJSON(_url + "/batches", "{\"file\":\"" + file + "\"}", null, null);
        //  }
        //else {
        //  result = SmallHttpClient.postJSON(_url + "/batches", "{\"file\":\"" + file + "\", \"className\":\"" + className + "\"}", null, null);
        //  }
        success = true;
        }
      catch (LomikelException e) {
        log.debug("Request has failed", e);
        }
      catch (InterruptedException e) {
        break;
        }
      }
    if (success) {
      log.debug("Result:\n" + result.trim());
      return new JSONObject(result).getInt("id");
      }
    else {
      return -1;
      }
    }
    
  /** Check command progreess, get results.
    * <pre>
    * GET /sessions/-idSession-/statements/-idStatement-
    * </pre>
    * @param  idSession   The existing session number.
    * @param  idStatement The statement number.
    * @param  tries       How many times to try.
    * @param  sleep       How many <tt>s</tt> wait between tries.
    * @return             The result. */
  public String checkSessionProgress(int idSession,
                                     int idStatement,
                                     int tries,
                                     int sleep) {
    String result = "";
    boolean success = false;
    int i = 0;
    while (!success && i++ <= tries) {
      try {
        Thread.sleep(1000 * sleep);
        result = SmallHttpClient.get(_url + "/sessions/" + idSession + "/statements/" + idStatement, null);
        success = true;
        }
      catch (LomikelException e) {
        log.debug("Request has failed", e);
        }
      catch (InterruptedException e) {
        break;
        }
      }
    if (success) {
      log.debug("Result:\n" + result.trim());
      return result;
      }
    else {
      return null;
      }
    }
    
  /** Check batch progress, get results.
    * <pre>
    * GET /batches/-idBatch-
    * </pre>
    * @param  idBatch     The existing batch session number.
    * @param  tries       How many times to try.
    * @param  sleep       How many <tt>s</tt> wait between tries.
    * @return             The result. */
  public String checkBatchProgress(int idBatch,
                                   int tries,
                                   int sleep) {
    String resultString = "";
    JSONObject result = null;
    boolean success = false;
    int i = 0;
    while (!success && i++ <= tries) {
      try {
        Thread.sleep(1000 * sleep);
        resultString = SmallHttpClient.get(_url + "/batches/" + idBatch, null);
        result = new JSONObject(resultString);
        success = true;
        }
      catch (LomikelException e) {
        log.debug("Request has failed", e);
        }
      catch (InterruptedException e) {
        break;
        }
      }
    if (success && result != null) {
      log.debug("Result:\n" + result.toString(2));
      return result.toString(2);
      }
    else {
      return null;
      }
    }
    
  /** Get batch log.
    * <pre>
    * GET /batches/-idBatch-/log
    * </pre>
    * @param  idBatch     The existing batch session number.
    * @param  tries       How many times to try.
    * @param  sleep       How many <tt>s</tt> wait between tries.
    * @return             The result. */
  public String getBatchLog(int idBatch,
                            int tries,
                            int sleep) {
    String resultString = "";
    JSONObject result = null;
    boolean success = false;
    int i = 0;
    while (!success && i++ <= tries) {
      try {
        Thread.sleep(1000 * sleep);
        resultString = SmallHttpClient.get(_url + "/batches/" + idBatch + "/log", null);
        result = new JSONObject(resultString);
        success = true;
        }
      catch (LomikelException e) {
        log.debug("Request has failed", e);
        }
      catch (InterruptedException e) {
        break;
        }
      }
    if (success && result != null) {
      log.debug("Result:\n" + result.toString(2));
      return result.toString(2);
      }
    else {
      return null;
      }
    }
    
  /** Delete batch.
    * <pre>
    * DELETE /batches/-idBatch-
    * </pre>
    * @param  idBatch The existing batch session number. */
  public void deleteBatch(int idBatch) {
    try {
      SmallHttpClient.delete(_url + "/batches/" + idBatch, null);
        }
    catch (LomikelException e) {
      log.debug("Request has failed", e);
      }
    }
    
  /** Wait for the job to deliver result.
    * @param  idSession   The existing session number.
    * @param  idStatement The statement number.
    * @param  tries       How many times to try.
    * @param  sleep       How many <tt>s</tt> wait between requests.
    * @return             The result as <em>Json</em> string. */
  public String waitForActionResult(int idSession,
                                    int idStatement,
                                    int tries,
                                    int sleep) {
    log.info("Waiting for Action result");
    String resultString = "";  
    JSONObject result = null;
    double progress;
    boolean success = false;
    while (true) {
      try {
        resultString = checkSessionProgress(idSession, idStatement, tries, sleep);
        result = new JSONObject(resultString);
        progress = result.getDouble("progress");
        log.debug("Progress = " + progress);
        if (progress == 1.0) {
          success = true;
          break;
          }
        Thread.sleep(1000 * sleep);
        }
      catch (InterruptedException e) {
        break;
        }          
      }
    if (success && result != null) {
      log.debug("Result:\n" + result.toString(2));
      return result.toString(2);
      }
    else {
      return null;
      }
    }
     
  /** Wait for the job to deliver result.
    * @param  idBatch     The existing batch session number.
    * @param  tries       How many times to try.
    * @param  sleep       How many <tt>s</tt> wait between requests.
    * @return             The result as <em>Json</em> string. */
  public String waitForJobResult(int idBatch,
                                 int tries,
                                 int sleep) {
    log.info("Waiting for Job result");
    String resultString = "";  
    JSONObject result = null;
    String state;
    boolean success = false;
    while (true) {
      try {
        resultString = checkBatchProgress(idBatch, tries, sleep);
        result = new JSONObject(resultString);
        state = result.getString("state");
        log.debug("State = " + state);
        if (state.equals("success") || state.equals("dead")) { // TBD: handle failure
          success = true;
          break;
          }
        Thread.sleep(1000 + sleep);
        }
      catch (InterruptedException e) {
        break;
        }          
      }
    if (success && result != null) {
      log.debug("Result:\n" + result.toString(2));
      return result.toString(2);
      }
    else {
      return null;
      }
    }
  
  /** Execute action, try until succeeds, wait for result.
    * @param cmd      The command to send.
    * @param language The command {@link Language}.
    * @param conf     The Sparc configuration (as JSON String).
    * @return         The result as <em>Json</em> string. */
  public String executeAction(String   cmd,
                              Language language,
                              String   conf) {
    log.info("Executing command '" + cmd + "' in " + language + " and waiting for result");
    int sessionId   = initSession(language,  conf, Integer.MAX_VALUE, 1);
    int statementId = sendCommand(sessionId, cmd,  Integer.MAX_VALUE, 1);
    return waitForActionResult(sessionId, statementId,  Integer.MAX_VALUE, 1);
    }
    
  /** Send job, try until succeeds, wait for result.
    * @param file           The jar filename.
    * @param className      The <em>main</em> className.
    * @param args           The Job args, if any. 
    * @param driverMemory   The Job driver memory or <tt>null</tt>. 
    * @param driverCores    The Job driver cores or <tt>0</tt>.  
    * @param executorMemory The Job executor memory or <tt>null</tt>. 
    * @param executorCores  The Job executor cores or <tt>0</tt>. 
    * @param numExecutors   The Job executots or <tt>0</tt>.
    * @param jars           The Job jars or <tt>null</tt>. 
    * @param pyFiles        The Job pyFiles or <tt>null</tt>. 
    * @param files          The Job files or <tt>null</tt>. 
    * @param archives       The Job archives or <tt>null</tt>. 
    * @param queue          The Job queue or <tt>null</tt>.
    * @param name           The Job name or <tt>null</tt>.
    * @param conf           The Job conf or <tt>null</tt>. 
    * @param proxyUser      The Job proxyUser or <tt>null</tt>. 
    * @return               The result as <em>Json</em> string. */
  public String sendJob(String file,
                        String className,
                        String args,
                        String driverMemory,
                        int    driverCores,
                        String executorMemory,
                        int    executorCores,
                        int    numExecutors,
                        String jars,
                        String pyFiles,
                        String files,
                        String archives,
                        String queue,
                        String name,
                        String conf,
                        String proxyUser) {
    log.info("Sending '" + className + "' in " + file + " and waiting for result");
    int batchId = sendJob(file,
                          className,
                          args,
                          driverMemory,
                          driverCores,
                          executorMemory,
                          executorCores,
                          numExecutors,
                          jars,
                          pyFiles,
                          files,
                          archives,
                          queue,
                          name,
                          conf,
                          proxyUser,
                          Integer.MAX_VALUE,
                          1);
    return waitForJobResult(batchId, Integer.MAX_VALUE, 1) + "\n\n" + 
           getBatchLog(     batchId, Integer.MAX_VALUE, 1);
    }
    
  /** Give Livy server url.
    * @return The Livy server url. */
  public String url() {
    return _url;
    }
    
  @Override
  public String toString() {
    return "LivyRESTClient(" + _url + ")";
    }
    
  private String _url;

  /** Logging . */
  private static Logger log = LogManager.getLogger(LivyRESTClient.class);

  }
