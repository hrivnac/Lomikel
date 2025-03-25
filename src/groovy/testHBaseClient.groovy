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

// Get timeline dependece of i:dec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
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
                      false);

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

// 3D searching

client = new FinkHBaseClient("hbase-1.lal.in2p3.fr", 2183);
client.connect("ztf_cube", "string");
client.search3D("ZTF17aaaehuf", "i:jd", "i:ra,i:dec", true, true, true);
client.search3D("ZTF17aaaehuf",         "i:ra,i:dec", true, true, true);

// -----------------------------------------------------------------------------

// Curves
source = new FinkHBaseClient("hbase-1.lal.in2p3.fr", 2183);
source.connect("ztf");
client = new FinkHBaseClient("hbase-1.lal.in2p3.fr", 2183);
//client.create("Curves", new String[]{"c:0"});
client.connect("Curves", null);
client.assembleCurves(source,
                      "ZTF19aaaiwak,ZTF19aaaiwam,ZTF19aaaiwef,ZTF19aaaiwfu,ZTF18acetjlq,ZTF18abrymkr",
                      "i:jd,i:ra,i:dec",
                      "schema_0_0_1");
print(client.search3D("ZTF18acetjlq", "c:jd", "c:ra,c:dec", true, true, true));
f = {row -> return (row[0] - row[1])};
client.search3D("ZTF18abrymkr", "c:jd", "c:ra,c:dec", true, true, true).add("f", f).retain("f").plot();

                   
// LightCurves
source = new HBaseClient("hbase-1.lal.in2p3.fr", 2183);
source.connect("ztf");
client = new FinkHBaseClient("hbase-1.lal.in2p3.fr", 2183);
//client.create("LightCurves", new String[]{"c:0"});
client.connect("LightCurves", null);
client.assembleLightCurves(source, "ZTF18acetjlq");
client.search3D("ZTF18acetjlq", "c:jd", "*", true, true, true).show();
                    
// -----------------------------------------------------------------------------