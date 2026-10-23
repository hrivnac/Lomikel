package com.Lomikel.Utils;

// Java
import java.util.Comparator;
import java.util.Collections;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedList;

/** <code>MapUtil</code> contains various utilities to operate
  * on {@link Map}s.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class MapUtil {
    
  /** Sort {@link Map} based on its values in ascending order, with nulls first.
    * Equal values retain their input iteration order.
    * @param  map The {@link Map} to be sorted.
    * @return The sorted {@link Map}. */
  public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
    return sortByValue(map, false);
    }
    
  /** Sort {@link Map} based on its values; nulls sort first in ascending order
    * and last in reverse order. Equal values retain their input iteration order.
    * @param  map The {@link Map} to be sorted.
    * @param  reverse Whether to sort in reverse order.
    * @return The sorted {@link Map}. */
  public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map,
                                                                           boolean   reverse) {
    List<Map.Entry<K, V>> list = new LinkedList<>(map.entrySet());
    Comparator<V> values = Comparator.nullsFirst(Comparator.naturalOrder());
    Collections.sort(list, new Comparator<Map.Entry<K, V>>() {
      public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
        return reverse ? values.compare(o2.getValue(), o1.getValue())
                       : values.compare(o1.getValue(), o2.getValue());
        }
      });
    Map<K, V> result = new LinkedHashMap<>();
    for (Map.Entry<K, V> entry : list) {
      result.put(entry.getKey(), entry.getValue());
      }
    return result;
    }
    
  }
