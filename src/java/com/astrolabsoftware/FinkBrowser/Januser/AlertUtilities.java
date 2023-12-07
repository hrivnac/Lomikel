package com.astrolabsoftware.FinkBrowser.Januser;

// Lomikel
import com.Lomikel.Januser.JanusClient;

// Tinker Pop
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;
import static org.apache.tinkerpop.gremlin.process.traversal.P.*;
import org.apache.tinkerpop.gremlin.structure.Vertex;

// Java
import java.util.List;

// Log4J
import org.apache.log4j.Logger;

/** <code>AlertUtilities</code> provides utility searches for alerts.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class AlertUtilities extends JanusClient {

  /** TBD */
  public static void main(String[] args) {
    AlertUtilities au = new AlertUtilities(args[0]);
    List<Vertex> r = au.searchJd(new Double(args[1]), new Double(args[2]), args[3], new Integer(args[4]));
    log.info(r);
    au.close();
    }
   
  /** Create with connection parameters.
    * @param hostname The HBase hostname.
    * @param table    The HBase table. */
  public AlertUtilities(String hostname,
                        String table) {
    super(hostname, table, false);
    }
   
  /** Create with connection parameters.
    * @param hostname The HBase hostname.
    * @param table    The HBase table.
    * @param batch    Whether open graph for batch loading. */
  public AlertUtilities(String  hostname,
                        String  table,
                        boolean batch) {
    super(hostname, table, batch);
    }
    
  /** Create with connection properties file.
    * @param properties The file with the complete properties. */
  public AlertUtilities(String properties) {
    super(properties);
    }

  /** Search {@link Vertex}es between <em>Julian Dates</em>.
    * @param since The end <em>Julian Date</em>.
    * @param till  The start <em>Julian Date</em>.
    * @param lbl   The {@link Vertex} label.
    * @param limit The maximal number of resuolts to give.
    * @return      The {@link List} of {@link Vertex}es.
    */
  public List<Vertex> searchJd(double since,
                               double till,
                               String lbl,
                               int    limit) {
    log.info("Searching " + lbl + " within " + since + " - " + till + ", limit = " + limit);
    if (limit <= 0) {
      log.info(g().V().has("jd", inside(since, till)).has("lbl", lbl).count());
      return null;
      }
    return g().V().has("jd", inside(since, till)).has("lbl", lbl).limit(limit).toList();
    }
    
  /** Logging . */
  private static Logger log = Logger.getLogger(AlertUtilities.class);

  }
