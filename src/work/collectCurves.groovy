// collect light curves for machine learning

// Lomikel
import com.Lomikel.HBaser.HBaseClient;
import com.astrolabsoftware.FinkBrowser.HBaser.FinkHBaseClient;
import com.astrolabsoftware.FinkBrowser.FinkPortalClient.FPC;

// org.json
import org.json.JSONObject;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

// Java
import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;

// -----------------------------------------------------------------------------

log = LogManager.getLogger(this.class);

nLimit = 2000;
tLimit = 200000;
fullSize = 100;
threshold = 0.75;
numClasses = 1;
selection = ["EB*":0];
//selection = ["RRLyr":0];
//selection = ["Mira":0];
//selection = ["LPV*":0];
//selection = ["V*":0];
//selection = ["Star":0];
//selection = ["Kilonova candidate":0];
//selection = ["SN candidate":0];
//selection = ["Microlensing candidate":0];

// Init files

files = [:]
indexes = [:]
for (sel : selection) {
  file = new File(sel.getKey() + ".lst");
  file.createNewFile();
  files[sel.getKey()] = file;
  index = new File(sel.getKey() + ".idx");
  index.createNewFile();
  indexes[sel.getKey()] = index;
  }

// Get oids  
  
cal = Calendar.getInstance();
cal.add(Calendar.MINUTE, - tLimit);
d = cal.getTime();
sd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);

source = new FinkHBaseClient("hbase-1.lal.in2p3.fr", 2183);
source.connect("ztf");

oids = [].toSet();
for (sel : selection) {
  ja = FPC.latests(new JSONObject().put("n",             2 * nLimit)
                                   .put("class",         sel.getKey())
                                   .put("startdate",     sd)
                                   .put("columns",       "i:objectId")
                                   .put("output-format", "json"));
  log.info(sel.getKey() + ": " + ja.length());
  for (int i = 0; i < ja.length(); i++) {
    jo = ja.getJSONObject(i);
    oids.add(jo.getString("i:objectId"));
    } 
  }                     
                      
log.info("all: " + oids.size());

// Extract curves to files

r   = {row -> return (row[2] == 1.0) ? row[1] : null};
g   = {row -> return (row[2] == 2.0) ? row[1] : null};
mjd = {row -> return row[0] - 2400000.5};

for (oid : oids) {
  client = new FinkHBaseClient("hbase-1.lal.in2p3.fr", 2183);
  client.connect("Curves", null);
  client.assembleCurves(source,
                        oid,
                        "i:jd,i:fid,i:magpsf",
                        "schema_0_0_1");
  lc = client.search3D(oid,
                       "c:jd",
                       "c:jd,c:magpsf,c:fid",
                       true,
                       true,
                       true)
             .add("r", r)
             .add("g", g)
             .add("mjd", mjd)
             .retain("mjd", "g")
             .reindex("mjd")
             .dropna();
  if (lc.length() >= 60) {
    lc = lc.slice(0, 60)
           .transpose()
           .toArray();

    ja = FPC.objects(new JSONObject().put("objectId", oid)
                                     .put("output-format", "json"));
    classes = [:];
    for (int i = 0; i < ja.length(); i++) {
      jo = ja.getJSONObject(i);
      cl = jo.getString("v:classification");
      jd = jo.getDouble("i:jd");
      if (!cl.equals("Unknown")) {
        if (classes.containsKey(cl)) {
          jds = classes[cl];
          jds += [jd];
          }
        else {
          jds = [];
          jds += [jd];
          }
        classes[cl] = jds;
        } 
      }
    classification = [:];
    sum = 0;
    classes.each{cl -> sum +=cl.getValue().size();
                       classification[cl.getKey()] = cl.getValue().size();
                       };
    classes.each{cl -> classification[cl.getKey()] /= sum};
    theClass = null;
    classes.each{cl -> if (classification[cl.getKey()] >= threshold) {
                         if (selection.containsKey(cl.getKey()) && selection[cl.getKey()] < fullSize) {
                           selection[cl.getKey()] += 1;
                           theClass = cl.getKey();
                           }
                         else if (selection.size() < numClasses) {
                           selection[cl.getKey()] = 1;
                           theClass = cl.getKey();
                           }
                         }
                       }
    if (theClass != null && lc.length == 60) {
      line = "";
      for (l : lc) {
        line += l.toString() + " ";
        }
      line += "\n";
      files[theClass].append(line);
      indexes[theClass].append(oid + "\n");
      full = true;
      selection.each{cl -> if (cl.getValue() < fullSize) {
                             full = false;
                             }
                           }
      log.info(oid + ": " + selection);
      if (full) {
        break;
        }
      }
    }
  }
  
// -----------------------------------------------------------------------------
