import com.Lomikel.Utils.Info;
import com.Lomikel.Januser.StringGremlinClient;

StringGremlinClient client = new StringGremlinClient(Info.gremlinHost(), Info.gremlinPort());

client.interpret("importStatus()");