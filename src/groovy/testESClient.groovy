import com.Lomikel.HBaser.HBaseESClient;
import com.Lomikel.ElasticSearcher.ESClient;

esclient = new ESClient("http://134.158.74.85:20200");

esclient.createIndex("radec", "location", "geo_point");
esclient.putGeoPoint("radec", "location", "point 1", 45.12, -79.34);
esclient.searchGeoPoint("radec", "location", 71.8747439, 47.9747439, 0.1);

esclient.createIndex("jd_double", "jd_double", "double");
esclient.createIndex("jd_string", "jd_string", "string");
esclient.createIndex("jd_nano", "jd_nano", "date_nano");

esclient.putValue("jd_double", "jd_double", "date 1", 12.3456789);
esclient.putValue("jd_string", "jd_string", "date 1", "12.3456789");
esclient.putValue("jd_nano", "jd_nano", "date 1", 12345678900);

esclient.searchRange("jd_double", "jd_double", 12.0, 13.0);
esclient.searchRange("jd_string", "jd_string", "12.0", "13.0");
esclient.searchRange("jd_nano",   "jd_nano",   12000000000, 13000000000);
