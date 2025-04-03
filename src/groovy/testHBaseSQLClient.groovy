import com.Lomikel.HBaser.HBaseSQLClient;

client = new HBaseSQLClient("vdhbase1.lal.in2p3.fr", 2183);
client.connect("ztf", "schema_3.1_5.2.0");
sql = client.sqlViewCreationCommand();

// Create Phoenix view of the HBase table
//   should be executed in sqlline.py
client.sqlViewCreationCommand();
// Use SQL query to scan it
// BUG: fails
results = client.scan("select ROWKEY,\"candid\",\"ra\",\"xpos\" from \"ztf\" limit 1", true);
print(results); 

// Create Phoenix table for the HBase table
//   a table per schema 
//   should be executed in sqlline.py
client.sqlTableCreationCommand();
// Replicate rows into Phoenix table
// BUG: fails
client.scan2SQL(null,
               "key:key:ZTF:prefix",
               "*",
               0,
               0,
               true,
               true,
               "ZTF_SCHEMA_3__1_5__2__0");

client.close();
