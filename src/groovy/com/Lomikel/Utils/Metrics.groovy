package com.Lomikel.Utils;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>Metrics</code> provides various metrics.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Metrics {
    
  /** Give distance (metric) between two {@link Map}s.
    * @param m0            The first classifier {@link Map} cls to weight.
    * @param mx            The second classifier {@link Map} cls to weight.
    *                      Entries, not present also in m0, will be ignored.
    * @param allClasses    Whether to consider also classes not available in original source.
    *                      Default: <tt>false</tt>.
    * @param metric        The metric to use <tt>JensenShannon, Euclidean or Cosine</tt>.
    *                      Default: <tt>JensenShannon</tt>. Anyhing else gives random metric - for testing.
    *                      <ul>
    *                      <li>Jensen-Shannon:	Comparing probability distributions, Handles missing classes robustly</li>
    *                      <li>Euclidean:	    Interpreting probabilities as points in space, Sensitive to magnitude</li>
    *                      <li>Cosine:         Comparing class pattern rather than strength, 	Ignores magnitude of probabilities</li>
    *                      </ul>
    * @return              The distance between two {@link Map}s. <tt>0-1</tt>*/
  def static double distance(Map<String, Double> m0,
                             Map<String, Double> mx,
                             boolean             allClasses,
                             String              metric = 'JensenShannon') {
    if (m0.isEmpty() && mx.isEmpty()) return 1.0 // or 0 ?
    if (m0.isEmpty() || mx.isEmpty()) return 1.0
    Set<String> keys = new HashSet<>(m0.keySet())
    keys.addAll(mx.keySet())
    double sum0 = m0.values().sum()
    double sumx = mx.values().sum()
    Map<String, Double> p = [:]
    Map<String, Double> q = [:]
    if (allClasses) { // normalise
      keys.each {k -> p[k] = m0.getOrDefault(k, 0.0) / sum0
                      q[k] = mx.getOrDefault(k, 0.0) / sumx}
      }
    else { // complete
      keys.each {k -> p[k] = m0.getOrDefault(k, 0.0)
                      q[k] = mx.getOrDefault(k, 0.0)}
      if (Math.abs(1.0 - sum0) > 0.000001) {
        def newkey = 'others0'
        keys.add(newkey)
        p[newkey] = 1.0 - sum0;
        q[newkey] = 0.0;
        }
      if (Math.abs(1.0 - sumx) > 0.000001) {
        def newkey = 'othersx'
        keys.add(newkey)
        p[newkey] = 0.0;
        q[newkey] = 1.0 - sumx;
        }
      }
    switch(metric) {
      case 'JensenShannon':
        return distanceJensenShannon(p, q, keys);
        break;
      case 'Euclidean':
        return distanceEuclidean(p, q, keys);
        break;
      case 'Cosine':
        return distanceCosine(p, q, keys);
        break;
      default:
        return _random.nextDouble();
        }    
    }
    
 /** Give Jensen Shannon distance (metric) between two {@link Map}s.
    * (see <a href="https://en.wikipedia.org/wiki/Jensen%E2%80%93Shannon_divergence">Jensen–Shannon divergence</a>)
    * @param p             The first classifier {@link Map} cls to weight.
    * @param q             The second classifier {@link Map} cls to weight.
    * @param keys          Unity of keys for m0, mx.
    * @return              The distance between two {@link Map}s. <tt>0-1</tt>*/
  def static double distanceJensenShannon(Map<String, Double> p,
                                          Map<String, Double> q,
                                          Set<String>         keys) {
    Map<String, Double> m = [:]
    keys.each {if (!p.containsKey(it)) {p[it] = 0.0}
               if (!q.containsKey(it)) {q[it] = 0.0}}
    keys.each {k -> m[k] = 0.5 * (p[k] + q[k])}
    def kl = {Map<String, Double> a, Map<String, Double> b ->
               double klDiv = 0.0
               a.each {k, v ->
                 if (v > 0 && b[k] > 0) {
                   klDiv += v * Math.log(v / b[k])
                   }
                 }
               return klDiv
               }
  double jsd = 0.5 * kl(p, m) + 0.5 * kl(q, m)
  return Math.sqrt(jsd)
  }                             
    
  /** Give Euclidean distance (metric) between two {@link Map}s.
    * @param p             The first classifier {@link Map} cls to weight.
    * @param q             The second classifier {@link Map} cls to weight.
    * @param keys          Unity of keys for m0, mx.
    * @return              The distance between two {@link Map}s. <tt>0-1</tt>*/
  def static double distanceEuclidean(Map<String, Double> p,
                                      Map<String, Double> q,
                                      Set<String>         keys) {
    double sumSq = 0.0
    keys.each {k ->
      sumSq += Math.pow(p[k] - q[k], 2)
      }   
   return Math.sqrt(sumSq) / Math.sqrt(2)
   }
    
  /** Give Cosine distance (metric) between two {@link Map}s.
    * @param p             The first classifier {@link Map} cls to weight.
    * @param q             The second classifier {@link Map} cls to weight.
    * @param keys          Unity of keys for m0, mx.
    * @return              The distance between two {@link Map}s. <tt>0-1</tt>*/
  def static double distanceCosine(Map<String, Double> p,
                                   Map<String, Double> q,
                                   Set<String>         keys) {
    double dot = 0.0
    double normp = 0.0
    double normq = 0.0
    keys.each {k ->
      dot += p[k] * q[k]
      normp += p[k] * p[k]
      normq += q[k] * q[k]
      }
    if (normp == 0 || normq == 0) return 1.0  // Max distance if one is zero vector
    double cosineSim = dot / (Math.sqrt(normp) * Math.sqrt(normq))
    return 1.0 - cosineSim
    }
    
  def static Random _random = new Random()
    
  /** Logging . */
  private static Logger log = LogManager.getLogger(Metrics.class);

  }
