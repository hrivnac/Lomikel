package com.Lomikel.Januser;

import com.Lomikel.DB.Client;
import com.Lomikel.Phoenixer.PhoenixClient;

// Tinker Pop
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

// Java
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

// Log4J
import org.apache.log4j.Logger;

/** <code>Rowkeys</code> handles a list of row keys.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Rowkeys {

  public Rowkeys(String[] rowkeyNames) {
    _rowkeyNames = rowkeyNames;
    }

  public void put(String rowkeyName,
                  String rowkey) {
    _rowkeys.put(rowkeyName, rowkey);
    }

    
  // TBD: check correct size
  public void set(String[] rowkeys) {
    _rowkeys.clear();
    for (int i = 0; i < _rowkeyNames.length; i++) {
      put(_rowkeyNames[i], rowkeys[i]);
      }
    }
    
  // TBD: check correct size and names
  public void set(String rowkeys) {
    _rowkeys.clear();
    String[] rkA;
    for (String rk : rowkeys.split("#")) {
      rkA = rk.split("=");
      put(rkA[0], rkA[1]);
      }
    }
    
  // TBD: check for correct name
  public String get(String rowkeyName) {
    return _rowkeys.get(rowkeyName);
    }
    
  public Map<String, String> get() {
    return _rowkeys;
    }
    
  @Override
  public String toString() {
    return "Rowkeys:\n\t" + String.join(",", _rowkeyNames) + "\n\t" + _rowkeys;
    }
    
  private String[] _rowkeyNames;
  
  private Map<String, String> _rowkeys = new HashMap<>();
  
  } 
