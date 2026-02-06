import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;
import com.astrolabsoftware.FinkBrowser.Januser.Classifier;

jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/IJCLab.properties")
gr = new FinkGremlinRecipiesG(jc)
graph = jc.g().getGraph();

csvFile = "../data/LightCurves/Latent/summary.csv"
batchSize = 10;
classifier = Classifier.instance('LIGHTCURVES=Latent');
new File(csvFile).eachLine {
  line, count ->
    if (line != null && line.trim().length() > 0 && count > 1) {
      field = line.split(",");
      oid = field[0]
      cls = field[1]
      if (cls != 'cls' && cls != 'Unknown') {
        gr.registerOCol(classifier, cls, oid, ['weight':'1'], true);
        }
      if (count % batchSize == 0) {
        graph.tx().commit();
        println count;
        }
      }  
  }    
graph.tx().commit();

classifiers = new Classifier[]{Classifier.instance('FINK'),
                               Classifier.instance('XMATCH'),
                               Classifier.instance('FEATURES=2024/13-60'),
                               Classifier.instance('FEATURES=2025/13-50'),
                               Classifier.instance('LIGHTCURVES=Latent'),
                               Classifier.instance('TAG')};
gr.generateCorrelations(classifiers);
gr.overlaps('LIGHTCURVES=Latent');



