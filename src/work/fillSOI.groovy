import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipies;
import com.astrolabsoftware.FinkBrowser.Januser.Classifiers;

jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/IJCLab.properties")
gr = new FinkGremlinRecipies(jc)

//gr.processSourcesOfInterest('FINK_PORTAL', 'hbase-1.lal.in2p3.fr:2183:ztf:', 1000, 10000, null, false, null)
//gr.processSourcesOfInterest('FINK_PORTAL', 'hbase-1.lal.in2p3.fr:2183:ztf:', 1000, 10000, new String[]{'SN candidate', 'Kilonova candidate', 'Early SN Ia candidate', 'Ambiguous', '*'},  false, null)
//gr.processSourcesOfInterest('FINK_PORTAL', 'hbase-1.lal.in2p3.fr:2183:ztf:', 10000, 1500, new String[]{'*'},  false, null)
gr.processSourcesOfInterest('FINK_PORTAL', 'hbase-1.lal.in2p3.fr:2183:ztf:', 3000, 1500, null,  false, null)
gr.processSourcesOfInterest('FEATURES',    'hbase-1.lal.in2p3.fr:2183:ztf:', 3000, 1500, null,  false, null)
gr.generateCorrelations(Classifiers.valueOf("FEATURES"), Classifiers.valueOf("FINK_PORTAL"))
gr.overlaps(null, null, "/tmp/overlaps.csv")
 