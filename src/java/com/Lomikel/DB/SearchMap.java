package com.Lomikel.DB;

// Java
import java.util.Map;

/** <code>SearchMap</code> embeds {@link Map} of search arguments. 
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: is should not be necessary
public class SearchMap {

  /** TBD */
  public SearchMap(Map<String, String> map) {
    _map = map;
    }
    
  /** TBD */
  public Map<String, String> map() {
    return _map;
    }
    
  /** TBD */
  public boolean isEmpty() {
    return _map == null || _map.isEmpty();
    }
    
  /** TBD */
  public void clear() {
    _map.clear();
    }
    
  /** TBD */
  public void put(String k, String v) {
    _map.put(k, v);
    }
    
  private Map<String, String> _map;
  

}
