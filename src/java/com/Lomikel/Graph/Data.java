package com.Lomikel.Graph;

// org.json
import org.json.JSONObject;

// Log4J
import org.apache.log4j.Logger;

/** <code>Date</code> contains {@link Nodes} and {@link Edges}.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Data {
  
  /** Create. 
    * @param nodes The contained {@link Nodes}.
    * @param edges The contained {@link Edges}. */
  public Data(Nodes nodes,
              Edges edges) {
    _data = new JSONObject();
    _data.put("nodes", nodes.toJSONArray());
    _data.put("edges", edges.toJSONArray());
    }
  
  /** Convert into {@link JSONObjeect}.
    * @return The {@link JSONObject} representation. */
  public JSONObject toJSONObject() {
    return _data;
    }
    
  private JSONObject _data;
      
  /** Logging . */
  private static Logger log = Logger.getLogger(Data.class);
   
  }
