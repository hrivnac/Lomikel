package com.Lomikel.Livyser;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>LivyCLient</code> is the bridge to the <em>Livy</em> .
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public abstract class LivyClient {
  
  /** Connect to the server.
    * @param url The url of the server. */
  public LivyClient(String url) {
    log.info("Connecting to Livy Server " + url);
    _url = url;
    }

  /** Execute action, try until succeeds, wait for result.
    * @param cmd      The command to send.
    * @param language The command {@link Language}.
    * @param conf     The Sparc configuration (as JSON String).
    * @return         The result as <em>Json</em> string. */
  public abstract String executeAction(String   cmd,
                                       Language language,
                                       String   conf);
  
  /** Execute action, try until succeeds, wait for result.
    * @param Action The {@link Action} to be executed.
    * @return       The result as <em>Json</em> string. */
  public String executeAction(Action action) {
    return executeAction(action.cmd(),
                         action.language(),
                         action.conf());
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
  public abstract String sendJob(String file,
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
                                 String proxyUser);
    
  /** Send job, try until succeeds, wait for result.
    * @param job The {@link Job} to be send.
    * @return    The result as <em>Json</em> string. */
  public String sendJob(Job job) {
    return sendJob(job.file(),
                   job.className(),
                   job.args(),
                   job.driverMemory(),
                   job.driverCores(),
                   job.executorMemory(),
                   job.executorCores(),
                   job.numExecutors(),
                   job.jars(),
                   job.pyFiles(),
                   job.files(),
                   job.archives(),
                   job.queue(),
                   job.name(),
                   job.conf(),
                   job.proxyUser());
    }
    
  /** Give Livy server url.
    * @return The Livy server url. */
  public String url() {
    return _url;
    }
    
  private String _url;

  /** Logging . */
  private static Logger log = LogManager.getLogger(LivyClient.class);

  }
