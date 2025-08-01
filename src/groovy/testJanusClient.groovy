import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;
import com.astrolabsoftware.FinkBrowser.Januser.Classifier;

jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/IJCLab.properties");
gr = new FinkGremlinRecipiesG(jc);
jc.g().V().limit(1).valueMap().next();
gr.stat();
gr.overlaps();

oid = "ZTF19abasthk";
oid = "ZTF25aakbssn";

gr.fhclient('vdhbase1.lal.in2p3.fr:2183:ztf:schema_4.0_6.1.1')
gr.classifySource(Classifier.instance("FEATURES=Clusters/2025/13-50"), oid, 'vdhbase1.lal.in2p3.fr:2183:ztf:schema_4.0_6.1.1', false, null)
gr.classifySource(Classifier.instance("FEATURES=Clusters/2025/13-50"), oid, null, false, null)

gr.registerSoI(Classifier.instance("TAG"), 'MyTag_1', oid, 1.0, '', false, null)

gr.classification(oid);
gr.classification(oid, "FINK");
gr.classification(oid, "FINK=aflavor");
gr.classification(oid, "FEATURES=Clusters/2025/13-50");

gr.reclassification(oid, "FEATURES=Clusters/2025/13-50", "FINK", 10, 'AoI');

gr.sourceNeighborhood(oid, "FINK", 10, 'JensenShannon'); // Euclidean, Cosine
gr.sourceNeighborhood(oid, "FEATURES=Clusters/2025/13-50", 10, 'JensenShannon');
gr.sourceNeighborhood(oid, "FEATURES=Clusters/2025/13-50", 0.5, 'JensenShannon');
gr.sourceNeighborhood(oid, "FEATURES=Clusters/2025/13-50", 10, 'JensenShannon', 0.2, True);

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
gr.sourceNeighborhood('ZTF18actbfgh', 'FINK', 10);

import com.Lomikel.Januser.StringGremlinClient;
client = new StringGremlinClient('134.158.74.85', 24444);
print(client.interpret("gr=new com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG(g);gr.sourceNeighborhood('ZTF18actbfgh', 'FINK', 10)"));

import com.Lomikel.Januser.DirectGremlinClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;
client = new DirectGremlinClient('134.158.74.85', 24444);
g = client.g();
gr = new FinkGremlinRecipiesG(g);
gr.sourceNeighborhood('ZTF18abctqum', 'FINK', 10);

