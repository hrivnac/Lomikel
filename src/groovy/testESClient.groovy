import com.Lomikel.HBaser.HBaseESClient;
import com.Lomikel.ElasticSearcher.ESClient;

esclient = new ESClient("http://134.158.74.85:20200");

esclient.createGeoPointIndex("radec", "location");
//esclient.createIndex("radec", "location", "geo_point");
esclient.putGeoPoint("radec", "location", "point 1", 45.12, -79.34);
esclient.searchGeoPoint("radec", "location", 71.8747439, 47.9747439, 0.1);

esclient.createDoubleIndex("jd", "date");
//esclient.createIndex("jd", "date", "double");
esclient.putDouble("jd", "date", "date 1", 12.3456789);
esclient.searchDouble("jd", "date", 12.0, 13.0);

client = new HBaseESClient("hbase-1.lal.in2p3.fr", 2183);

client.connect("ztf", "schema_2.2_2.0.0");
client.setLimit(10);
client.connectElasticSearch("http://134.158.74.85:20200");
client.scan(null,
            "key:key:ZTF17aaaaaa:prefix,d:cdsxmatch:YSO:exact",
            "i:candid,i:ra,i:dec,i:jd",
            0,
            true,
            true);

