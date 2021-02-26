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

  /** Create.
    @param map The {@link Map} of search terms.*/
  public SearchMap(Map<String, String> map) {
    _map = map;
    }
    
  /** Give the embedded {@link Map}.
     *@return The embedded {@link Map}. */
  public Map<String, String> map() {
    return _map;
    }
    
  /** Tell, whether the {@link Map} is empty.
    * @return Whether the {@link Map} is empty. */
  public boolean isEmpty() {
    return _map == null || _map.isEmpty();
    }
    
  /** Clear the {@link Map}. */
  public void clear() {
    _map.clear();
    }
    
  /** Put an entry into the {@link Map}.
    * @param k The entry key (i.e. the name of the search term).
    * @param v The entry value (i.e. the value of the search term). */
  public void put(String k, String v) {
    _map.put(k, v);
    }
    
  @Override
  public String toString() {
    return _map.toString();
    }
    
  private Map<String, String> _map;
  

}
