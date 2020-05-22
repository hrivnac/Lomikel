package com.JHTools.WebService;

import com.JHTools.Utils.Coding;
import com.JHTools.HBaser.Schema;
import com.JHTools.HBaser.CellContent;

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
import java.util.Base64;

// Log4J
import org.apache.log4j.Logger;

/** <code>HBase2Table</code> interprets <em>HBase</em> data
  * as a HTML table.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class HBase2Table {
    
  /** Create. */
  public HBase2Table() {
   reset();
   }
   
  /** TBD */
  public void setup(String hbase,
                    String htable) {
    log.info("Setting " + htable + "@" + hbase);
    reset();
    _hbase  = hbase;
    _htable = htable;
    }
   
  /** TBD */
  public void reset() {
    _repository.clear();
    _schema = null;
    _showColumns = null;
    _thead = null;
    _data = null;
    _table.clear();
    _fLengths.clear();
    }
  
  /** Set columns to show.
    * @param showColumns  The columns to be shown.
    *                     All columns will be shown if <tt>null</tt> or empty. */  
   public void setShowColumns(String[] showColumns) {
    _showColumns = Arrays.asList(showColumns);
    }  
    
  /** Convert <em>HBase</em> {@link JSONObject} into table.
    * @param json  The {@link JSONObject} representation of the HBase table.
    * @param limit Max number of rows. <tt>0</tt> means no limit. */
  public void setTable(JSONObject json,
                       int        limit) {
    log.info("Setting HBase table " + toString());
    if (json == null || json.equals("")) {
      return;
      }
    JSONArray rows = json.getJSONArray("Row");
    JSONArray cells;
    String key;
    String column;
    String value;
    CellContent cc;
    String id;
    _table.clear();
    Map<String, String> entry;
    int n = 1;
    for (int i = 0; i < rows.length(); i++) {
      if (limit != 0 && n++ > limit) {
        break;
        }
      key = Coding.decode(rows.getJSONObject(i).getString("key"));
      entry = new HashMap<>();
      cells = rows.getJSONObject(i).getJSONArray("Cell");
      for (int j = 0; j < cells.length(); j++) {
        column = Coding.decode(cells.getJSONObject(j).getString("column"));
        value  = cells.getJSONObject(j).getString("$");
        if (!key.startsWith("schema") && _schema != null) {
          if (column.startsWith("b:")) {
            id = "url:" + key + ":" + column;
            entry.put(column, id);
            _repository.put(id, Base64.getDecoder().decode(value));
            }
          else {
            entry.put(column, _schema.decode2Content(column, Coding.decode(value)).asString());
            }
          }
        else {
          entry.put(column, Coding.decode(value));
          }
        }
      _table.put(key, entry);
      }
    }
    
  /** Convert <em>HBase</em> {@link JSONObject} into table and create its Web representation..
    * @param json  The {@link JSONObject} representation of the HBase table.
    * @param limit Max number of rows. */
  public void processTable(JSONObject json,
                           int        limit) {
    log.info("Processing HBase table " + toString());
    setTable(json, limit);
    if (_table == null) {
      return;
      }
    Set<String> columns0 = new TreeSet<>();
    for (Map<String, String> entry : _table.values()) {
      for (String column : entry.keySet()) {
        if (_showColumns == null || _showColumns.isEmpty() || _showColumns.contains(column)) {  
          columns0.add(column);
          }
        }
      }
    // TBD: support non-default columns
    List<String> columns = new ArrayList<>();
    _fLengths.clear();
    for (String family : new String[]{"b", "i", "d"}) {
      _fLengths.put(family, 0);
      for (String column : columns0) {
        if (column.startsWith(family + ":")) {
          _fLengths.put(family, _fLengths.get(family) + 1);
          columns.add(column);
          }
        }
      }
    _thead = "";
    String formatter;
    for (String column : columns) {
      formatter = "";
      if (column.startsWith("b:")) {
        formatter += "data-formatter='binaryFormatter' ";
        }
      _thead += "<th data-field='" + column + "' data-sortable='true' data-visible='false' " + formatter + "><b><u>" + column + "</u></b>";
      if (_schema != null) {
        _thead += "<br/><small>" + _schema.type(column) + "</small>";
        }
      _thead += "</th>";
      }
    String content;
    String id;
    _data = "";
    boolean firstEntry = true;
    for (Map.Entry<String, Map<String, String>> entry : _table.entrySet()) {
      if (entry.getKey().startsWith("schema")) {
        continue;
        }
      if (!firstEntry) {
        _data += ",";
        }
      else {
        firstEntry = false;
        }
      _data += "{\n";
      _data += "'key':'" + entry.getKey() + "'\n";
      for (String column : columns) {
        content = entry.getValue().get(column);
        _data += ",'" + column + "':'" + entry.getValue().get(column) + "'\n";
        }
      _data += "}\n";
      }
    } 

  /** Set overall {@link Schema}.
    * @param schema The {@link Schema} to set. */
  // TBD: handle schema per row
  public void setSchema(Schema schema) {
    _schema = schema;
    }
    
  /** Give the table header.
    * @return The table header. */
  public String thead() {
    return _thead;
    }
    
  /** Give the table content.
    * @return The table content. */
  public String data() {
    return _data;
    }
    
  /** TBD */
  // TBD: _data should be creatd from _table
  public Map<String, Map<String, String>> table() {
    return _table;
    }
     
  /** Give number of columns for a  column family.
    * @param  The column family name.
    * @return The number of columns for a column family*/
  public int familyLength(String family) {
    return _fLengths.get(family);
    }
    
  /** TBD */
  public int width() {
    if (_schema != null) {
      return _schema.size();
      }
    return 0;
    }
    
  /** Give the {@link BinaryDataRepository} with binary content.
    * @return The {@link BinaryDataRepository} with binary content. */
  public BinaryDataRepository repository() {
    return _repository;
    }
    
  /** TBD */
  public String toHide(String idCol) {
    String id;
    Map<String, TreeSet<String>> id2key = new HashMap<>();
    for (Map.Entry<String, Map<String, String>> entry : _table.entrySet()) {
      if (entry.getKey().startsWith("schema")) {
        continue;
        };
      id = entry.getValue().get(idCol);
      if (!id2key.containsKey(id)) {
        id2key.put(id, new TreeSet<String>());
        }
      id2key.get(id).add(entry.getKey());
      }
    String hidden = "[";
    boolean firstEntry = true;
    for (Map.Entry<String, TreeSet<String>> e : id2key.entrySet()) {
      e.getValue().remove(e.getValue().last());
      for (String s : e.getValue()) {
        if (!firstEntry) {
          hidden += ",";
          }
        else {
          firstEntry = false;
          }
        hidden += "'" + s + "'";
        }
      }
    hidden += "]";
    return hidden;
    }
    
  /** TBD */
  public String toString() {
    return _htable + "@" + _hbase + "(" + width() + ")";
    }
    
  private String _hbase;
  
  private String _htable;
    
  private BinaryDataRepository _repository = new BinaryDataRepository();  
    
  private Schema _schema;
    
  private List<String> _showColumns;
  
  private String _thead;
  
  private String _data;
  
  private Map<String, Map<String, String>> _table = new HashMap<>();
  
  private Map<String, Integer> _fLengths = new HashMap<>();

  /** Logging . */
  private static Logger log = Logger.getLogger(HBase2Table.class);

  }
