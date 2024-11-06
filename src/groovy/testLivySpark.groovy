import com.Lomikel.Livyser.Server;
import com.Lomikel.Livyser.Action;
import com.Lomikel.Livyser.Language;
import org.json.JSONObject;

serverName     = "LAL";
urlLivy        = "http://134.158.75.222:8020";
urlSpark       = "http://134.158.75.222:21111";
urlHBase       = "http://hbase-1.lal.in2p3.fr:2183";
fileName       = "test.py";
className      = null;
args           = "";
driverMemory   = null;
driverCores    = 0;
executorMemory = null;
executorCores  = 0;
numExecutors   = 0;
jars           = null;
pyFiles        = null;
files          = null;
archives       = null;
queue          = null;
jobName        = null;
conf           = null;
proxyUser      = null;
tries          = 10;
sleep          = 1

server = new Server(serverName, urlLivy, urlSpark, urlHBase);

action = new Action("Pokus", "1+1", Language.PYTHON);

result = server.executeAction(action);

print(result);

/*
id = server.livy().sendJob(fileName,sendJ
                           className,
                           args,
                           driverMemory,
                           driverCores,
                           executorMemory,
                           executorCores,
                           numExecutors,
                           jars,
                           pyFiles,
                           files,
                           archives,
                           queue,
                           jobName,
                           conf,
                           proxyUser,
                           Integer.MAX_VALUE,
                           1);
   
print(id);
while (true) {
  resultString = server.livy().checkBatchProgress(id, 10, 1);
  if (resultString != null) {
    result = new JSONObject(resultString);
    statex = result.getString("state");
    if (statex0 == null) {
      statex0 = statex;
      print("State: " + statex + "<br/>");
      }
    if (!statex.equals(statex0)) {
      print("State: " + statex + "<br/>");
      statex0 = statex;
      }
    if (statex.equals("success") || statex.equals("dead")) {
      break;
      }
    }
  Thread.sleep(1000);
  }
logArray = result.getJSONArray("log");    
String fullLog = "";
for (Object logEntry : logArray) {
  fullLog += logEntry.toString() + "\n";
  }
resultString = server.livy().getBatchLog(id, 10, 1);
result = new JSONObject(resultString);
logArray = result.getJSONArray("log");
*/
