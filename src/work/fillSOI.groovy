import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;
import com.astrolabsoftware.FinkBrowser.Januser.Classifiers;
import com.astrolabsoftware.FinkBrowser.Januser.FeaturesClassifier

jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/IJCLab.properties")
gr = new FinkGremlinRecipiesG(jc)

FeaturesClassifier.setModelResource("Clusters/2024/13-60")

formula = "cdsxmatch != 'Unknown' && roid != 3 && ndethist >= 3";
gr.processSoI(new String[]{'FINK', 'FEATURES'}, formula, 'vdhbase1.lal.in2p3.fr:2183:ztf:schema_4.0_6.1.1', 20000, 1500, null, false, null)

//classes = new String[]{"(CTA) Blazar",
//                       "Ambiguous",
//                       "Early SN Ia candidate",
//                       "Kilonova candidate",
//                       "Microlensing candidate",
//                       "SN candidate",
//                       "Solar System candidate",
//                       "Solar System MPC",
//                       "Tracklet",
//                       "Anomaly"}
//gr.processSoI(new String[]{'FINK', 'FEATURES'}, null, 'vdhbase1.lal.in2p3.fr:2183:ztf:schema_4.0_6.1.1', 0, 1500, classes, false, null)

gr.generateCorrelations(Classifiers.valueOf("FEATURES"), Classifiers.valueOf("FINK_PORTAL"))
gr.overlaps("outputCSV":"/tmp/overlaps.csv")
