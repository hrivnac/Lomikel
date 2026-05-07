import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;
import com.astrolabsoftware.FinkBrowser.Januser.Classifier;

jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/CC.properties");
g = jc.g();
gr = new FinkGremlinRecipiesG(jc);

// just take one object
oid = '313985349745377418';

// how it is classified
println(gr.classification(oid));

println(g.V().has('lbl', 'object').has('objectId', oid).inE().valueMap().next());

// what are the most similar objects
println(gr.objectNeighborhood(oid, 'FINK', 10, 'JensenShannon'));

// the classification of the first one (the closest one)
println(gr.classification('170028486134595648'));

// tag oid as 'TestTag' with weight=1.0 in TAG classification schema
gr.registerOCol(Classifier.instance('TAG', 'LSST', ''), 'TestTag', oid, 1.0, '', '');

// get full classification of oid (in all available schemas)
println(gr.classification(oid));

// how would be that oid classified in FINK
// (it uses information about its classification in TAG
//  and the correlations between classification in FINK and TAG schemas,
//  it doesn't use information how oid is actually classified in FINK)
println(gr.reclassification(oid, "TAG", "FINK", 10, true));

/* Python:  

from py4j.java_gateway import (JavaGateway, GatewayParameters)

gateway = JavaGateway(gateway_parameters = GatewayParameters(address = "127.0.0.1",
                                                             port = 25333))

jc = gateway.jvm.com.Lomikel.Januser.JanusClient("/opt/janusgraph-1/conf/gremlin-server/CC.properties")
g = jc.g()
gr = gateway.jvm.com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG(jc)

oid = '313985349745377418'

print(gr.classification(oid))

print(gr.objectNeighborhood(oid, 'FINK', 10.0, 'JensenShannon', 0.0, False))

print(gr.classification('170028486134595648'))

gr.registerOCol(gateway.jvm.com.astrolabsoftware.FinkBrowser.Januser.Classifier.instance('TAG', 'LSST', ''), 'TestTag', oid, 1.0, '', '')
print(gr.classification(oid))

print(gr.reclassification(oid, "TAG", "FINK", 10.0, True))

*/