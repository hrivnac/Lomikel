import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;

jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/IJCLab.properties");
gr = new FinkGremlinRecipiesG(jc);
print(gr.sourceNeighborhood("ZTF17aaawgky", ["ZTF18abablgk", "ZTF18ablxmyz"], null, 10));
