package com.JHTools.WebService;

import com.JHTools.Utils.Coding;
import com.JHTools.HBaser.Schema;
import com.JHTools.HBaser.CellContent;
import com.JHTools.HBaser.BinaryDataRepository;

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
   
  /** Reset all data. */
  public void reset() {
    _repository.clear();
    _schema = null;
    _thead = null;
    _data = null;
    _table.clear();
    _fLengths.clear();
    }
    
  /** Convert <em>HBase</em> table into its Web representation..
    * @param table      The {@link Map} of {@link Map}s as <tt>key-&t;{family:column-&gt;value}</tt>. 
    * @param schema     The {@link Schema} to use (may be <tt>null</tt>).
    * @param repository The {@link BinaryDataRepository} with related binary data. */
  public void processTable(Map<String, Map<String, String>> table,
                           Schema                           schema,
                           BinaryDataRepository             repository) {
    log.info("Processing HBase table");
    if (_table == null) {
      return;
      }
    _table      = table;
    _schema     = schema;
    _repository = repository;
    Set<String> columns0;
    if (_schema == null) {
      columns0 = new TreeSet<>();
      for (Map<String, String> entry : _table.values()) {
        for (String column : entry.keySet()) {
          columns0.add(column);
          }
        }
      }
    else {
      columns0 = _schema.columnNames();
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
    
  /** Give the content as a multi{@link Map}.
    * @return The content as a multi{@link Map} <code>key-{column-value,...}</code>. */
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
    
  /** Give the number of known columns.
    * @return The number of known columns. */
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
    
  private BinaryDataRepository _repository = new BinaryDataRepository();  
    
  private Schema _schema;
  
  private String _thead;
  
  private String _data;
  
  private Map<String, Map<String, String>> _table = new HashMap<>();
  
  private Map<String, Integer> _fLengths = new HashMap<>();

  /** Logging . */
  private static Logger log = Logger.getLogger(HBase2Table.class);

  }
