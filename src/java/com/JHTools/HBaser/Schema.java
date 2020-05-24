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
import java.util.HashMap;
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
    * @param schema The schema to set. */
  // TBD: handle schema per version
  public Schema(Map<String, String> schemaMap) {
    _schemaMap = schemaMap;
    }
    
  /** Decode the column value.
    * @param  column       The column to decode.
    * @param  encodedValue The encoded value.
    * @return              The decoded value.
    *                      Binary values are decoded as <tt>*binary*</tt>,
    *                      or showing their MIME-type, when known. */
  public String decode(String column,
                       String encodedValue) {
    String value;
    String type = _schemaMap.get(column);
    if (type == null) {
      return encodedValue;
      }
    switch (type) {
      case "float": 
        value = String.valueOf(Bytes.toFloat(Bytes.toBytes(encodedValue)));
        break;
      case "double": 
        value = String.valueOf(Bytes.toDouble(Bytes.toBytes(encodedValue)));
        break;
      case "integer": 
        value = String.valueOf(Bytes.toInt(Bytes.toBytes(encodedValue)));
        break;
      case "long": 
        value = String.valueOf(Bytes.toLong(Bytes.toBytes(encodedValue)));
        break;
      case "image/fits": 
        value = "*image/fits*";
        break;
      case "binary": 
        value = "*binary*";
        break;
      default: // includes "string"
        value = encodedValue;
      }
    return value;
    }
    
  /** Decode the column value to {@link CellContent}..
    * @param  column       The column to decode.
    * @param  encodedValue The encoded value.
    * @return              The decoded value. */
  public CellContent decode2Content(String column,
                                    String encodedValue) {
    CellContent value;
    String type = _schemaMap.get(column);
    if (type == null) {
      return new CellContent(encodedValue);
      }
    switch (type) {
      case "float": 
        value = new CellContent(String.valueOf(Bytes.toFloat(Bytes.toBytes(encodedValue))));
        break;
      case "double": 
        value = new CellContent(String.valueOf(Bytes.toDouble(Bytes.toBytes(encodedValue))));
        break;
      case "integer": 
        value = new CellContent(String.valueOf(Bytes.toInt(Bytes.toBytes(encodedValue))));
        break;
      case "long": 
        value = new CellContent(String.valueOf(Bytes.toLong(Bytes.toBytes(encodedValue))));
        break;
      case "image/fits":
        value = new CellContent(Bytes.toBytes(encodedValue), CellContent.Type.FITS);
        break;
      case "binary":
        value = new CellContent(Bytes.toBytes(encodedValue), CellContent.Type.FITS); // TBD: should disappear
        break;
      default: // includes "string"
        value = new CellContent(encodedValue);
      }
    return value;
    }
    
  /** Encode the column value.
    * @param  column       The column to encode.
    * @param  decodedValue The decoded value.
    * @return              The encoded value. */
  // TBD: How to encode *binary* ?
  public String encode(String column,
                       String decodedValue) {
    String value;
    String type = _schemaMap.get(column);
    if (type == null) {
      return decodedValue;
      }
    switch (type) {
      case "float": 
        value = String.valueOf(Bytes.toBytes(Float.valueOf(decodedValue)));
        break;
      case "double": 
        value = String.valueOf(Bytes.toBytes(Double.valueOf(decodedValue)));
        break;
      case "integer": 
        value = String.valueOf(Bytes.toBytes(Integer.valueOf(decodedValue)));
        break;
      case "long": 
        value = String.valueOf(Bytes.toBytes(Long.valueOf(decodedValue)));
        break;
      case "image/fits": 
        value  = "*fits*";
        break;
      case "binary": 
        value  = "*binary*"; // TBD: should disappear
        break;
      default: // includes "string"
        value  = decodedValue;
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
    
  private Map<String, String> _schemaMap;

  /** Logging . */
  private static Logger log = Logger.getLogger(Schema.class);

  }
