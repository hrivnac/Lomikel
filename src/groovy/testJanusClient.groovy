import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;

jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/IJCLab.properties");
gr = new FinkGremlinRecipiesG(jc);
print(jc.g().V().limit(1).valueMap().next());
print(gr.sourceNeighborhood("ZTF18actbfgh", null, null, "FINK_PORTAL", 10));
print(gr.stat());
print(gr.classification("ZTF18actbfgh"));
print(gr.overlaps());
print(gr.standardDeviationE('deepcontains', ['weight']));
print(gr.exportAoISoI('/tmp/Overlaps.graphml')); 
