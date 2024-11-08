package com.Lomikel.Livyser;

import com.Lomikel.HBaser.HBaseClient;
import com.Lomikel.Utils.LomikelException;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>Server</code> represents <em>Spark</em> server behind <em>Livy</em> proxy.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Server extends Element {
  
  /** Create new Spark and Livy Server.
    * @param name     The Server name.
    * @param urlLivy  The url of the Spark Server Livy interface.
    * @param urlSpark The url of the Spark Server.
    * @param urlHBase The url of the HBase Server. Can be <tt>null</tt>.*/
  public Server(String name,
                String urlLivy,
                String urlSpark,
                String urlHBase) {
    super(name);
    setLivy(urlLivy);
    setSpark(urlSpark);
    if (urlHBase != null) {
      setHBase(urlHBase);
      }
    }
        
  /** Create new Spark and Livy Server.
    * @param name The Server name. */
  public Server(String name) {
    super(name);
    }
    
  /** Set Spark Server Livy interface.
    * @param urlLivy  The url of the Spark Server Livy interface. */
  public void setLivy(String url) {
    _urlLivy = url;
    _livy = new LivyClient(url);
    }

  /** Set Spark Server.
    * @param urlLivy  The url of the Spark Server. */
  public void setSpark(String urlSpark) {
    _urlSpark = urlSpark;
    }
 
  /** Set HBase Server.
    * @param url The url of the HBase Server. */
  public void setHBase(String url) {
    _urlHBase = url;
    try {
      _hbase    = new HBaseClient(url);
      }
    catch (LomikelException e) {
      log.error("Cannot set hBase", e);
      }
    }
    
  /** Give Spark Server Livy interface url.
    * @return The Spark Server Livy interface url. */
  public String urlLivy() {
    return _urlLivy;
    }
        
  /** Give Spark Server url.
    * @return The Spark Server url. */
  public String urlSpark() {
    return _urlSpark;
    }  

  /** Give HBase Server url.
    * @return The HBase Server url. */
  public String urlHBase() {
    return _urlHBase;
    }  
    
  /** Give Hadoop Server url.
    * @return The Hadoop Server url. Can be <tt>null</tt>. */
  public String urlHadoop() {
    return _urlHadoop;
    }  
    
  /** Give Spark History Server url.
    * @return The Spark History Server url. Can be <tt>null</tt>. */
  public String urlSparkHistory() {
    return _urlSparkHistory;
    }  
    
  /** Give Ganglia Server url.
    * @return The Ganglia Server url. Can be <tt>null</tt>. */
  public String urlGanglia() {
    return _urlGanglia;
    }  
   
  /** Give {@link LivyClient}.
    * @return The {@link LivyClient}. */
  public LivyClient livy() {
    return _livy;
    }
    
  /** Give {@link HBaseClient}.
    * @return The {@link HBaseClient}. */
  public HBaseClient hbase() {
    return _hbase;
    }
           
  /** TBD */
  public void setUrlHadoop(String urlHadoop) {
    _urlHadoop = urlHadoop;
    }
    
  /** TBD */
  public void setUrlSparkHistory(String urlSparkHistory) {
    _urlSparkHistory = urlSparkHistory;
    }
    
  /** TBD */
  public void setUrlGanglia(String urlGanglia) {
    _urlGanglia = urlGanglia;
    }
    
  @Override
  public String toString() {
    String s = name() + " (Livy = " + _urlLivy + ", Spark = " + _urlSpark + ", HBase = " + _urlHBase + ")";
    if (_urlHadoop != null) {
      s += "\n(Hadoop = " + _urlHadoop + ")";
      }
    if (_urlGanglia != null) {
      s += "\n(Ganglia = " + _urlGanglia + ")";
      }
    if (_urlSparkHistory != null) {
      s += "\n(SparkHistory = " + _urlSparkHistory + ")";
      }
    return s;
    }
    
  private String _urlLivy;
  
  private String _urlSpark;
  
  private String _urlHBase;
  
  private String _urlHadoop;
  
  private String _urlSparkHistory;
  
  private String _urlGanglia;
  
  private LivyClient _livy;
  
  private HBaseClient _hbase;
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(Server.class);

  }

