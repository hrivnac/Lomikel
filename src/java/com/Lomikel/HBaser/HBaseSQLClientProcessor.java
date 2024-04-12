package com.Lomikel.HBaser;

import com.Lomikel.DB.Schema;

// SQL
import java.sql.SQLException;
import java.sql.Statement;

// Java
import java.util.List;  
import java.util.ArrayList;  
import java.util.Map;  

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>HBaseSQLClientProcessor</code> implements {@link HBaseProcessor} for Phoenix SQL.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: reuse Phoenixer
public class HBaseSQLClientProcessor implements HBaseProcessor {
    
  /** Create and attach {@link HBaseSQLClient}.
    * @param client The {@link HBaseSQLClient} to be attached. */
  public HBaseSQLClientProcessor(HBaseSQLClient client) {
    _client = client;
    }
                  
  /** Depending on <tt>_upsert</tt>, upsert results into Phoenix SQL table
    * and clean the {@link Map}. */  
  @Override
  public void processResults(Map<String, Map<String, String>> results) {
    if (_client.sqlTableName() != null) {
      results2SQL(results, _client.sqlTableName());
      results.clear();
      }
    }

  /** Results presented as an SQL command.
    * The command can be used for SQL tables created with {@link Schema#toSQL} method.
    * Using the default table name.
    * @param results The {@link Map} of results.
    * @return        The result as a {@link List}. */
  public void results2SQL(Map<String, Map<String, String>> results) {
    results2SQL(results, null);
    }
    
  /** Results presented as an SQL command.
    * The command can be used for SQL tables created with {@link Schema#toSQL} method.
    * @param results      The {@link Map} of results.
    * @param sqlTableName The SQL table name. Empty or <tt>null</tt> will use the default name. */
  public void results2SQL(Map<String, Map<String, String>> results,
                          String                           sqlTableName) {
    String stn = _client.tableName();
    if (sqlTableName != null && !sqlTableName.trim().equals("")) {
      stn = sqlTableName;
      }
    if (_sqlCompatibleSchema == null) {
      _sqlCompatibleSchema = (HBaseSchema)_client.schema().sqlCompatibleSchema();
      }
    String sql;
    List<String> names  = new ArrayList<>();
    List<String> values = new ArrayList<>();
    short n = 0;
    for (Map.Entry<String, Map<String, String>> entry : results.entrySet()) {
      sql = "UPSERT INTO " + stn;
      names.clear();
      values.clear();
      names.add("ROWKEY");
      values.add("'" + entry.getKey() + "'");
      for (Map.Entry<String, String> cell : entry.getValue().entrySet()) {
        if (cell.getKey().equals("key:key")) {
          // already added
          }
        else if (cell.getKey().equals("key:time")) {
          names.add("ROWTIME");
          values.add("'" + cell.getValue() + "'");
          }
        else if (!_sqlCompatibleSchema.contains(cell.getKey())) {
          log.warn("The column " + cell.getKey() + " is not covered by " + _client.schema().name() + ", so ignored.");
          // column not in schema
          }
        else if (cell.getValue().split(":")[0].equals("binary")) {
          names.add(cell.getKey().split(":")[1]);
          values.add("'" + _client.repository().get64(cell.getValue()) + "'");
          }
        else {
          names.add(cell.getKey().split(":")[1]);
          if (_sqlCompatibleSchema.isNumber(cell.getKey())) {
            values.add(cell.getValue());
            }
          else {
            values.add("'" + cell.getValue() + "'");
            }
          }
        }
      sql += " (" + String.join(",", names) + ") VALUES(" + String.join(",", values) + ")";
      upsert(sql);
      n++;
      _n++;
      }
    try {
      _client.conn().commit();
      log.info("" + n + " upserts commited, total = " + _n);
      }
    catch (SQLException e) {
      log.error("Cannot commit", e);
      }
    }

  /** Upsert into connected SQL Phoenix database.
    * @param sql The SQL <tt>upsert</tt> command. */
  private void upsert(String sql) {
    Statement stmt;
    try {
      stmt = _client.conn().createStatement();
      }
    catch (SQLException e) {
      log.error("Cannot create statement", e);
      return;
      }
    try {
      stmt.executeUpdate(sql);
      }
    catch (SQLException e ) {
      log.error("Cannot execute: " + sql, e);
      } 
    }    
    
  private int _n = 0;
  
  private HBaseSchema _sqlCompatibleSchema;
  
  private HBaseSQLClient _client;
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(HBaseSQLClient.class);

  }
