import com.Lomikel.HBaser.HBaseClient;
import com.astrolabsoftware.FinkBrowser.HBaser.FinkHBaseClient;

source = new HBaseClient("hbase-1.lal.in2p3.fr", 2183);
source.connect("ztf");
source.setLimit(1000);
oids = source.latests("i:objectId",
                      null,
                      1000,
                      true);
oids = oids.toArray().join(",");

source = new HBaseClient("hbase-1.lal.in2p3.fr", 2183);
source.connect("ztf");
source.setLimit(Integer.MAX_VALUE);
client = new FinkHBaseClient("hbase-1.lal.in2p3.fr", 2183);
client.connect("LightCurves", null);
client.assembleLightCurves(source, oids);

source = new HBaseClient("hbase-1.lal.in2p3.fr", 2183);
source.connect("ztf");
source.setLimit(Integer.MAX_VALUE);
client = new FinkHBaseClient("hbase-1.lal.in2p3.fr", 2183);
client.connect("Curves", null);
client.assembleCurves(source,
                      oids,
                      "i:jd,i:ra,i:dec",
                      "schema_0_0_1");
                      
source = new HBaseClient("hbase-1.lal.in2p3.fr", 2183);
source.connect("ztf.upper");