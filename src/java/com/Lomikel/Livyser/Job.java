package com.Lomikel.Livyser;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** Job represents a program to be send to a Spark Server,
  * formulated as a JAR or PY file.
  * It can be written in Python or Scala.
  * You can send it to a Batch opened on a Spark Server.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Job extends Element {  
   
  /** Create new Job.
    * @param jobName           The Job name.
    * @param file           The jar or py filename.
    * @param className      The <em>main</em> className for jar file,
    *                       <tt>null</tt> for py file.
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
    * @param conf           The Job conf or <tt>null</tt>. 
    * @param proxyUser      The Job proxyUser or <tt>null</tt>. */
  public Job(String jobName,
             String file,
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
             String conf,
             String proxyUser) {
    super(jobName);
    _file = file;
    _className = className;
    if (args != null) {
      _args = args;
      }
    if (driverMemory != null) {
      _driverMemory = driverMemory;
      }
    if (executorMemory != null) {
      _executorMemory = executorMemory;
      }
    if (executorCores != 0) {
      _executorCores = executorCores;
      }
    if (numExecutors != 0) {
      _numExecutors = numExecutors;
      }
    if (jars != null) {
      _jars = jars;
      }
    if (pyFiles != null) {
      _pyFiles = pyFiles;
      }
    if (files != null) {
      _files = files;
      }
    if (archives != null) {
      _archives = archives;
      }
    if (queue != null) {
      _queue = queue;
      }
    if (conf != null) {
      _conf = conf;
      }
    if (proxyUser != null) {
      _proxyUser = proxyUser;
      }
    }

  /** Give the Job jar or py filename.
    * @return The Job or py jar filename. */
  public String file() {
    return _file;
    }
    
  /** Give the Job <em>main</em> className or <tt>null</tt> for py file.
    * @return The <em>main</em> className or <tt>null</tt> for py file. */
  public String className() {
    return _className;
    }            

  /** Give the Job args.
    * @return The Job args, if any. */
  public String args() {
    return _args;
    }
    
  /** Give the Job jars.
    * @return The Job jars, if any. */
  public String jars() {
    return _jars;
    }
    
  /** Give the Job pyFiles.
    * @return The Job pyFiles, if any. */
  public String pyFiles() {
    return _pyFiles;
    }
    
  /** Give the Job files.
    * @return The Job files, if any. */
  public String files() {
    return _files;
    }
    
  /** Give the Job archives.
    * @return The Job archives, if any. */
  public String archives() {
    return _archives;
    }
    
  /** Give the Job driver memory.
    * @return The Job driver memory, can be <tt>null</tt>. */
  public String driverMemory() {
    return _driverMemory;
    }
    
  /** Give the Job driver cores.
    * @return The Job driver cores, can be <tt>0</tt>. */
  public int driverCores() {
    return _driverCores;
    }
    
  /** Give the Job executor memory.
    * @return The Job executor memory, can be <tt>null</tt>. */
  public String executorMemory() {
    return _executorMemory;
    }
    
  /** Give the Job executor cores.
    * @return The Job executor cores, can be <tt>0</tt>. */
  public int executorCores() {
    return _executorCores;
    }
     
  /** Give the Job executors.
    * @return The Job executors, can be <tt>0</tt>. */
  public int numExecutors() {
    return _numExecutors;
    }
   
  /** Give the Job queue.
    * @return The Job queue, if any. */
  public String queue() {
    return _queue;
    }
    
  /** Give the Job conf.
    * @return The Job conf, if any. */
  public String conf() {
    return _conf;
    }
    
  /** Give the Job proxyUser.
    * @return The Job proxyUser, if any. */
  public String proxyUser() {
    return _proxyUser;
    }
    
  /** Set as a new Job, so it will be stored on Exit. */
  public void setNew() {
    _new = true;
    }
    
  /** Whether is new, to be stored on Exit.
    * return Whether is new. */
  public boolean isNew() {
    return _new;
    }
    
  @Override
  public String toString() {
    return name() + " (" + _className + " from " + _file + ")";
    }
    
  private String  _file;
  
  private String  _className;
  
  private String  _args = "";
  
  private String  _driverMemory = "1g";
    
  private String  _executorMemory = "1g";

  private int     _driverCores = 3;
  
  private int     _executorCores = 3;
  
  private int     _numExecutors  = 5;
                  
  private String  _jars = "";
                  
  private String  _pyFiles = "";
                  
  private String  _files = "";
                  
  private String  _archives = "";
                  
  private String  _queue = "";
                  
  private String  _conf = "";
                  
  private String  _proxyUser = "";
                  
  private boolean _new = false;
 
  /** Logging . */
  private static Logger log = LogManager.getLogger(Job.class);

  }
