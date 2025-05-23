import com.Lomikel.Januser.DirectGremlinClient;

// Connect to JanusGraph
client = new DirectGremlinClient("134.158.74.85", 24444);
g = client.g();

// Direct access (should not contain any non-tinkerpop extentions)
// submit(...) gives ResultSet
t = g.V().has('lbl', 'source').limit(4).values('objectId');
print(client.submit(t).all().get());
print(client.submit(t).all().get().get(0).getString());

// String access (can contain anything)
s = "v=g.V().has('lbl', 'source').limit(1).valueMap()";
print(client.submit(s).one().getString());

client.close();


