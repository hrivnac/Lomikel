import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;
import com.astrolabsoftware.FinkBrowser.Januser.Classifiers;

jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/IJCLab.properties")
gr = new FinkGremlinRecipiesG(jc)

classes = new String[]{"(CTA) Blazar",
                       "Ambiguous",
                       "Early SN Ia candidate",
                       "Kilonova candidate",
                       "Microlensing candidate",
                       "SN candidate",
                       "Solar System candidate",
                       "Solar System MPC",
                       "Tracklet",
                       "Anomaly"}
gr.processSourcesOfInterest(new String[]{'FINK_PORTAL', 'FEATURES'}, 'vdhbase1.lal.in2p3.fr:2183:ztf:schema_4.0_6.1.1', 10000, 1500, null, false, null)
gr.processSourcesOfInterest(new String[]{'FINK_PORTAL', 'FEATURES'}, 'vdhbase1.lal.in2p3.fr:2183:ztf:schema_4.0_6.1.1', 10000, 1500, classes, false, null)
gr.generateCorrelations(Classifiers.valueOf("FEATURES"), Classifiers.valueOf("FINK_PORTAL"))
gr.overlaps("outputCSV":"/tmp/overlaps.csv")
