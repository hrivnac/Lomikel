import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;

jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/IJCLab.properties");
gr = new FinkGremlinRecipiesG(jc);
jc.g().V().limit(1).valueMap().next();
gr.sourceNeighborhood("ZTF18actbfgh", "FINK_PORTAL", 10);
gr.stat();
gr.classification("ZTF18actbfgh");
gr.overlaps();
gr.classifySource(Classifiers.FEATURES, 'ZTF25aaksfzy', 'vdhbase1.lal.in2p3.fr:2183:ztf:schema_4.0_6.1.1', false, null)
gr.classify("FC-3", "SourcesOfInterest", "FEATURES", "FINK_PORTAL")
gr.standardDeviationE('deepcontains', ['weight']);
gr.exportAoISoI('/tmp/Overlaps.graphml');
// sum of weights (= number od alerts) per source
jc.g().V().has('lbl', 'source').order().by('objectId', asc).project('objectId', 'weight').by(values('objectId')).by(inE().values('weight').sum());
// numbers of source lastly updated per update (date)
jc.g().V().has('lbl', 'source').values('importDate').groupCount();

// comparison of JanusClient, DirectGremlinClient, StringGremlinClient

import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;
jc = new JanusClient('/opt/janusgraph-1/conf/gremlin-server/IJCLab.properties');
gr = new FinkGremlinRecipiesG(jc);
gr.sourceNeighborhood('ZTF18actbfgh', 'FINK_PORTAL', 10);

import com.Lomikel.Januser.StringGremlinClient;
client = new StringGremlinClient('134.158.74.85', 24444);
print(client.interpret("gr=new com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG(g);gr.sourceNeighborhood('ZTF18actbfgh', 'FINK_PORTAL', 10)"));

import com.Lomikel.Januser.DirectGremlinClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;
client = new DirectGremlinClient('134.158.74.85', 24444);
g = client.g();
gr = new FinkGremlinRecipiesG(g);
gr.sourceNeighborhood('ZTF18abctqum', 'FINK_PORTAL', 10);

