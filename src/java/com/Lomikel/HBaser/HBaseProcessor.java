package com.Lomikel.HBaser;

// Java
import java.util.Map;  

/** <code>HBaseProcessor</code> provides aux processing for {@link HBaseClient}.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public interface HBaseProcessor {
  
  /** Process results after each insertion of new result.
    * @param results The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public void processResults(Map<String, Map<String, String>> results);
  
  }