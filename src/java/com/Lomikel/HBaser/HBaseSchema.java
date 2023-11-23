package com.Lomikel.HBaser;

import com.Lomikel.DB.Schema;
import com.Lomikel.DB.CellContent;
import com.Lomikel.Utils.ByteArray;

// HBase
import org.apache.hadoop.hbase.util.Bytes;

// Java
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.stream.Collectors;

// Log4J
import org.apache.log4j.Logger;

/** <code>Schema</code>handles <em>HBase</em> types coding/decoding.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: handle all types
public class HBaseSchema extends Schema<ByteArray> {
  
  /** Set overall schema.
    * @param schemaName The name of the schema to set.
    * @param schemaMap  The schema to set. */
  public HBaseSchema(String              schemaName,
                     Map<String, String> schemaMap) {
    super(schemaName, schemaMap);
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
    return decode(column, new ByteArray(encodedValue));
    }
    
  @Override
  public String decode(String    column,
                       ByteArray encodedValue) {
    byte[] ev = encodedValue.bytes();
    String value;
    String type = map().get(column);
    if (type == null) {
      type = "string";
      }
    switch (type) {
      case "float": 
        value = String.valueOf(Bytes.toFloat(ev));
        break;
      case "double": 
        value = String.valueOf(Bytes.toDouble(ev));
        break;
      case "integer": 
        value = String.valueOf(Bytes.toInt(ev));
        break;
      case "long": 
        value = String.valueOf(Bytes.toLong(ev));
        break;
      case "short": 
        value = String.valueOf(Bytes.toShort(ev));
        break;
      case "fits": 
        value = "*fits*";
        break;
      case "fits/image": 
        value = "*fits*";
        break;
      case "binary": 
        value = "*binary*";
        break;
      default: // includes "string"
        value = Bytes.toString(ev);
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
    return decode2Content(column, new ByteArray(encodedValue));
    }
    
  @Override
  public CellContent decode2Content(String    column,
                                    ByteArray encodedValue) {
    byte[] ev = encodedValue.bytes();
    CellContent value;
    String type = map().get(column);
    if (type == null) {
      type = "string";
      }
    switch (type) {
      case "float": 
        value = new CellContent(String.valueOf(Bytes.toFloat(ev)));
        break;
      case "double": 
        value = new CellContent(String.valueOf(Bytes.toDouble(ev)));
        break;
      case "integer": 
        value = new CellContent(String.valueOf(Bytes.toInt(ev)));
        break;
      case "long": 
        value = new CellContent(String.valueOf(Bytes.toLong(ev)));
        break;
      case "fits":
        value = new CellContent(ev, CellContent.Type.FITS); // TBD: should disappear
        break;
      case "fits/image":
        value = new CellContent(ev, CellContent.Type.FITS);
        break;
      case "binary":
        value = new CellContent(ev, CellContent.Type.FITS); // TBD: should disappear
        break;
      default: // includes "string"
        value = new CellContent(Bytes.toString(ev));
      }
    return value;
    }
    
  @Override
  public ByteArray encode(String column,
                          String decodedValue) {
    byte[] value;
    String type = map().get(column);
    if (type == null) {
      type = "string";
      }
    switch (type) {
      case "float": 
        value = Bytes.toBytes(Float.parseFloat(decodedValue));
        break;
      case "double": 
        value = Bytes.toBytes(Double.parseDouble(decodedValue));
        break;
      case "integer": 
        value = Bytes.toBytes(Integer.parseInt(decodedValue));
        break;
      case "long": 
        value = Bytes.toBytes(Long.parseLong(decodedValue));
        break;
      case "short": 
        value = Bytes.toBytes(Short.parseShort(decodedValue));
        break;
      case "fits": 
        value  = new byte[0]; // TBD: should disappear
        break;
      case "fits/image": 
        value  = new byte[0];
        break;
      case "binary": 
        value  = new byte[0]; // TBD: should disappear
        break;
      default: // includes "string"
        value  = Bytes.toBytes(String.valueOf(decodedValue));
      }
    return new ByteArray(value);
    }
        
  /** Strip families from column names.
    * @return The {@link HBaseSchema} without family names. */
  public HBaseSchema simpleSchema() {
    Map<String, String> newMap = new TreeMap<>();
    for (Map.Entry<String, String> entry : map().entrySet()) {
      newMap.put(entry.getKey().split(":")[1], entry.getValue());
      }
    return new HBaseSchema(name(), newMap);
    }    
    
  /** Give SQL view creation command for this schema.
    * To be used in Phoenix representation of the HBase table. 
    * @param viewName The name of created SQL view.
    * @return The SQL table creation command for this schema. */
  public String toSQLView(String viewName) {
    String sql = "DROP VIEW " + viewName + ";\n";
    sql += "CREATE VIEW \"" + viewName + "\" (";
    sql += "ROWKEY VARCHAR PRIMARY KEY,";
    sql += map().entrySet()
                .stream()
                .map(e -> "\"" + e.getKey().split(":")[0] + "\".\"" + e.getKey().split(":")[1] + "\" VARCHAR")
                .collect(Collectors.joining(","));
    sql += ");";
    return sql;
    }
     
  @Override
  public String toString() {
    return "HBase" + super.toString();
    }

  /** Logging . */
  private static Logger log = Logger.getLogger(HBaseSchema.class);

  }
