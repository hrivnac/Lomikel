package com.Lomikel.HBaser;

// HealPix
import static cds.healpix.VerticesAndPathComputer.LON_INDEX;
import static cds.healpix.VerticesAndPathComputer.LAT_INDEX;

// Java
import java.util.Map;  
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>AsynchHBaseProcessor</code> implements {@link HBaseProcessor} for {@link AsynchHBaseClient}. 
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class AsynchHBaseProcessor implements HBaseProcessor {
    
  /** Create and assigne {@link ConcurrentLinkedQueue}.
    * @param queue The {@link ConcurrentLinkedQueue} to be assigned. */
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
      try {
        while (_queue.size() > _maxsize) {
          log.info("queue size = " + _queue.size() + " > " + _maxsize);
          TimeUnit.SECONDS.sleep(_waitSeconds);
          }
        }
      catch (InterruptedException e) {
        log.warn("Processing pause interruped");
        }
      }
    if (isSchema) {
      isSchema = false;
      }
    else {
      results.clear();
      }
    }
    
  /** Set maximum size of the queue.
    * Queue accumlation will stop till its size goes bellow this limit.
    * @param maxsize The maximum size of the queue. DEfault is <tt>1000</tt>. */
  public void setMaxQueueSize(int maxsize) {
    _maxsize = maxsize;
    }

  private ConcurrentLinkedQueue<Map<String, String>> _queue;
  
  private int _maxsize = 1000;
  
  private int _waitSeconds = 1;
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(AsynchHBaseProcessor.class);

  }
