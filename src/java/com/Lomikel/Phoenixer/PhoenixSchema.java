package com.Lomikel.Phoenixer;

import com.Lomikel.DB.Schema;
import com.Lomikel.DB.CellContent;

// HBase
import org.apache.hadoop.hbase.util.Bytes;

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
  
  // TBD:move to Atlascope
  // TBD:make rowkey part of schema also in HBase
  static {
    log.info("Setting know Schemas");
    Map<String, String> schema = new HashMap<>();
    schema.put("runnumber"         , "Integer");
    schema.put("project"           , "String" );
    schema.put("streamname"        , "String" );
    schema.put("prodstep"          , "String" );
    schema.put("datatype"          , "String" );
    schema.put("version"           , "String" );
    schema.put("dspid"             , "Integer"); 
    schema.put("state"             , "String" ); 
    schema.put("state_modification", "Date"   );
    schema.put("state_details"     , "String" );
    schema.put("insert_start"      , "Date"   );
    schema.put("backup_end"        , "Date"   );
    schema.put("backup_start"      , "Date"   );
    schema.put("insert_end"        , "Date"   );
    schema.put("source_path"       , "String" );
    schema.put("validated"         , "Long"   );
    schema.put("count_events"      , "Long"   );
    schema.put("uniq_dupl_events"  , "Long"   );
    schema.put("num_duplicates"    , "Long"   );
    schema.put("trigger_counted"   , "Integer");
    schema.put("ds_overlaps"       , "Integer");
    schema.put("ami_count"         , "Long"   );
    schema.put("ami_raw_count"     , "Long"   );
    schema.put("ami_date"          , "Date"   );
    schema.put("ami_upd_date"      , "Date"   );
    schema.put("ami_state"         , "String" );
    schema.put("inconctainer"      , "Integer");
    schema.put("smk"               , "Integer");
    addSchema("aeidev.datasets", schema, new String[]{"runnumber",
                                                      "project",
                                                      "streamname",
                                                      "prodstep",
                                                      "datatype",
                                                      "version",
                                                      "dspid"});
    schema = new HashMap<>();
    schema.put("runnumber"         , "Integer");
    schema.put("project"           , "String" );
    schema.put("streamname"        , "String" );
    schema.put("prodstep"          , "String" );
    schema.put("datatype"          , "String" );
    schema.put("version"           , "String" );
    schema.put("dspid"             , "Integer"); 
    schema.put("insert_start"      , "Date"   );
    schema.put("insert_end"        , "Date"   );
    schema.put("backup_start"      , "Date"   );
    schema.put("backup_end"        , "Date"   );
    schema.put("validated"         , "Long"   );
    schema.put("count_events"      , "Long"   );
    schema.put("uniq_dupl_events"  , "Long"   );
    schema.put("num_duplicates"    , "Integer");
    schema.put("tigger_counted"    , "Integer");
    schema.put("ds_overlaps"       , "Long"   );
    schema.put("ami_count"         , "Long"   );
    schema.put("ami_raw_count"     , "Long"   );
    schema.put("ami_date"          , "Date"   );
    schema.put("ami_upd_date"      , "String" );
    schema.put("ami_state"         , "Integer");
    schema.put("inconctainer"      , "String" );
    schema.put("state"             , "String" );
    schema.put("smk"               , "Integer");
    addSchema("aei.sdatasets", schema, new String[]{"runnumber",
                                                    "project",
                                                    "streamname",
                                                    "prodstep",
                                                    "datatype",
                                                    "version",
                                                    "dspid"});
    schema =  new HashMap<>();
    schema.put("dspid"             , "Integer");
    schema.put("dstypeid"          , "Short"  );
    schema.put("dssubtypeid"       , "Short"  );
    schema.put("eventno"           , "Long"   );
    schema.put("seq"               , "Short"  );
    schema.put("tid"               , "Integer");
    schema.put("sr"                , "String" ); // binary(24)
    schema.put("pv"                , "String" ); // binary(24) array
    schema.put("lb"                , "Integer");
    schema.put("bcid"              , "Integer");
    schema.put("lpsk"              , "Integer");
    schema.put("etime"             , "Date"   );
    schema.put("id"                , "Long"   );
    schema.put("tbp"               , "Short"  ); // array
    schema.put("tap"               , "Short"  ); // array
    schema.put("tav"               , "Short"  ); // array
    schema.put("lb2"               , "Integer");
    schema.put("bcid2"             , "Integer");
    schema.put("hpsk"              , "Integer");
    schema.put("ph"                , "Short"  ); // array 
    schema.put("pt"                , "Short"  ); // array
    schema.put("rs"                , "Short"  ); // array
    addSchema("aeidev.events", schema, new String[]{"dspid",
                                                    "dstypeid",
                                                    "dssubtypeid",
                                                    "eventno",
                                                    "seq"});
    schema = new HashMap<>();
    schema.put("dspid"             , "Integer");
    schema.put("eventnumber"       , "Long"   );
    schema.put("hltpsk"            , "Integer");
    schema.put("l1psk"             , "Integer");
    schema.put("lumiblocknr"       , "Integer");
    schema.put("bunchid"           , "Integer");
    schema.put("eventtime"         , "Integer");
    schema.put("eventtimens"       , "Integer");
    schema.put("lvl1id"            , "Long"   );
    schema.put("l1trigmask"        , "String" );
    schema.put("l1trigchainstav"   , "String" );
    schema.put("l1trigchainstap"   , "String" );
    schema.put("l1trigchainstbp"   , "String" );
    schema.put("eftrigmask"        , "String" );
    schema.put("eftrigchainsph"    , "String" );
    schema.put("eftrigchainspt"    , "String" );
    schema.put("eftrigchainsrs"    , "String" );
    schema.put("dbraw"             , "String" );
    schema.put("tkraw"             , "String" );
    schema.put("dbesd"             , "String" );
    schema.put("tkesd"             , "String" );
    schema.put("dbaod"             , "String" );
    schema.put("tkaod"             , "String" );
    schema.put("db"                , "String" );
    schema.put("tk"                , "String" );
    addSchema("aei.sevents", schema, new String[]{"dspid",
                                                  "eventnumber"});
    }
  
  }
