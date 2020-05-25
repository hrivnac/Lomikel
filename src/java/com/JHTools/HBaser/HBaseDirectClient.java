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
    
 /** Selftest.
   * @throws IOException If anything goes wrong. */
 public static void main(String[] args) throws IOException {
   //String zookeepers = "localhost";
   //String clientPort = "2181";
   String zookeepers = "134.158.74.54";
   String clientPort = "24444";
   HBaseDirectClient client = new HBaseDirectClient(zookeepers, clientPort);
   System.out.println(client.connect("janusgraph"));
   }
   
 /** Create.
   * @param zookeepers The coma-separated list of zookeper ids.
   * @param clientPort The client port. 
   * @throws IOException If anything goes wrong. */
 public HBaseDirectClient(String zookeepers,
                          String clientPort) throws IOException {
   _conf = HBaseConfiguration.create();
   _conf.set("hbase.zookeeper.quorum", zookeepers);
   _conf.set("hbase.zookeeper.property.clientPort", clientPort);
   _connection = ConnectionFactory.createConnection(_conf); 
   }
   
 /** Create local.
   * @throws IOException If anything goes wrong. */
 public HBaseDirectClient() throws IOException {
   Configuration conf = HBaseConfiguration.create();
   _connection = ConnectionFactory.createConnection(_conf); 
   }
    
 /** Connect the table.
   * @param tableName  The table name.
   * @return           The assigned id. 
   * @throws IOException If anything goes wrong. */
  public Table connect(String tableName) throws IOException {
    return connect(tableName, 0);
    }
    
 /** Connect the table.
   * @param tableName  The table name.
   * @param timeout    The timeout in ms (may be <tt>0</tt>).
   * @return           The assigned id.
   * @throws IOException If anything goes wrong. */
  public Table connect(String tableName,
                       int    timeout) throws IOException {
    _tableName = tableName;
    if (timeout > 0) {
      String tout = String.valueOf(timeout);
      _conf.set("hbase.rpc.timeout",                   tout);
      _conf.set("hbase.client.scanner.timeout.period", tout);
      }
    _table = _connection.getTable(TableName.valueOf(_tableName));
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

  /** Give {@link Table}.
    * @return The {@link Table}. */
  public Table table() {
    return _table;
    }

  private Table _table;
  
  private Configuration _conf;
  
  private Connection _connection;
  
  private static String _tableName;

  /** Logging . */
  private static Logger log = Logger.getLogger(HBaseDirectClient.class);

  }
