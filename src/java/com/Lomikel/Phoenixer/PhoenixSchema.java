package com.Lomikel.Phoenixer;

import com.Lomikel.DB.Schema;
import com.Lomikel.DB.CellContent;

// Java
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Arrays;

// Log4J
import org.apache.log4j.Logger;

/** <code>Schema</code>handles <em>Phoenix</em> schema and types coding/decoding.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: handle all types
public class PhoenixSchema extends Schema<String> {
  
  /** Set overall schema.
    * @param schemaName The name of the schema to set.
    * @param schemaMap  The schema to set.
    * @param rowkeys    The rowkeys to idetify rows. */
  public PhoenixSchema(String              schemaName,
                       Map<String, String> schemaMap,
                       String[]            rowkeys) {
    super(schemaName, schemaMap);   
    _rowkeys = rowkeys;
    }
  
  /** Set overall schema.
    * @param schemaName The name of the schema to set.
    * @param schemaMap  The schema to set.
    * @param reMap      The renaming of attributes.
    * @param rowkeys    The rowkeys to idetify rows. */
  public PhoenixSchema(String              schemaName,
                       Map<String, String> schemaMap,
                       Map<String, String> reMap,
                       String[]            rowkeys) {
    super(schemaName, schemaMap, reMap);
    _rowkeys = rowkeys;
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
        
  /** Get registered {@link PhoenixSchema}.
    * @param schemaName The name of the requested {@link PhoenixSchema}.
    * @return The registered {@link PhoenixSchema}. */
  public static PhoenixSchema getSchema(String schemaName) {
    return _schemas.get(schemaName);
    }
    
  /** Register named {@link PhoenixSchema}.
    * @param schemaName The name of the {@link PhoenixSchema} to register. 
    * @param schemaName The {@link PhoenixSchema} to register. 
    * @param rowkeys    The rowkeys to idetify rows. */
  public static void addSchema(String              schemaName,
                               Map<String, String> schemaMap,
                               String[]            rowkeys) {
    _schemas.put(schemaName, new PhoenixSchema(schemaName, schemaMap, rowkeys));
    }
    
  /** Give row identifying key names.
    * @return The row identifying key names. */
 public String[] rowkeyNames() {
   return _rowkeys;
   }
    
  @Override
  public String toString() {
    return "Phoenix" + super.toString() + "\n\trowkeys: " + Arrays.toString(_rowkeys);
    }
  
  private String[] _rowkeys;
  
  private static Map<String, PhoenixSchema> _schemas = new HashMap<>();

  /** Logging . */
  private static Logger log = Logger.getLogger(PhoenixSchema.class);
    
  }
