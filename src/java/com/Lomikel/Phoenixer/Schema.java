package com.Lomikel.Phoenixer;

import com.Lomikel.DB.CellContent;

// HBase
import org.apache.hadoop.hbase.util.Bytes;

// Java
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

// Log4J
import org.apache.log4j.Logger;

/** <code>Schema</code>handles <em>Phoenix</em> types coding/decoding.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Schema {
  
  /** Set overall schema.
    * @param schemaName The name of the schema to set.
    * @param schemaMap  The schema to set. */
  public Schema(String              schemaName,
                Map<String, String> schemaMap) {
    _schemaName = schemaName;
    _schemaMap  = schemaMap;
    }
    
  /** Decode the column value.
    * @param  column       The column to decode.
    * @param  encodedValue The encoded value.
    * @return              The decoded value.
    *                      Binary values are decoded as <tt>*binary*</tt>,
    *                      or showing their MIME-type, when known.
    *                      Unknown types are decoded as strings. */
  public String decode(String column,
                       String encodedValue) {
    return encodedValue;
    }
    
  /** Decode the column value to {@link CellContent}..
    * @param  column       The column to decode.
    * @param  encodedValue The encoded value.
    * @return              The decoded value.
    *                      Unknown types are decoded as strings. */
  public CellContent decode2Content(String column,
                                    String encodedValue) {
    return new CellContent(encodedValue);
    }
    
  /** Encode the column value. Doesn't encode binary values.
    * @param  column       The column to encode.
    * @param  decodedValue The decoded value.
    * @return              The encoded value.
    *                      Unknown types are decoded as strings. */
  public String encode(String column,
                       String decodedValue) {
    return decodedValue;
    }

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
    
  /** Get registered {@link Schema}.
    * @param schemaName The name of the requested {@link Schema}.
    * @return The registered {@link Schema}. */
  public static Schema getSchema(String schemaName) {
    return _schemas.get(schemaName);
    }
    
  /** Register named {@link Schema}.
    * @param schemaName The name of the {@link Schema} to register. 
    * @param schemaName The {@link Schema} to register. */
  public static void addSchema(String              schemaName,
                               Map<String, String> schemaMap) {
    _schemas.put(schemaName, new Schema(schemaName, schemaMap));
    }
    
  @Override
  public String toString() {
    return "Schema " + name() + " = " + _schemaMap;
    }
    
  private String _schemaName;
    
  private Map<String, String> _schemaMap;
  
  private static Map<String, Schema> _schemas = new HashMap<>();

  /** Logging . */
  private static Logger log = Logger.getLogger(Schema.class);

  }
