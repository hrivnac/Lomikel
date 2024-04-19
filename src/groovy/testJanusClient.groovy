import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;

jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/IJCLab.properties");
gr = new FinkGremlinRecipiesG(jc);
print(jc.g().V().limit(1).valueMap().next());
print(gr.sourceNeighborhood("ZTF17aaawgky", ["ZTF18abablgk", "ZTF18ablxmyz"], null, 10));
print(gr.stat());
print(gr.classification("ZTF17aaawgky"));
print(gr.overlaps());
print(gr.standardDeviationE('deepcontains', ['weight']));

