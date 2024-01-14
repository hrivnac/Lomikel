package com.astrolabsoftware.FinkBrowser.HBaser;

import com.Lomikel.Utils.Init;
import com.Lomikel.Utils.DateTimeManagement;
import com.Lomikel.Utils.MapUtil;
import com.Lomikel.Utils.Pair;
import com.Lomikel.Utils.LomikelException;
import com.Lomikel.DB.Schema;
import com.Lomikel.DB.Client;
import com.Lomikel.DB.SearchMap;
import com.Lomikel.ElasticSearcher.ESClient;
import com.Lomikel.HBaser.HBaseClient;

// HBase
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName ;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.filter.Filter;  
import org.apache.hadoop.hbase.filter.FilterList;  
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;  
import org.apache.hadoop.hbase.filter.RandomRowFilter;  
import org.apache.hadoop.hbase.filter.RowFilter;  
import org.apache.hadoop.hbase.filter.PrefixFilter;  
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp;
import org.apache.hadoop.hbase.filter.BinaryPrefixComparator;
import org.apache.hadoop.hbase.filter.BinaryComparator;
import org.apache.hadoop.hbase.filter.RegexStringComparator;
import org.apache.hadoop.hbase.filter.SubstringComparator;
import org.apache.hadoop.hbase.filter.MultiRowRangeFilter;
import org.apache.hadoop.hbase.filter.MultiRowRangeFilter.RowRange;

// Hadoop
import org.apache.hadoop.conf.Configuration;

// HealPix
import static cds.healpix.VerticesAndPathComputer.LON_INDEX;
import static cds.healpix.VerticesAndPathComputer.LAT_INDEX;

// Java
import java.util.List;  
import java.util.ArrayList;  
import java.util.Set;
import java.util.SortedSet;  
import java.util.TreeSet;  
import java.util.Map;  
import java.util.HashMap;  
import java.util.TreeMap;  
import java.util.NavigableMap;
import java.io.IOException;
import java.util.Date;
import java.util.Arrays;

// Log4J
import org.apache.log4j.Logger;

/** <code>FinkHBaseESClient</code> connects to HBase and updates {@link ESClient}. 
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class FinkHBaseESClient extends HBaseClient {
   
  // Lifecycle -----------------------------------------------------------------
  
  /** Create and connect to HBase.
    * @param zookeepers The comma-separated list of zookeper ids.
    * @param clientPort The client port. 
    * @throws LomikelException If anything goes wrong. */
  public FinkHBaseESClient(String zookeepers,
                           String clientPort) throws LomikelException {
    super(zookeepers, clientPort);
    }
        
  /** Create and connect to HBase.
    * @param zookeepers The comma-separated list of zookeper ids.
    * @param clientPort The client port. 
    * @throws LomikelException If anything goes wrong. */
  public FinkHBaseESClient(String zookeepers,
                           int    clientPort) throws LomikelException {
    super(zookeepers, clientPort);
    }
    
  /** Create and connect to HBase.
    * @param url The HBase url.
    * @throws LomikelException If anything goes wrong. */
  public FinkHBaseESClient(String url) throws LomikelException {
    super(url);
    }
    
  public void connectElasticSearch(String url,
                                   String idxName) {
    _esclient = new ESClient(url);
    _idxName = idxName;
    }
    
  /** Register {@link Result} into {@link ESClient} */
  @Override
  protected boolean addResult(Result              r,
                              Map<String, String> result,
                              String              filter,
                              boolean             ifkey,
                              boolean             iftime) {
    if (super.addResult(r, result, filter, ifkey, iftime)) {
      String key = Bytes.toString(r.getRow());
      if (!key.startsWith("schema")) {     
        try {
          _esclient.putGeoPoint(_idxName,
                                key,
                                Double.valueOf(result.get("i:ra")),
                                Double.valueOf(result.get("i:dec")));
          }
        catch (LomikelException e) {
          log.error("Cannot add result " + r, e);
          }
        }
      }
    return false;
    }
    
  private String _idxName;
    
  private ESClient _esclient;
  
  /** Logging . */
  private static Logger log = Logger.getLogger(FinkHBaseESClient.class);

  }
