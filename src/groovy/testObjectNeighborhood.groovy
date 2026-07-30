import com.Lomikel.Januser.JanusClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;

jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/CC.properties");
try {
  gr = new FinkGremlinRecipiesG(jc);

  String oid = '313985349745377418';
  Set<String> candidates = [oid, '313888627059851362'] as Set;
  Set<String> classes = [
    'rubin.tag_early_snia_candidate',
    'rubin.tag_sn_near_galaxy_candidate'
  ] as Set;

  def result = gr.objectNeighborhood(oid,
                                     'FINK',
                                     candidates,
                                     classes,
                                     ['nmax': 10, 'metric': 'JensenShannon']);
  assert result.size() == 1 : "Expected one constrained neighbor, got ${result}";
  assert result.keySet().first().key == '313888627059851362' : "Unexpected constrained neighbor: ${result}";

  def emptyClassResult = gr.objectNeighborhood(oid,
                                               'FINK',
                                               ['313888627059851362'] as Set,
                                               ['class-that-does-not-exist'] as Set,
                                               ['nmax': 10, 'metric': 'JensenShannon']);
  assert emptyClassResult.size() == 1 : "A constrained candidate without selected memberships must still be evaluated: ${emptyClassResult}";
  assert emptyClassResult.values().first().isEmpty() : "Expected an empty selected classification: ${emptyClassResult}";
  assert emptyClassResult.keySet().first().value == 1.0 : "Expected maximum distance for an empty selected classification: ${emptyClassResult}";

  def limitedResult = gr.objectNeighborhood(oid,
                                            'FINK',
                                            ['313888627059851362'] as Set,
                                            classes,
                                            ['nmax': 10, 'metric': 'JensenShannon', 'climit': 0.55]);
  assert limitedResult.size() == 1 : "Expected the constrained climit result: ${limitedResult}";
  assert limitedResult.values().first().keySet() == ['rubin.tag_early_snia_candidate'] as Set : "climit was not applied consistently: ${limitedResult}";
  assert limitedResult.keySet().first().value >= 0.0 && limitedResult.keySet().first().value <= 1.0 : "Filtered distance is outside the documented range: ${limitedResult}";

  assert gr.objectNeighborhood('missing-object-id',
                               'FINK',
                               ['313888627059851362'] as Set,
                               classes,
                               ['nmax': 10]).isEmpty();
  println 'OBJECT_NEIGHBORHOOD_JAVA_OVERLOAD_OK';
  }
finally {
  jc.close();
  }
