package com.JHTools.HBaser;

// HBase
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName ;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Table;

// Hadoop
import org.apache.hadoop.conf.Configuration;

// Java
import java.io.IOException;

// Log4J
import org.apache.log4j.Logger;

/** <code>HBaseDirectClient</code> handles HBase connection. 
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class HBaseDirectClient {
    
 /** Connect the table.
   * @param tableName The table name.
   * @return          The assigned id. */
  public Table connect(String tableName) throws IOException {
    _tableName = tableName;
    Configuration conf = HBaseConfiguration.create();
    _connection = ConnectionFactory.createConnection(conf);
    _table = _connection.getTable(TableName.valueOf(_tableName));
    return _table;
    }
    
 /** Connect the table.
   * Set custom timeoute.
   * @param tableName The table name.
   * @param timeout   The timeout in ms.
   * @return          The assigned id. */
  public Table connect(String tableName,
                        int    timeout) throws IOException {
    _tableName = tableName;
    Configuration conf = HBaseConfiguration.create();
    Connection connection = ConnectionFactory.createConnection(conf);
    String tout = String.valueOf(timeout);
    conf.set("hbase.rpc.timeout",                   tout);
    conf.set("hbase.client.scanner.timeout.period", tout);
    _table = connection.getTable(TableName.valueOf(_tableName));
    return _table;
    }

  @Override
  protected void finalize() throws Throwable {
    close();
    }
    
  /** Close and release resources. */
  public void close() {
    log.debug("Closing");
    try {
      _table.close();
      _connection.close();
      }
    catch (IOException e) {
      log.warn("Cannot close Table", e);
      }
    _table = null;
    }               
    
  /** Check, if user is allowed to modify the table.
    * Only very naive check is made, based on table id and user name.
    * @return     Whether the user name is compatible with table name. */
  // TBD: refactor with Catalog
  public boolean writable() {
    String juser = System.getProperty("user.name");
    String huser = _tableName.substring(_tableName.indexOf(".") + 1, _tableName.lastIndexOf("."));
    return juser.equals(huser) || juser.equals("tomcat");
    }  

  /** Give {@link Table}.
    * @return The {@link Table}. */
  public Table table() {
    return _table;
    }

  private Table _table;
  
  private Connection _connection;
  
  private static String _tableName;

  /** Logging . */
  private static Logger log = Logger.getLogger(HBaseDirectClient.class);

  }
