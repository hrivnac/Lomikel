package com.Lomikel.DB;

import com.Lomikel.Utils.LomikelException;

// Java
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>Client</code> handles access to database.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * <tt>T</tt>: the table representation.
  * <tt>S</tt>: the {@link Schema}.
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public abstract class Client<T, S extends Schema> {
   
  // Lifecycle -----------------------------------------------------------------
	
  /** Connect to the table. Using the latest schema starting with <tt>schema</tt>.
    * @param tableName  The table name.
    * @return           The assigned table. 
    * @throws LomikelException If anything goes wrong. */
   public abstract T connect(String tableName) throws LomikelException;
             
  /** Connect to the table.
    * @param tableName  The table name.
    * @param schemaName The name of the {@link Schema} row.
    *                   <tt>null</tt> means to ignore schema,
    *                   empty {@link String} will take the latest one. 
    * @return           The assigned table.
    * @throws LomikelException If anything goes wrong. */
   public abstract T connect(String tableName,
                             String schemaName) throws LomikelException;
     
  /** Connect to the table.
    * @param tableName  The table name.
    * @param schemaName The name of the {@link Schema} row.
    *                   <tt>null</tt> means to ignore schema,
    *                   empty {@link String} will take the latest one. 
    * @param timeout    The timeout in ms (may be <tt>0</tt>).
    * @return           The assigned table.
    * @throws LomikelException If anything goes wrong. */
  public abstract T connect(String tableName,
                            String schemaName,
                            int    timeout) throws LomikelException;
   
  /** Connect to the table.
    * @param tableName  The table name.
    * @param schemaName The name of the {@link Schema} row.
    *                   <tt>null</tt> means to ignore schema,
    *                   empty {@link String} will take the latest one. 
    * @param timeout    The timeout in ms (may be <tt>0</tt>).
    * @param timeout    The retries (may be <tt>0</tt>).
    * @return           The assigned table.
    * @throws LomikelException If anything goes wrong. */
  public abstract T connect(String tableName,
                            String schemaName,
                            int    timeout,
                            int    retries) throws LomikelException;
  @Override
  protected void finalize() throws Throwable {
    close();
    }
	  
  /** Close and release resources. */
  public abstract void close();
    
  // Search --------------------------------------------------------------------
  
  /** Delete row(s) with all cell versions.
    * @param key     The row key. */
  public abstract void delete(String key);
   
  /** Get row(s) with latest cell version.
    * @param key     The row key. Disables other search terms.
    *                It can be <tt>null</tt>.
   * @param search  The search terms as <tt>family:column:value,...</tt>.
    *                Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                <tt>key:startKey</tt> and <tt>key:stopKey</tt> van restrict search to a key interval.
    *                {@link Comparator} can be chosen as <tt>family:column:value:comparator</tt>
    *                among <tt>exact,prefix,substring,regex</tt>.
    *                The default for key is <tt>prefix</tt>,
    *                the default for columns is <tt>substring</tt>.
    *                The randomiser can be added with <tt>key:random:chance</tt>.
    *                It can be <tt>null</tt>.
    *                All searches are executed as prefix searches.    
    * @param filter  The names of required values as <tt>family:column,...</tt>.
    *                <tt>*</tt> = all.
    * @param delay   The time period start, in minutes back since dow.
    *                <tt>0</tt> means no time restriction.
    * @param ifkey   Whether give also entries keys (as <tt>key:key</tt>).
    * @param iftime  Whether give also entries timestamps (as <tt>key:time</tt>).
    * @return        The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public Map<String, Map<String, String>> scan(String  key,
                                               String  search,
                                               String  filter,
                                               long    delay,
                                               boolean ifkey,
                                               boolean iftime) {
    String searchMsg = search;
    if (searchMsg != null && searchMsg.length() > 80) {
      searchMsg = searchMsg.substring(0, 80) + "...";
      }
    log.debug("Searching for key: " + key + 
              ", search: " + searchMsg + 
              ", filter: " + filter +
              ", delay: "  + delay + " min" + 
              ", id/time: " + ifkey + "/" + iftime);
    long now = System.currentTimeMillis();
    long start = (delay == 0L) ? 0L :  now - delay * 1000L * 60L;
    long stop = now;
    return scan(key, search, filter, start, stop, ifkey, iftime);
    }
  
  /** Get row(s) with all cell versions.
    * @param key     The row key. Disables other search terms.
    *                It can be <tt>null</tt>.
    * @param search  The search terms as <tt>family:column:value,...</tt>.
    *                Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                <tt>key:startKey</tt> and <tt>key:stopKey</tt> van restrict search to a key interval.
    *                {@link Comparator} can be chosen as <tt>family:column:value:comparator</tt>
    *                among <tt>exact,prefix,substring,regex</tt>.
    *                The default for key is <tt>prefix</tt>,
    *                the default for columns is <tt>substring</tt>.
    *                The randomiser can be added with <tt>key:random:chance</tt>.
    *                It can be <tt>null</tt>.
    *                All searches are executed as prefix searches.    
    * @param filter  The names of required values as <tt>family:column,...</tt>.
    *                <tt>*</tt> = all.
    * @param delay   The time period start, in minutes back since dow.
    *                <tt>0</tt> means no time restriction.
    * @return        The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public Map<String, Map<String, Map<Long, String>>> scan3(String  key,
                                                           String  search,
                                                           String  filter,
                                                           long    delay) {
    String searchMsg = search;
    if (searchMsg != null && searchMsg.length() > 80) {
      searchMsg = searchMsg.substring(0, 80) + "...";
      }
    log.debug("Searching for key: " + key + 
              ", search: " + searchMsg + 
              ", filter: " + filter +
              ", delay: "  + delay + " min");
    long now = System.currentTimeMillis();
    long start = (delay == 0L) ? 0L :  now - delay * 1000L * 60L;
    long stop = now;
    return scan3(key, search, filter, start, stop);
    }
    
  /** Get row(s) with latest cell version.
    * @param key     The row key. Disables other search terms.
    *                It can be <tt>null</tt>.
    * @param search  The search terms as <tt>family:column:value,...</tt>.
    *                Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                <tt>key:startKey</tt> and <tt>key:stopKey</tt> van restrict search to a key interval.
    *                {@link Comparator} can be chosen as <tt>family:column:value:comparator</tt>
    *                among <tt>exact,prefix,substring,regex</tt>.
    *                The default for key is <tt>prefix</tt>,
    *                the default for columns is <tt>substring</tt>.
    *                The randomiser can be added with <tt>key:random:chance</tt>.
    *                It can be <tt>null</tt>.
    *                All searches are executed as prefix searches.    
    * @param filter  The names of required values as <tt>family:column,...</tt>.
    *                <tt>*</tt> = all.
    * @param start   The time period start timestamp in <tt>ms</tt>.
    *                <tt>0</tt> means since the beginning.
    * @param stop    The time period stop timestamp in <tt>ms</tt>.
    *                <tt>0</tt> means till now.
    * @param ifkey   Whether give also entries keys (as <tt>key:key</tt>).
    * @param iftime  Whether give also entries timestamps (as <tt>key:time</tt>).
    * @return        The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public Map<String, Map<String, String>> scan(String  key,
                                               String  search,
                                               String  filter,
                                               long    start,
                                               long    stop,
                                               boolean ifkey,
                                               boolean iftime) {
    String searchMsg = search;
    if (search != null && search.length() > 80) {
      searchMsg = search.substring(0, 80) + "...";
      }
    log.info("Searching for key: " + key + 
             ", search: " + searchMsg + 
             ", filter: " + filter +
             ", interval: " + start + " ms - " + stop + " ms" +
             ", id/time: " + ifkey + "/" + iftime);
    Map<String, String> searchM = new TreeMap<>();
    if (search != null && !search.trim().equals("")) {
      String[] ss;
      String k;
      String v;
      for (String s : search.trim().split(",")) {
        ss = s.trim().split(":");
        if (ss.length == 4) {
          k = ss[0] + ":" + ss[1] + ":" + ss[3];
          }
        else {
          k = ss[0] + ":" + ss[1];
          }
        v = ss[2];
        if (searchM.containsKey(k)) {
          v = searchM.get(k) + "," + v;
          }
        searchM.put(k, v);
        }
      }
    return scan(key, searchM, filter, start, stop, ifkey, iftime);
    }
    
  /** Get row(s) with all cell versions.
    * @param key     The row key. Disables other search terms.
    *                It can be <tt>null</tt>.
    * @param search  The search terms as <tt>family:column:value,...</tt>.
    *                Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                <tt>key:startKey</tt> and <tt>key:stopKey</tt> van restrict search to a key interval.
    *                {@link Comparator} can be chosen as <tt>family:column:value:comparator</tt>
    *                among <tt>exact,prefix,substring,regex</tt>.
    *                The default for key is <tt>prefix</tt>,
    *                the default for columns is <tt>substring</tt>.
    *                The randomiser can be added with <tt>key:random:chance</tt>.
    *                It can be <tt>null</tt>.
    *                All searches are executed as prefix searches.    
    * @param filter  The names of required values as <tt>family:column,...</tt>.
    *                <tt>*</tt> = all.
    * @param start   The time period start timestamp in <tt>ms</tt>.
    *                <tt>0</tt> means since the beginning.
    * @param stop    The time period stop timestamp in <tt>ms</tt>.
    *                <tt>0</tt> means till now.
    * @param ifkey   Whether give also entries keys (as <tt>key:key</tt>).
    * @param iftime  Whether give also entries timestamps (as <tt>key:time</tt>).
    * @return        The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  // TBD: refactor scan3/scan
  public Map<String, Map<String, Map<Long, String>>> scan3(String  key,
                                                           String  search,
                                                           String  filter,
                                                           long    start,
                                                           long    stop) {
    String searchMsg = search;
    if (search != null && search.length() > 80) {
      searchMsg = search.substring(0, 80) + "...";
      }
    log.info("Searching for key: " + key + 
             ", search: " + searchMsg + 
             ", filter: " + filter +
             ", interval: " + start + " ms - " + stop + " ms");
    Map<String, String> searchM = new TreeMap<>();
    if (search != null && !search.trim().equals("")) {
      String[] ss;
      String k;
      String v;
      for (String s : search.trim().split(",")) {
        ss = s.trim().split(":");
        if (ss.length == 4) {
          k = ss[0] + ":" + ss[1] + ":" + ss[3];
          }
        else {
          k = ss[0] + ":" + ss[1];
          }
        v = ss[2];
        if (searchM.containsKey(k)) {
          v = searchM.get(k) + "," + v;
          }
        searchM.put(k, v);
        }
      }
    return scan3(key, searchM, filter, start, stop);
    }
    
  /** Get row(s) with latest cell version.
    * @param key       The row key. Disables other search terms.
    *                  It can be <tt>null</tt>.
    * @param searchMap The {@link Map} of search terms as <tt>family:column-value,value,...</tt>.
    *                  Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                  <tt>key:startKey</tt> and <tt>key:stopKey</tt> van restrict search to a key interval.
    *                  {@link Comparator} can be chosen as <tt>family:column:value:comparator-value</tt>
    *                  among <tt>exact,prefix,substring,regex</tt>.
    *                  The default for key is <tt>prefix</tt>,
    *                  the default for columns is <tt>substring</tt>.
    *                  The randomiser can be added with <tt>key:random:chance</tt>.
    *                  It can be <tt>null</tt>.
    *                  All searches are executed as prefix searches.    
    * @param filter    The names of required values as <tt>family:column,...</tt>.
    *                  <tt>*</tt> = all.
    * @param start     The time period start timestamp in <tt>ms</tt>.
    *                  <tt>0</tt> means since the beginning.
    * @param stop      The time period stop timestamp in <tt>ms</tt>.
    *                  <tt>0</tt> means till now.
    * @param ifkey     Whether give also entries keys (as <tt>key:key</tt>).
    * @param iftime    Whether give also entries timestamps (as <tt>key:time</tt>).
    * @return          The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public Map<String, Map<String, String>> scan(String              key,
                                               Map<String, String> searchMap,
                                               String              filter,
                                               long                start,
                                               long                stop,
                                               boolean             ifkey,
                                               boolean             iftime) {
    return scan(key, new SearchMap(searchMap), filter, start, stop, ifkey, iftime);
    }
    
  /** Get row(s) with all cell versions.
    * @param key       The row key. Disables other search terms.
    *                  It can be <tt>null</tt>.
    * @param searchMap The {@link Map} of search terms as <tt>family:column-value,value,...</tt>.
    *                  Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                  <tt>key:startKey</tt> and <tt>key:stopKey</tt> van restrict search to a key interval.
    *                  {@link Comparator} can be chosen as <tt>family:column:value:comparator-value</tt>
    *                  among <tt>exact,prefix,substring,regex</tt>.
    *                  The default for key is <tt>prefix</tt>,
    *                  the default for columns is <tt>substring</tt>.
    *                  The randomiser can be added with <tt>key:random:chance</tt>.
    *                  It can be <tt>null</tt>.
    *                  All searches are executed as prefix searches.    
    * @param filter    The names of required values as <tt>family:column,...</tt>.
    *                  <tt>*</tt> = all.
    * @param start     The time period start timestamp in <tt>ms</tt>.
    *                  <tt>0</tt> means since the beginning.
    * @param stop      The time period stop timestamp in <tt>ms</tt>.
    *                  <tt>0</tt> means till now.
    * @return          The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public Map<String, Map<String, Map<Long, String>>> scan3(String              key,
                                                           Map<String, String> searchMap,
                                                           String              filter,
                                                           long                start,
                                                           long                stop) {
    return scan3(key, new SearchMap(searchMap), filter, start, stop);
    }
    
  /** Get row(s) with the latest cell version.
    * @param key       The row key. Disables other search terms.
    *                  It can be <tt>null</tt>.
    * @param searchMap The {@link Map} of search terms as <tt>family:column-value,value,...</tt>.
    *                  Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                  <tt>key:startKey</tt> and <tt>key:stopKey</tt> van restrict search to a key interval.
    *                  {@link Comparator} can be chosen as <tt>family:column:comparator-value</tt>
    *                  among <tt>exact,prefix,substring,regex</tt>.
    *                  The default for key is <tt>prefix</tt>,
    *                  the default for columns is <tt>substring</tt>.
    *                  The randomiser can be added with <tt>key:random:chance</tt>.
    *                  It can be <tt>null</tt>.
    *                  All searches are executed as prefix searches.    
    * @param filter    The names of required values as <tt>family:column,...</tt>.
    *                  <tt>*</tt> = all.
    * @param start     The time period start timestamp in <tt>ms</tt>.
    *                  <tt>0</tt> means since the beginning.
    * @param stop      The time period stop timestamp in <tt>ms</tt>.
    *                  <tt>0</tt> means till now.
    * @param ifkey     Whether give also entries keys (as <tt>key:key</tt>).
    * @param iftime    Whether give also entries timestamps (as <tt>key:time</tt>).
    * @return          The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public abstract Map<String, Map<String, String>> scan(String    key,
                                                        SearchMap searchMap,
                                                        String    filter,
                                                        long      start,
                                                        long      stop,
                                                        boolean   ifkey,
                                                        boolean   iftime);
    
  /** Get row(s) with all cell versions.
    * @param key       The row key. Disables other search terms.
    *                  It can be <tt>null</tt>.
    * @param searchMap The {@link Map} of search terms as <tt>family:column-value,value,...</tt>.
    *                  Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                  <tt>key:startKey</tt> and <tt>key:stopKey</tt> van restrict search to a key interval.
    *                  {@link Comparator} can be chosen as <tt>family:column:comparator-value</tt>
    *                  among <tt>exact,prefix,substring,regex</tt>.
    *                  The default for key is <tt>prefix</tt>,
    *                  the default for columns is <tt>substring</tt>.
    *                  The randomiser can be added with <tt>key:random:chance</tt>.
    *                  It can be <tt>null</tt>.
    *                  All searches are executed as prefix searches.    
    * @param filter    The names of required values as <tt>family:column,...</tt>.
    *                  <tt>*</tt> = all.
    * @param start     The time period start timestamp in <tt>ms</tt>.
    *                  <tt>0</tt> means since the beginning.
    * @param stop      The time period stop timestamp in <tt>ms</tt>.
    *                  <tt>0</tt> means till now.
    * @return          The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public abstract Map<String, Map<String, Map<Long, String>>> scan3(String    key,
                                                                    SearchMap searchMap,
                                                                    String    filter,
                                                                    long      start,
                                                                    long      stop);
  
  // Aux -----------------------------------------------------------------------
    
  /** Results presented as a readable {@link String}.
    * @param results The {@link Map} of results.
    * @return        The result in a readable {@link String}. */
  public static String results2String(Map<String, Map<String, String>> results) {
    StringBuffer reportB = new StringBuffer();
    for (Map.Entry<String, Map<String, String>> entry : results.entrySet()) {
      reportB.append(entry.getKey())
             .append(" = ")
             .append(entry.getValue())
             .append("\n");
      }
    return reportB.toString();
    }
    
  /** Results presented as a {@link List}.
    * @param results The {@link Map} of results.
    * @return        The result as a {@link List}. */
  public static List<Map<String, String>> results2List(Map<String, Map<String, String>> results) {
    List<Map<String, String>> report = new ArrayList<>();
    Map<String, String> row;
    for (Map.Entry<String, Map<String, String>> entry : results.entrySet()) {
      row = new TreeMap<>();
      row.put("key:key",  entry.getKey());
      for (Map.Entry<String, String> cell : entry.getValue().entrySet()) {
        row.put(cell.getKey(), cell.getValue());
        }
      report.add(row);
      }
    return report;
    }
                         
  /** Set the table name.
    * @param schema The table name to set. */
  public void setTableName(String tableName) {
    _tableName = tableName;
    }
    
  /** Give the table name.
    * @param schema The used table name. */
  public String tableName() {
    return _tableName;
    }
                     
  /** Set the table {@link Schema}.
    * @param schema The {@link Schema} to set. */
  public void setSchema(S schema) {
    _schema = schema;
    }
    
  /** Give the table {@link Schema}.
    * @param schema The used {@link Schema}. */
  public S schema() {
    return _schema;
    }
    
  /** Set the limit for the number of results.
    * @param limit The limit for the number of results. */
  public void setLimit(int limit) {
    log.info("Setting limit " + limit);
    _limit = limit;
    }
    
  /** Give the limit for the number of results.
    * @return The limit for the number of results. */
  public int limit() {
    return _limit;
    }
    
  /** Set whether the results should be in the reversed order.
    * @param reversed Whether the results should be in the reversed order. */
  public void setReversed(boolean reversed) {
    log.info("Setting reversed " + reversed);
    _reversed = reversed;
    }
    
  /** Tell, whether the results should be in the reversed order.
    * @return  Whether the results should be in the reversed order. */
  public boolean isReversed() {
    return _reversed;
    }
    
  /** Give {@link Class} representing a {@link Vertex} of a label.
    * @param lbl The {@link Vertex} label (i.e. <em>lbl</em>) value of
    *            the {@link Vertex} to be represented.
    * @return    The representation of requested {@link Vertex}. */
  public static void registerVertexType(String lbl,
                                        Class  representant) {
    log.info(lbl + "  will be represented by " + representant);
    _representations.put(lbl, representant);
    }      
    
  /** Give {@link Class} representing a {@link Vertex} of a label.
    * @param lbl The {@link Vertex} label (i.e. <em>lbl</em>) value of
    *            the {@link Vertex} to be represented.
    * @return    The representation of requested {@link Vertex}. */
  public Class representation(String lbl) {
    return _representations.get(lbl);
    }
    
  /** Give all {@link Class} representing a {@link Vertex} of a label.
    *            the {@link Vertex} to be represented.
    * @return    All representation of requested {@link Vertex}s. */
  public  Map<String, Class> representations() {
    return _representations;
    }
    
  /** Give Graph property name derived from the Phoenix column name.
    * It uses {@link Schema#reMap}.
    * @param name The Phoenix column name.
    * @return     The correspinding Graph name. */
  public String rename(String name) {
    if (_schema.reMap() != null && _schema.reMap().containsKey(name)) {
      return _schema.reMap().get(name).toString(); // BUG: toString() should not be needed
      }
    return name;
    }
  
  private String _tableName;
  
  private S _schema;

  private int _limit = 0;
  
  private boolean _reversed = false;
      
  private static Map<String, Class> _representations = new TreeMap<>();

  
  /** Logging . */
  private static Logger log = LogManager.getLogger(Client.class);

  }
