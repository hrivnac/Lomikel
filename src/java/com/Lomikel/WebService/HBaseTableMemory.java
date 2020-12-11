package com.Lomikel.WebService;

// Log4J
import org.apache.log4j.Logger;

/** <code>HBaseTableMemory</code> keeps connection properties between invocations.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class HBaseTableMemory {
    
  public void setHBase(String hbase) {
    _hbase = hbase;
    }
    
  public void setHTable(String htable) {
    _htable = htable;
    }
    
  public void setSchema(String schema) {
    _schema = schema;
    }
    
  public void setGroup(String group) {
    _group = group;
    }
   
  public String hbase() {
    return _hbase;
    }
    
  public String htable() {
    return _htable;
    }

  public String schema() {
    return _schema;
    }

  public String group() {
    return _group;
    }
    
  private String _hbase;
  
  private String _htable;
  
  private String _schema;
  
  private String _group;
  
  /** Logging . */
  private static Logger log = Logger.getLogger(HBaseTableMemory.class);

  }
