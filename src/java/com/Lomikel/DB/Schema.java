package com.Lomikel.DB;

import com.Lomikel.DB.CellContent;

// Java
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Log4J
import org.apache.log4j.Logger;

/** <code>Schema</code>handles database schema and types coding/decoding.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public abstract class Schema<T> {
  
  /** Set overall schema.
    * @param schemaName The name of the schema to set.
    * @param schemaMap  The schema to set. */
  public Schema(String              schemaName,
                Map<String, String> schemaMap) {
    _schemaName = schemaName;
    _schemaMap  = schemaMap;
    }
  
  /** Set overall schema.
    * @param schemaName The name of the schema to set.
    * @param reMap      The renaming of attributes.
    * @param schemaMap  The schema to set. */
  public Schema(String              schemaName,
                Map<String, String> schemaMap,
                Map<String, String> reMap) {
    _schemaName = schemaName;
    _schemaMap  = schemaMap;
    _reMap      = reMap;
    }
    
  /** Decode the column value.
    * @param  column       The column to decode.
    * @param  encodedValue The encoded value.
    * @return              The decoded value.
    *                      Binary values are decoded as <tt>*binary*</tt>,
    *                      or showing their MIME-type, when known.
    *                      Unknown types are decoded as strings. */        
  public abstract String decode(String column,
                                T      encodedvalue);
  
  /** Decode the column value to {@link CellContent}..
    * @param  column       The column to decode.
    * @param  encodedValue The encoded value.
    * @return              The decoded value.
    *                      Unknown types are decoded as strings. */
  public abstract CellContent decode2Content(String column,
                                             T      encodedValue);
  
  /** Encode the column value. Doesn't encode binary values.
    * @param  column       The column to encode.
    * @param  decodedValue The decoded value.
    * @return              The encoded value.
    *                      Unknown types are decoded as strings. */
  public abstract T encode(String column,
                           String decodedValue);
    
  /** Give the column type (from {@link Schema}).
    * @param column The column.
    * @return       The column type. */
  public String type(String column) {
    return _schemaMap.get(column);
    }
    
  /** Give the current number of columns.
    * @return The current number of known columns. */
  public int size() {
    if (_schemaMap != null) {
      return _schemaMap.size();
      }
    return 0;
    }
    
  /** Give all column names.
    * @return The {@link Set} of column names. */
  public Set<String> columnNames() {
    return _schemaMap.keySet();
    }
    
  /** Give schema name.
    * @return The schema name.*/
  public String name() {
    return _schemaName;
    }
    
 /** Give schema map.
   * @return The schema map.*/
  protected Map<String, String> map() {
    return _schemaMap;
    }
    
 /** Give renaming map.
   * @return The renaming map.*/
  protected Map<String, String> reMap() {
    return _reMap;
    }
    
  /** Give SQL table creation command for this schema.
    * @param tableName The name of created SQL table.
    * @return          The SQL table creation command for this schema. */
  public String toSQL(String tableName) {
    String sql = "DROP TABLE " + tableName + ";\n";
    sql += "CREATE TABLE " + tableName + " (";
    sql += "ROWKEY VARCHAR NOT NULL PRIMARY KEY,ROWTIME VARCHAR,";
    sql += map().entrySet()
                .stream()
                .map(e -> e.getKey().split(":")[1].toUpperCase() + " " + type2SQL(e.getValue()))
                .collect(Collectors.joining(","));
    sql += ");";
    return sql;
    }
    
  /** Give SQL type of schema type.
    * @param type The schema type name.
    * @return     The correspomnding SQL type. */
  protected String type2SQL(String type) {
    if (type == null) {
      type = "string";
      }
    switch (type) {
      case "float": 
        return "FLOAT";
      case "double": 
        return "DOUBLE";
      case "integer": 
        return "INTEGER";
       case "long": 
        return "BIGINT";
      case "short": 
        return "SMALLINT";
      case "fits": 
        //return "VARBINARY";
      case "fits/image": 
        //return "VARBINARY";
      case "binary": 
        //return "VARBINARY";
      default: // includes "string"
        return "VARCHAR";
      } 
    }
    
  /** Tell, whether the column is a numerical type.
    * @param column The column name.
    * @return        Whether the column is a numerical type. */
  public boolean isNumber(String column) {
    String type = type(column);
    if (type == null) {
      return false;
      }
    switch (type) {
      case "float": 
      case "double": 
      case "integer": 
      case "long": 
      case "short": 
        return true;
      default:
        return false;
      } 
   }
    
  @Override
  public String toString() {
    return "Schema " + name() + " =\n\t" + map() + "\t" + reMap();
    }
   
  private String _schemaName;
    
  private Map<String, String> _schemaMap;
    
  private Map<String, String> _reMap;

  /** Logging . */
  private static Logger log = Logger.getLogger(Schema.class);

  }
