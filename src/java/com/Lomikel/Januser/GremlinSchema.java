package com.Lomikel.Januser;

import com.Lomikel.DB.Schema;
import com.Lomikel.DB.CellContent;
import com.Lomikel.Utils.ByteArray;

// HBase
import org.apache.hadoop.hbase.util.Bytes;

// JanusGraph
import org.janusgraph.graphdb.database.StandardJanusGraph;
import org.janusgraph.core.schema.JanusGraphManagement;
import org.janusgraph.core.PropertyKey;
import org.janusgraph.core.Cardinality;
import org.janusgraph.graphdb.database.management.ManagementSystem;

// Java
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

// Log4J
import org.apache.log4j.Logger;

/** <code>Schema</code>handles <em>Greamlin</em> types coding/decoding.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: handle all types
public class GremlinSchema extends Schema<String> {
  
  /** Set overall schema.
    * @param schemaName The name of the schema to set.
    * @param graph      The {@link StandardJanusGraph} holding the schema. */
  public GremlinSchema(String             schemaName,
                       StandardJanusGraph graph) {  
    super(schemaName, new HashMap<String, String>());
    ManagementSystem system = (ManagementSystem)graph.openManagement(); // the only implemeting class // TBD: check
    Iterable<PropertyKey> keys = system.getRelationTypes(PropertyKey.class);
    for (PropertyKey key: keys) {
      map().put(key.name(), key.dataType().getName() + (key.cardinality() == Cardinality.SINGLE ? "" : (":" + key.cardinality().toString())));
      }  
    }

  @Override
  public String decode(String column,
                       String encodedValue) {
    return encodedValue;
    }
    
  @Override
  public CellContent decode2Content(String column,
                                    String encodedValue) {
    return new CellContent(encodedValue);
    }
    
  @Override
  public String encode(String column,
                       String decodedValue) {
    return decodedValue;
    }
        
  @Override
  public String toString() {
    return "Gremlin" + super.toString();
    }

  /** Logging . */
  private static Logger log = Logger.getLogger(GremlinSchema.class);

  }
