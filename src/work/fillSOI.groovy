import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipies;

jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/IJCLab.properties")
gr = new FinkGremlinRecipies(jc)

//gr.processSourcesOfInterest('FINK_PORTAL',    'hbase-1.lal.in2p3.fr:2183:ztf:', 1000, 10000, null, false, null)
//gr.processSourcesOfInterest('FINK_PORTAL',    'hbase-1.lal.in2p3.fr:2183:ztf:', 1000, 10000, new String[]{'SN candidate', 'Kilonova candidate', 'Early SN Ia candidate', 'Ambiguous', '*'},  false, null)
//gr.processSourcesOfInterest('FINK_PORTAL_10', 'hbase-1.lal.in2p3.fr:2183:ztf:', 1000, 10000, null, false, null)
//gr.processSourcesOfInterest('FINK_PORTAL_10', 'hbase-1.lal.in2p3.fr:2183:ztf:', 1000, 10000, new String[]{'SN candidate', 'Kilonova candidate', 'Early SN Ia candidate', 'Ambiguous', '*'},  false, null)

gr.processSourcesOfInterest('FINK_PORTAL',    'hbase-1.lal.in2p3.fr:2183:ztf:',                  1000000000, 1500, new String[]{'*'},  false, null)
gr.processSourcesOfInterest('FINK_PORTAL_10', 'hbase-1.lal.in2p3.fr:2183:ztf:',                  1000000000, 1500, new String[]{'*'},  false, null)
gr.processSourcesOfInterest('FEATURES',       'hbase-1.lal.in2p3.fr:2183:ztf::schema_3.1_5.6.2', 1000000000, 1500, new String[]{'*'},  false, null)
