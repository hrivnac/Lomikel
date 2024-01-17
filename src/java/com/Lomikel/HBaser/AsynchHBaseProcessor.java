package com.Lomikel.HBaser;

import com.Lomikel.Utils.LomikelException;

// HBase
import org.apache.hadoop.hbase.client.Scan;

// HealPix
import static cds.healpix.VerticesAndPathComputer.LON_INDEX;
import static cds.healpix.VerticesAndPathComputer.LAT_INDEX;

// Java
import java.util.List;  
import java.util.ArrayList;  
import java.util.Map;  
import java.util.concurrent.ConcurrentLinkedQueue;

// Log4J
import org.apache.log4j.Logger;

/** <code>AsynchHBaseProcessor</code> implements {@link HBaseProcessor} for {@link AsynchHBaseClient}. 
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class AsynchHBaseProcessor implements HBaseProcessor {
    
  public AsynchHBaseProcessor(ConcurrentLinkedQueue<Map<String, String>> queue) {
    _queue = queue;
    }
  
  /** Add results into {@link ConcurrentLinkedQueue}
    * and clean the {@link Map}. */  
  @Override
  public void processResults(Map<String, Map<String, String>> results) {
    boolean isSchema = false; // BUG: in other subclasses of HBaseClient ?
    for (Map.Entry<String, Map<String, String>> entry : results.entrySet()) {
      if (entry.getKey().startsWith("schema")) {
        isSchema = true;
        break;
        }
      _queue.add(entry.getValue());
      }
    if (isSchema) {
      isSchema = false;
      }
    else {
      results.clear();
      }
    }

  private ConcurrentLinkedQueue<Map<String, String>> _queue = new ConcurrentLinkedQueue<>();
  
  /** Logging . */
  private static Logger log = Logger.getLogger(AsynchHBaseProcessor.class);

  }
