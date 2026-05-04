import com.Lomikel.ElasticSearcher.ESClient;

esclient = new ESClient("http://157.136.253.253:20200"); // @CC

// set results limit
esclient.setSizeSearch(10);

// get all dia in a cone (the same for ss)
// dia_radec is 1-1 mapping
println(esclient.searchGeoPoint("dia_radec", "location", 150.009977, 0.670251, 0.001));

// get all dia within mjd range (the same for ss)
// dia-mjd is 1-n mapping
x = esclient.searchRange("dia_mjd", "mjd", 61134.99953458342, 61134.99953458342);
println(x);
// get all mjd for the first one
println(esclient.getDoubleArray("dia_mjd", x[0], "mjd"));

// get sizes of all indexes
for (idxName : ["dia_mjd", "ss_mjd", "dia_radec", "ss_radec"]) {
  println(idxName + "[" + esclient.size(idxName) + "]");
  }
  
/* interrogate via CURL:

curl -X GET 'http://157.136.253.253:20200/dia_mjd/_search?pretty=true' -H 'Content-Type: application/json' -d '{"query" : {"match_all" : {}}}'
curl -X GET 'http://157.136.253.253:20200/dia_radec/_search?pretty=true' -H 'Content-Type: application/json' -d '{"query" : {"match_all" : {}}}'

curl -X GET 'http://157.136.253.253:20200/dia_mjd/_search?pretty=true' \
  -H 'Content-Type: application/json' \
  -d '{
    "query": {
      "script": {
        "script": {
          "lang": "painless",
          "source": "doc[\"mjd\"].size() > 10"
        }
      }
    }
  }'

curl 'http://157.136.253.253:20200/_cat/indices?v'  
  
*/

