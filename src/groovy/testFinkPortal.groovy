// https://api.fink-portal.org/api

import com.astrolabsoftware.FinkBrowser.FinkPortalClient.FPC;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;

FINK_OBJECTS_WS = "https://api.fink-portal.org/api/v1/objects";
FINK_LATESTS_WS = "https://api.fink-portal.org/api/v1/latests";
FINK_ANOMALY_WS = "https://api.fink-portal.org/api/v1/anomaly";

cal = Calendar.getInstance();
cal.add(Calendar.HOUR, -1);
d = cal.getTime();
sd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);

request = new JSONObject().put("objectId",      "ZTF17aaaehup").
                           put("output-format", "json");
ja = FPC.objects(request);
print(ja);

request = new JSONObject().put("class",         "Star").
                           put("n",             "1000").
                           put("columns",       "i:objectId").
                           put("startdate",     sd).
                           put("output-format", "json");
ja = FPC.latests(request);
print(ja);

request = new JSONObject().put("n",             "1000").
                           put("output-format", "json").
                           put("startdate",     sd).
                           put("columns",       "i:objectId");
ja = FPC.anomaly(request);
print(ja);
