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
// sum of weights (= number od alerts) per source
print(jc.g().V().has('lbl', 'source').order().by('objectId', asc).project('objectId', 'weight').by(values('objectId')).by(inE().values('weight').sum()));
// numbers of source lastly updated per update (date)
print(jc.g().V().has('lbl', 'source').values('importDate').groupCount());
