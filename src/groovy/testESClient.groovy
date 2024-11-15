import com.Lomikel.ElasticSearcher.ESClient;

esclient = new ESClient("http://134.158.74.85:20200");
//esclient.setSize(20);

//esclient.createIndex("radec", "location", "geo_point");
//esclient.createIndex("jd_double", "jd_double", "double");
//esclient.createIndex("jd_text", "jd_text", "text");
//esclient.createIndex("jd_long", "jd_long", "long");

//esclient.putGeoPoint("radec", "location", "point 1", 45.12, -79.34);
//esclient.putValue("jd_double", "jd_double", "date 1", 12.3456789);
//esclient.putValue("jd_string", "jd_string", "date 1", "12.3456789");
//esclient.putValue("jd_nano", "jd_nano", "date 1", 12345678900);

println(esclient.searchGeoPoint("radec", "location", 59.89, 50.27, 0.1));
println(esclient.searchRange("jd_double", "jd_double", 2460143.0, 2460144.0));
println(esclient.searchRange("jd_text", "jd_text", "2460143.0", "2460144.0"));
println(esclient.searchRange("jd_long", "jd_long", 2460143000000000, 2460144000000000));

