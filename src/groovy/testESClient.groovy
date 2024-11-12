mport com.Lomikel.HBaser.HBaseESClient;
import com.Lomikel.ElasticSearcher.ESClient;

esclient = new ESClient("http://134.158.74.85:20200");

//esclient.createIndex("radec", "location", "geo_point");
//esclient.putGeoPoint("radec", "location", "point 1", 45.12, -79.34);
//esclient.searchGeoPoint("radec", "location", 71.8747439, 47.9747439, 0.1);

//esclient.createIndex("jd_double", "jd_double", "double");
//esclient.putValue("jd_double", "jd_double", "date 1", 12.3456789);
//esclient.createIndex("jd_string", "jd_string", "string");
//esclient.putValue("jd_string", "jd_string", "date 1", "12.3456789");
//esclient.createIndex("jd_nano", "jd_nano", "date_nano");
//esclient.putValue("jd_nano", "jd_nano", "date 1", 12345678900);
esclient.searchRange("jd_double", "jd_double", 12.0, 13.0);
esclient.searchRange("jd_string", "jd_string", "12.0", "13.0");
esclient.searchRange("jd_nano",   "jd_nano",   12000000000, 13000000000);

client = new HBaseESClient("hbase-1.lal.in2p3.fr", 2183);

client.connect("ztf", "schema_2.2_2.0.0");
client.setLimit(100);
client.connectElasticSearch("http://134.158.74.85:20200");
client.scan(null,
            "key:key:ZTF17aaaaaa:prefix,d:cdsxmatch:YSO:exact",
            "i:candid,i:ra,i:dec,i:jd",
            0,
            true,
            true);

