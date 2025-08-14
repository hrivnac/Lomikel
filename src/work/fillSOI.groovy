import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;
import com.astrolabsoftware.FinkBrowser.Januser.Classifier;

jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/IJCLab.properties")
gr = new FinkGremlinRecipiesG(jc)

classifiers = new Classifier[]{Classifier.instance('FINK'),
                               Classifier.instance('XMATCH'),
                               Classifier.instance('FEATURES=Clusters/2024/13-60'),
                               Classifier.instance('FEATURES=Clusters/2025/13-50')}
formula = "cdsxmatch != 'Unknown' && roid != 3 && ndethist >= 3";
gr.processSoI(classifiers, formula, 'vdhbase1.lal.in2p3.fr:2183:ztf:schema_4.0_6.1.1', 20000, 1500, null)

gr.overlaps("outputCSV":"/tmp/overlaps.csv")



// ===============

//g.V().has('lbl', 'SoI').has('classifier', 'FINK').group().by(values('cls')).by(out().count()).unfold()
//==>Microlensing candidate=112
//==>Early SN Ia candidate=23
//==>SN candidate=1587
//==>Solar System candidate=12
//==>Solar System MPC=7

import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;
import com.astrolabsoftware.FinkBrowser.Januser.Classifier;

classifiers = new Classifier[]{Classifier.instance('FINK'),
                               Classifier.instance('XMATCH'),
                               Classifier.instance('FEATURES=Clusters/2024/13-60'),
                               Classifier.instance('FEATURES=Clusters/2025/13-50')}

clss = new String[]{"Early SN Ia candidate"}

gr.processSoI(classifiers, 'true', 'vdhbase1.lal.in2p3.fr:2183:ztf:schema_4.0_6.1.1', 200, 1500, clss)
