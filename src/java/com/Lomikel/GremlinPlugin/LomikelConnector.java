package com.Lomikel.GremlinPlugin;

import com.Lomikel.Januser.GremlinRecipies;
import com.Lomikel.HBaser.HBaseClient;
import com.Lomikel.Phoenixer.PhoenixProxyClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipies;
import com.astrolabsoftware.FinkBrowser.HBaser.FinkHBaseClient;

// TinkerPop
import org.apache.tinkerpop.gremlin.jsr223.AbstractGremlinPlugin;
import org.apache.tinkerpop.gremlin.jsr223.DefaultImportCustomizer;
import org.apache.tinkerpop.gremlin.jsr223.DefaultImportCustomizer.Builder;
import org.apache.tinkerpop.gremlin.jsr223.ImportCustomizer;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** Add connection to aux databases into Gremlin.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: parametrise
// TBD: put Fink into Fink
public class LomikelConnector extends AbstractGremlinPlugin {

  /** Create. */
  public LomikelConnector() {
    super("Lomikel.connector",
          imports(new Class[]{GremlinRecipies.class,
                              FinkGremlinRecipies.class,
                              PhoenixProxyClient.class,
                              HBaseClient.class,
                              FinkHBaseClient.class}),
          new LomikelConsoleCustomizer());
    }

  /** Give the instance of itself.
    * @return The {@link LomikelConnector} instance. */
  public static LomikelConnector instance() {
    return new LomikelConnector();
    }
  
  /** Create {@link ImportCustomizer}.
    * @param classes The {@link Class}es to add.
    * @return The created  {@link ImportCustomizer}. */
  // TBD: add methods
  private static final ImportCustomizer imports(Class[] classes) {
    Builder builder = DefaultImportCustomizer.build();
    for (Class cl : classes) {
      builder.addClassImports(cl);
      }
    //.addMethodImports(PhoenixProxyClient.class.getMethod("test"))
    return builder.create();
    }

  /** Logging . */
  private static Logger log = LogManager.getLogger(LomikelConnector.class);

  }
