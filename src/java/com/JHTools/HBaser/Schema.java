package com.JHTools.HBaser;

import com.JHTools.Utils.Coding;
import com.JHTools.Utils.Gzipper;

// org.json
import org.json.JSONObject;
import org.json.JSONArray;

// HBase
import org.apache.hadoop.hbase.util.Bytes;

// Java
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

// Log4J
import org.apache.log4j.Logger;

/** <code>Schema</code>handles <em>HBase</em> types coding/decoding.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Schema {
  
  /** Set overall schema.
    * @param schemaMap The schema to set. */
  // TBD: handle schema per version
  public Schema(Map<String, String> schemaMap) {
    _schemaMap = schemaMap;
    }
    
  /** Decode the column value.
    * @param  column       The column to decode.
    * @param  encodedValue The encoded value.
    * @return              The decoded value.
    *                      Binary values are decoded as <tt>*binary*</tt>,
    *                      or showing their MIME-type, when known.
    *                      Unknown types are decoded as strings. */
  public String decode(String column,
                       byte[] encodedValue) {
    String value;
    String type = _schemaMap.get(column);
    if (type == null) {
      type = "string";
      }
    switch (type) {
      case "float": 
        value = String.valueOf(Bytes.toFloat(encodedValue));
        break;
      case "double": 
        value = String.valueOf(Bytes.toDouble(encodedValue));
        break;
      case "integer": 
        value = String.valueOf(Bytes.toInt(encodedValue));
        break;
      case "long": 
        value = String.valueOf(Bytes.toLong(encodedValue));
        break;
      case "image/fits": 
        value = "*image/fits*";
        break;
      case "binary": 
        value = "*binary*";
        break;
      default: // includes "string"
        value = Bytes.toString(encodedValue);
      }
    return value;
    }
    
    
  /** Decode the column value to {@link CellContent}..
    * @param  column       The column to decode.
    * @param  encodedValue The encoded value.
    * @return              The decoded value.
    *                      Unknown types are decoded as strings. */
  public CellContent decode2Content(String column,
                                    byte[] encodedValue) {
    CellContent value;
    String type = _schemaMap.get(column);
    if (type == null) {
      type = "string";
      }
    switch (type) {
      case "float": 
        value = new CellContent(String.valueOf(Bytes.toFloat(encodedValue)));
        break;
      case "double": 
        value = new CellContent(String.valueOf(Bytes.toDouble(encodedValue)));
        break;
      case "integer": 
        value = new CellContent(String.valueOf(Bytes.toInt(encodedValue)));
        break;
      case "long": 
        value = new CellContent(String.valueOf(Bytes.toLong(encodedValue)));
        break;
      case "image/fits":
        value = new CellContent(encodedValue, CellContent.Type.FITS);
        break;
      case "binary":
        value = new CellContent(encodedValue, CellContent.Type.FITS); // TBD: should disappear
        break;
      default: // includes "string"
        value = new CellContent(Bytes.toString(encodedValue));
      }
    return value;
    }
    
  /** Encode the column value.
    * @param  column       The column to encode.
    * @param  decodedValue The decoded value.
    * @return              The encoded value.
    *                      Unknown types are decoded as strings. */
  // TBD: How to encode *binary* ?
  public byte[] encode(String column,
                       String decodedValue) {
    byte[] value;
    String type = _schemaMap.get(column);
    if (type == null) {
      type = "string";
      }
    switch (type) {
      case "float": 
        value = Bytes.toBytes(Float.valueOf(decodedValue));
        break;
      case "double": 
        value = Bytes.toBytes(Double.valueOf(decodedValue));
        break;
      case "integer": 
        value = Bytes.toBytes(Integer.valueOf(decodedValue));
        break;
      case "long": 
        value = Bytes.toBytes(Long.valueOf(decodedValue));
        break;
      case "image/fits": 
        value  = new byte[0]; // BUG
        break;
      case "binary": 
        value  = new byte[0]; // TBD: should disappear
        break;
      default: // includes "string"
        value  = Bytes.toBytes(String.valueOf(decodedValue));
      }
    return value;
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
    
  private Map<String, String> _schemaMap;

  /** Logging . */
  private static Logger log = Logger.getLogger(Schema.class);

  }
