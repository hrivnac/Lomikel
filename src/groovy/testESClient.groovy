import com.Lomikel.ElasticSearcher.ESClient;

esclient = new ESClient("http://127.0.0.1:20200");
//esclient.setSize(20);

//esclient.createIndex("radec", "location", "geo_point");
//esclient.createIndex("jd_double", "jd_double", "double");
//esclient.createIndex("jd_text", "jd_text", "text");
//esclient.createIndex("jd_long", "jd_long", "long");

//esclient.putGeoPoint("radec", "location", "point 2", 45.12, 79.34);
//esclient.commit();
//esclient.putValue("jd_double", "jd_double", "date 1", 12.3456789);
//esclient.putValue("jd_string", "jd_string", "date 1", "12.3456789");
//esclient.putValue("jd_nano", "jd_nano", "date 1", 12345678900);

println(esclient.searchGeoPoint("radec", "location", 45.12, 79.34, 0.00001));
println(esclient.searchRange("jd_double", "jd_double", 2460970.82, 2460970.88));
println(esclient.searchRange("jd_text", "jd_text", "2460970.82", "2460970.88"));
println(esclient.searchRange("jd_long", "jd_long", 2460970820000000, 2460970880000000));
