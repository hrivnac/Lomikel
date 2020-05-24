package com.JHTools.WebService;

// Java
import java.util.Map;

/** <code>HBaseColumnsProcessor</code> extracts X-axes from rows for graphs.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: use timestamp
public interface HBaseColumnsProcessor {
  
  /** Give the value of the X-axes corresponding to one table row.
    * @param entry0 One row of the table.
    * @return       The derived value of x-axes. */
  public String getX(Map.Entry<String, Map<String, String>> entry0);
  
  /** Give the date corresponding to one table row.
    * @param entry0 One row of the table.
    * @return       The date (may not correspond to the row timestamp). */
  public String getXDate(Map.Entry<String, Map<String, String>> entry0);

  }
