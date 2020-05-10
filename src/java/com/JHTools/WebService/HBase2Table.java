package com.JHTools.WebService;

import com.JHTools.Utils.Coding;
import com.JHTools.HBaser.Schema;

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

/** <code>HBase2Table</code> interprets <em>HBase</em> data
  * as a HTML table.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class HBase2Table {
    
  /** Set columns to show.
    * @param showColumns  The columns to be shown.
    *                     All columns will be shown if <tt>null</tt> or empty. */  
   public void setShowColumns(String[] showColumns) {
    _showColumns = Arrays.asList(showColumns);
    }  
    
  /** Set first colums to show.
    * @param firstColumns The columns to be shown first.
    *                     All other columns will be shown after, in alphabetic order. */
  public void setFirstColumns(String[] firstColumns) {
    _firstColumns = Arrays.asList(firstColumns);
    }  
    
  /** Convert <em>HBase</em> {@link JSONObject} into table.
    * @param json  The {@link JSONObject} representation of the HBader table.
    * @param limit Max number of rows. <tt>0</tt> means no limit.
    * @return     The table as {@link Map}. */
  public Map<String, Map<String, String>> table(JSONObject json,
                                                int        limit) {
    if (json == null || json.equals("")) {
      return null;
      }
    JSONArray rows = json.getJSONArray("Row");
    JSONArray cells;
    String key;
    String column;
    String value;
    Map<String, Map<String, String>> entries = new HashMap<>();
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
        value  = Coding.decode(cells.getJSONObject(j).getString("$"));
        if (!key.startsWith("schema") && _schema != null) {
          value = _schema.decode(column, value);
          }
        entry.put(column, value);
        }
      entries.put(key, entry);
      }
    return entries;
    }
    
  /** Convert <em>HBase</em> {@link JSONObject} into table.
    * @param json  The {@link JSONObject} representation of the HBase table.
    * @param limit Max number of rows. */
  public void process(JSONObject json,
                          int    limit) {
    Map<String, Map<String, String>> table = table(json, limit);
    if (table == null) {
      return;
      }
    Set<String> columnsSet = new TreeSet<>();
    for (Map<String, String> entry : table.values()) {
      for (String column : entry.keySet()) {
        if (_showColumns == null || _showColumns.isEmpty() || _showColumns.contains(column)) {  
          columnsSet.add(column);
          }
        }
      }
    List<String> columns = new ArrayList<>();
    if (_firstColumns != null && !_firstColumns.isEmpty()) {
      for (String column : _firstColumns) {
        if (columnsSet.contains(column)) {
          columns.add(column);
          columnsSet.remove(column);
          }
        }
      }
    for (String column : columnsSet) {
      columns.add(column);
      }
    _thead = "";
    for (String column : columns) {
      _thead += "<th data-field='" + column + "' data-sortable='true' data-visible='false'><b><u>" + column + "</u></b>";
      if (_schema != null) {
        _thead += "<br/>" + _schema.type(column);
        }
      _thead += "</th>";
      }
    String content;
    String id;
    _data = "";
    boolean firstEntry = true;
    for (Map.Entry<String, Map<String, String>> entry : table.entrySet()) {
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
    
  /** TBD */
  public String thead() {
    return _thead;
    }
    
  /** TBD */
  public String data() {
    return _data;
    }
    
  private Schema _schema;
    
  private List<String> _showColumns;
    
  private List<String> _firstColumns;
  
  private String _thead;
  
  private String _data;

  /** Logging . */
  private static Logger log = Logger.getLogger(HBase2Table.class);

  }
