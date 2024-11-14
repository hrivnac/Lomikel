import com.Lomikel.HBaser.HBaseESClient;
import com.Lomikel.ElasticSearcher.ESClient;

esclient = new ESClient("http://134.158.74.85:20200");
esclient.createIndex("radec", "location", "geo_point");
esclient.putGeoPoint("radec", "location", "point 1", 45.12, -79.34);
esclient.searchGeoPoint("radec "location", 71.8747439, 47.9747439, 0.1);
sclient.createIndex("jd_double", "jd_double", "double");
esclient.createIndex("jd_text", "jd_text", "string");
esclient.createIndex("jd_long", "jd_long", "long");

esclient.putValue("jd_double", "jd_double", "date 1", 12.3456789);
esclient.putValue("jd_text", "jd_text", "date 1", "12.3456789");
esclient.putValue("jd_long", "jd_long", "date 1", 12345678900);

esclient.searchRange("jd_double", "jd_double", 12.0, 13.0);
esclient.searchRange("jd_text", "jd_text", "12.0", "13.0");
esclient.searchRange("jd_long",   "jd_long",   12000000000, 13000000000);
