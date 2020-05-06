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
    
  /** Set prefered sequence of columns.
    * All other columns will be shown after, in alphabetic order.
    * @param columns The prefered sequence of columns. */
  public void setColumns(String[] columns) {
    _columns = Arrays.asList(columns);
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
    * @param limit Max number of rows.
    * @return     The table as html string. */
  public String htmlTable(JSONObject json,
                          int        limit) {
    Map<String, Map<String, String>> table = table(json, limit);
    if (table == null) {
      return "";
      }
    Set<String> columnsSet = new TreeSet<>();
    for (Map<String, String> entry : table.values()) {
      for (String column : entry.keySet()) {
        columnsSet.add(column);
        }
      }
    List<String> columns = new ArrayList<>();
    if (_columns != null) {
      for (String column : _columns) {
        if (columnsSet.contains(column)) {
          columns.add(column);
          columnsSet.remove(column);
          }
        }
      }
    for (String column : columnsSet) {
      columns.add(column);
      }
    String html = "<div id='hbasetable'><table class='sortable'>";
    html += "<thead><tr><td></td>";
    for (String column : columns) {
      html += "<td><b><u>" + column + "</u></b>";
      if (_schema != null) {
        html += "<br/>" + _schema.type(column);
        }
      html += "</td>";
      }
    html += "</tr></thead>";
    String content;
    String id;
    for (Map.Entry<String, Map<String, String>> entry : table.entrySet()) {
      if (entry.getKey().startsWith("schema")) {
        continue;
        }
      html += "<tr><td valign='top'><b>" + entry.getKey() + "</b></td>";
      for (String column : columns) {
        content = entry.getValue().get(column);
        html += "<td>";
        if (content == null) {
          html += "";
          }
        else if (content.length() > 100) {
          id = entry.getKey().replaceAll("\\.", "").replaceAll("\\/", "") + column;
          content = "<pre>" + content.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">","&gt;").replaceAll("\"","&quot;").replaceAll("\n", "<br/>") + "</pre>";
          html += content.substring(0, 20) + "...&nbsp<button onclick=\"" + id + "()\">full</button><script>function " + id + "() {var myWindow = window.open(\"\", \"\", \"width=500,height=500\");myWindow.document.write(\"" + content + "\");}</script>";
          }
        else {
          html += "<pre>" + content + "</pre>";
          }
        html += "</td>";
        }
      html += "</tr>";
      }
    html += "</table></div>";
    return html;
    } 

  /** Set overall {@link Schema}.
    * @param schema The {@link Schema} to set. */
  // TBD: handle schema per row
  public void setSchema(Schema schema) {
    _schema = schema;
    }
    
  private Schema _schema;
    
  private List<String> _columns;
  
  private int _height = 0;

  /** Logging . */
  private static Logger log = Logger.getLogger(HBase2Table.class);

  }
