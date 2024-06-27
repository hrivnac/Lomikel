import com.Lomikel.HBaser.HBaseClient;
import com.astrolabsoftware.FinkBrowser.HBaser.FinkHBaseClient;

// Open HBase database (ip, port)
client = new HBaseClient("hbase-1.lal.in2p3.fr", 2183);
// Connect to table with defined schema
client.connect("ztf", "schema_2.2_2.0.0");
// Set maximum number of results
client.setLimit(5);

// Get the loaded schema as family:column=type,...
result = client.schema();
print(result);
// Get two columns for two known alerts (specified by their keys) 
// Result: Map<String, Map<String, String>>
results = client.scan("ZTF17aaaaaal_2458860.665162,ZTF17aaaaaal_2458899.6333102",
                      null,
                      "i:candid,b:cutoutScience_stampData",
                      0,
                      false,
                      false);
print(client.results2String(results)); 

// Get binary cell received in previous command
print(client.repository().get("binary:ZTF17aaaaaal_2458860.665162:cutoutScience_stampData"));

// Get all alerts with key containing 'xyd' or starting with 'ZTF19aaaiw'
// Available comparators: exact, prefix (default), substring, regex
results = client.scan(null,
                      "key:key:xyd:substring,key:key:ZTF19aaaiw:prefix",
                      "i:candid",
                      0,
                      true,
                      true);
print(client.results2String(results)); 

// Get all alerts with column match
// It only works for string columns
// May use Evaluator for numerical columns
// Available comparators: exact, prefix, substring (default), regex
results = client.scan(null,
                      "d:cdsxmatch:YSO:exact",
                      "i:candid,d:cdsxmatch",
                      0,
                      true,
                      true);
print(client.results2String(results)); 

// Get timeline dependece of i:dec
results = client.timeline("i:dec", null);
print(results); 

// Get all recent (last 100000 minutes) objectIds
results = client.latests("i:objectId",
                         null,
                         100000,
                         true);
print(results); 

// Apply predefined function to filter resultsimport com.Lomikel.HBaser.AsynchHBaseClient;

client.setEvaluation("isWithinGeoLimits(80, 85, -4.0, 0.0)", "ra,dec");
results = client.scan(null,
                      null,
                      null,
                      0,
                      false,
                      false);
print(client.results2String(results)); 

// Apply formula to filter results
client.setEvaluation("dec < 55");
results = client.scan(null,
                      null,
                      null,
                      0,
                      false,
                      false);import com.Lomikel.HBaser.AsynchHBaseClient;

print(client.results2String(results)); 

client.close();

// -----------------------------------------------------------------------------

// Get all schemas

client1 = new HBaseClient("hbase-1.lal.in2p3.fr", 2183);
client1.connect("ztf", null);
results = client1.scan(null,
                       "key:key:schema:prefix",
                       null,
                       0,
                       0,
                       false,
                       false);
print(client1.results2String(results)); 

client1.close();

// -----------------------------------------------------------------------------

// create HBase client put method

client2 = new HBaseClient("hbase-1.lal.in2p3.fr", 2183);

client2.create("test1", new String[]{"i", "b", "d", "a"});

client2.connect("test1", null);
client2.put("schema_a", new String[]{"i:one:string", "i:two:float", "i:three:double", "i:four:short", "i:five:integer", "i:six:long", "b:seven:fits/image", "b:eight:fits", "b:nine:binary"});

client2.connect("test1", "schema_a");
client2.put("row_a_1", new String[]{"i:one:abcdef", "i:two:1.2345", "i:three:1.23456789", "i:four:5", "i:five:555", "i:six:555555555"});
results = client2.scan("row_a_1",
                        null,
                        "*",
                        0,
                        false,
                        false);
                      
print(client2.results2String(results)); 

client2.close()

// -----------------------------------------------------------------------------


client = new FinkHBaseClient("hbase-1.lal.in2p3.fr", 2183);
client.connect("ztf_cube", "string");
client.search2("ZTF17aaaehuf", "i:jd", "i:ra,i:dec", true);
client.search2("ZTF17aaaehuf", "i:ra,i:dec", true);

// -----------------------------------------------------------------------------
