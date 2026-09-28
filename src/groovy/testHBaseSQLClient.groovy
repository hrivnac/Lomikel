import com.Lomikel.HBaser.HBaseSQLClient;

client = new HBaseSQLClient("vdhbase1.lal.in2p3.fr", 2183);
client.connect("ztf", "schema_3.1_5.2.0");
sql = client.sqlViewCreationCommand();

client.close();
