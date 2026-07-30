// Created by AI (ChatGPT-5.6 via Hermes).
import com.Lomikel.Januser.DirectGremlinClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG;

client = new DirectGremlinClient('157.136.253.253', 24444);
try {
  gr = new FinkGremlinRecipiesG(client);
  String oid = 'ZTF19aadouqo';
  String src = 'FEATURES=2025/13-50';
  String dst = 'FINK';
  assert gr.classification(oid, dst).isEmpty() : 'The regression object must remain unclassified in the destination schema';

  long t0 = System.nanoTime();
  def result = gr.reclassification(oid, src, dst, 10, true);
  double elapsedMs = (System.nanoTime() - t0) / 1_000_000.0;

  def expected = [
    'SN candidate'           : 0.9391499883613128,
    'Microlensing candidate' : 0.0543305219188342,
    'Solar System candidate' : 0.0028279876786143888,
    'Early SN Ia candidate'  : 0.0021789859272380507,
    'Solar System MPC'       : 0.0011782841373792355,
    'Kilonova candidate'     : 3.3423197662139193E-4
  ];
  assert result*.class == expected.keySet().toList() : "Unexpected class ranking: ${result}";
  result.each { row ->
    assert row.classifier == 'FINK';
    assert row.flavor == '';
    assert Math.abs(row.weight - expected[row.class]) < 1.0E-12 : "Unexpected weight for ${row.class}: ${row.weight}";
  }

  def singleClass = gr.reclassify('FC-40', 'OCol', src, dst);
  assert singleClass == ['SN candidate':3.560797004004411,
                         'Microlensing candidate':0.7439011501426769] : "Standalone reclassify changed: ${singleClass}";
  assert gr.reclassify(null, 'OCol', src, dst).isEmpty();
  assert gr.reclassify('FC-40', null, src, dst).isEmpty();
  assert gr.reclassify('FC-40', 'OCol', null, dst).isEmpty();
  assert gr.reclassify('FC-40', 'OCol', src, null).isEmpty();
  def limited = gr.reclassification(oid, src, dst, 2, false);
  assert limited == result.take(2) : "nmax did not retain the two strongest classes: ${limited}";
  assert gr.lastQuality() == 0.0 : 'check=false must reset quality';
  assert gr.reclassification('missing-object-id', src, dst, 10, true).isEmpty();
  assert gr.lastQuality() == 0.0 : 'A missing object must not retain an earlier quality';

  String classifiedOid = 'ZTF17aackceb';
  assert !gr.classification(classifiedOid, dst).isEmpty() : 'The quality-check object must be classified in the destination schema';
  gr.reclassification(classifiedOid, src, dst, 10, true);
  assert gr.lastQuality() > 0.0 : 'A destination-classified object should produce a quality score';
  def repeated = gr.reclassification(oid, src, dst, 10, true);
  assert repeated == result : 'Reclassification changed between identical calls';
  assert gr.lastQuality() == 0.0 : 'Unavailable quality must not leak from a previous reclassification';

  println "RECLASSIFICATION_REGRESSION_OK MS=${elapsedMs}";
}
finally {
  client.close();
}
