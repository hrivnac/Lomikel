import com.Lomikel.Utils.Metrics

// Direct public helper must not mutate caller-owned probability maps.
def p = ['A': 1.0d]
def q = ['B': 1.0d]
def keys = ['A', 'B'] as Set
assert Math.abs(Metrics.distanceJensenShannon(p, q, keys) - Math.sqrt(Math.log(2.0d))) < 1e-12
assert p == ['A': 1.0d] : 'distanceJensenShannon changed its first input map'
assert q == ['B': 1.0d] : 'distanceJensenShannon changed its second input map'

// Normalization of a nonempty all-zero vector must not produce NaN/false identity.
['JensenShannon', 'Euclidean', 'Cosine'].each { name ->
    assert Metrics.distance(['A': 0.0d], ['A': 1.0d], true, name) == 1.0d :
        "a zero-mass vector is not a probability distribution for ${name}"
    assert Metrics.distance(['A': 0.0d], ['B': 0.0d], true, name) == 1.0d
}

// Synthetic missing-mass buckets must not overwrite real class labels.
def reference = Metrics.distance(['A': 0.3d, 'Z': 0.2d],
                                 ['A': 0.7d, 'Z': 0.1d], false, 'JensenShannon')
def realReservedName = Metrics.distance(['A': 0.3d, 'others0': 0.2d],
                                       ['A': 0.7d, 'others0': 0.1d], false, 'JensenShannon')
assert Math.abs(realReservedName - reference) < 1e-12 : 'real others0 class was overwritten by synthetic mass'
println 'MetricsOfflineTest PASS'
