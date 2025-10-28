import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;
import com.astrolabsoftware.FinkBrowser.Januser.Classifier;

jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/IJCLab.properties")
gr = new FinkGremlinRecipiesG(jc)

classifiers = new Classifier[]{Classifier.instance('FINK'),
                               Classifier.instance('XMATCH'),
                               Classifier.instance('FEATURES=2024/13-60'),
                               Classifier.instance('FEATURES=2025/13-50'),
                               Classifier.instance('TAG')}
//formula = "cdsxmatch != 'Unknown' && roid != 3 && ndethist >= 3";
gr.processOCol(classifiers, 'true', 'vdhbase1.lal.in2p3.fr:2183:ztf:schema_4.0_6.1.1', 20000, 1500, null);

//gr.processOCol(classifiers, 'true', 'vdhbase1.lal.in2p3.fr:2183:ztf:schema_4.0_6.1.1', 2000000, 1500000, new String[]{"Microlensing candidate"})
//gr.processOCol(classifiers, 'true', 'vdhbase1.lal.in2p3.fr:2183:ztf:schema_4.0_6.1.1', 2000000, 1500000, new String[]{"Solar System candidate"})
//gr.processOCol(classifiers, 'true', 'vdhbase1.lal.in2p3.fr:2183:ztf:schema_4.0_6.1.1', 2000000, 1500000, new String[]{"Solar System MPC"})



// ===============

//g.V().has('lbl', 'OCol').has('classifier', 'FINK').group().by(values('cls')).by(out().count()).unfold()
//==>Microlensing candidate=112
//==>Early SN Ia candidate=23
//==>SN candidate=1587
//==>Solar System candidate=12
//==>Solar System MPC=7

