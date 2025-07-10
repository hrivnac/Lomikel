import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;
import com.astrolabsoftware.FinkBrowser.Januser.Classifiers;
import com.astrolabsoftware.FinkBrowser.Januser.FeaturesClassifier

jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/IJCLab.properties")
gr = new FinkGremlinRecipiesG(jc)

gr.fhclient('vdhbase1.lal.in2p3.fr:2183:ztf:schema_4.0_6.1.1')
FeaturesClassifier.setModelResource("Clusters/2024/13-60")
jc.g().V().has('lbl', 'source').values('objectId').toList().each{oid -> gr.classifySource(Classifiers.FEATURES, oid, 'vdhbase1.lal.in2p3.fr:2183:ztf:schema_4.0_6.1.1', false, null)}

gr.generateCorrelations(Classifiers.valueOf("FEATURES"), Classifiers.valueOf("FINK_PORTAL"))
gr.overlaps("outputCSV":"/tmp/newoverlaps.csv")
