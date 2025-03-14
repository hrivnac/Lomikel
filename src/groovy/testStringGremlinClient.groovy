import com.Lomikel.Januser.StringGremlinClient;

// Connect to JanusGraph
client = new StringGremlinClient("134.158.74.85", 24444);

// Execute Gremlin command
// interpret(...) gives List<Result>
// interpretJSON(...) gives JSON String can
print(client.interpret("g.V().has('lbl', 'alert').limit(2).next(2).get(0).values('importDate')"));
print(client.interpret2JSON("v=g.V().has('lbl', 'source').limit(1).valueMap())"));
print(client.interpret2JSON("g.V().limit(20)"));

client.close();


