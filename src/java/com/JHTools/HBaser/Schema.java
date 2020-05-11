package com.JHTools.HBaser;

import com.JHTools.Utils.Coding;

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
    * @return              The decoded value. */
  public String decode(String column,
                       String encodedValue) {
    String value;
    switch (_schemaMap.get(column)) {
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
    switch (_schemaMap.get(column)) {
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
      case "binary":
        value = new CellContent(Bytes.toBytes(encodedValue), CellContent.Type.FITS); // TBD: should be from schema
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
  public String encode(String column,
                       String decodedValue) {
    String value;
    switch (_schemaMap.get(column)) {
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
      case "binary": 
        value  = "*binary*";
        break;
      default: // includes "string"
        value  = decodedValue;
      }
    return value;
    }

  /** Give the colun type (from {@link Schema}).
    * @param column The column.
    * @return       The column type. */
  public String type(String column) {
    return _schemaMap.get(column);
    }
    
  private Map<String, String> _schemaMap;

  /** Logging . */
  private static Logger log = Logger.getLogger(Schema.class);

  }
