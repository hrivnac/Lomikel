import com.Lomikel.Livyser.Server;
import com.Lomikel.Livyser.Action;
import com.Lomikel.Livyser.Language;
import org.json.JSONObject;

FINK_HOME      = '/home/janusgraph/fink-broker';

serverName     = 'LAL';
urlLivy        = 'http://157.136.254.149:21111';
urlSpark       = 'http://157.136.254.149:8020';
//urlHBase       = 'http://vdhbase1.lal.in2p3.fr:2183';
urlHBase       = null;
fileName       = '/user/julius.hrivnac/PCA.py';
className      = null;
args           = null;
driverMemory   = null;
driverCores    = 0;
executorMemory = null;
executorCores  = 0;
numExecutors   = 0;
//jars           = '["hdfs:///user/julius.hrivnac/hbase-spark-hbase2.3.0_spark3.4.1_scala2.12.0_hadoop3.3.6.jar","hdfs:///user/hrivnac/hbase-spark-protocol-shaded-hbase2.3.0_spark3.4.1_scala2.12.0_hadoop3.3.6.jar"]';
jars           = null;
pyFiles        = null;
files          = null
archives       = null;
queue          = null;
jobName        = null;
conf           = '{"spark.mesos.principal":          "fink",' +
                   '"spark.mesos.secret":            "secret_2025",' +
                   '"spark.mesos.role":              "fink",' +
                   '"spark.cores.max":               "8",' +
                   '"spark.executor.cores":          "2",' +
                   '"name":                          "livy-batch",' +
                   '"spark.jars":                    "hdfs://157.136.254.149:8020/user/julius.hrivnac/hbase-spark-hbase2.3.0_spark3.4.1_scala2.12.0_hadoop3.3.6.jar,hdfs://157.136.254.149:8020/user/julius.hrivnac/hbase-spark-protocol-shaded-hbase2.3.0_spark3.4.1_scala2.12.0_hadoop3.3.6.jar",' +
                   '"spark.jars.packages":           "org.apache.spark:spark-streaming-kafka-0-10-assembly_2.12:3.4.1,org.apache.spark:spark-sql-kafka-0-10_2.12:3.4.1,org.apache.spark:spark-avro_2.12:3.4.1,org.apache.hbase:hbase-shaded-mapreduce:2.2.7,org.slf4j:slf4j-log4j12:1.7.36,org.slf4j:slf4j-simple:1.7.36",' +
                   '"spark.executor.memory":         "4g"}';
proxyUser      = 'livy';
tries          = 10;
sleep          = 1

server = new Server(serverName, urlLivy, urlSpark, urlHBase);

//action = new Action("Pokus", "print 1+1", Language.PYTHON, conf);
//result = server.livy().executeAction(action);
//print(result);

id = server.livy().sendJob(fileName,
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
                           tries,
                           1);
   
println("id = " + id);
statex0 = null;
if (id >= 0) {
  while (true) {
    resultString = server.livy().checkBatchProgress(id, 10, 1);
    if (resultString != null) {
      result = new JSONObject(resultString);
      statex = result.getString("state");
      if (statex0 == null) {
        statex0 = statex;
        println("State: " + statex);
        }
      if (!statex.equals(statex0)) {
        println("State: " + statex);
        statex0 = statex;
        }
      if (statex.equals("success") || statex.equals("dead")) {
        break;
        }
      }
    Thread.sleep(1000);
    }
  }
//logArray = result.getJSONArray("log");    
//String fullLog = "";
//for (Object logEntry : logArray) {
//  fullLog += logEntry.toString() + "\n";
//  }
resultString = server.livy().getBatchLog(id, 10, 1);
//result = new JSONObject(resultString);
println(resultString);
