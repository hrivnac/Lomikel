# Automatic projection and trajectory diagnostics

LightCurveView can derive a deterministic two-dimensional projection with
**standardized principal-component analysis (PCA)** and describe the resulting
trajectory. This is an exploratory aid for classifier development. It is not an
astrophysical classifier and none of its displayed values is a class
probability.

## Projection method

For the bands that have at least three finite observations, the analysis:

1. chooses the largest deterministic subset with a positive common observing
   interval;
2. linearly interpolates each selected band onto the same 201-point grid;
3. standardizes each band independently;
4. diagonalizes the resulting correlation matrix with a small dependency-free
   Jacobi eigensolver;
5. uses the two components with the largest eigenvalues as the x and y axes;
6. converts those components back to affine formulas in raw magnitudes, including
   the required intercepts.

The eigenvector signs and subset tie-breaks are fixed, so identical input gives
identical coefficients. Coefficients are scaled by one common positive divisor
to fit the editor without changing trajectory geometry. Constant bands,
insufficient bands, lack of overlap, rank-one data, and numerical failures are
reported rather than converted into invented 2D results.

Standardized PCA is the default because raw PCA allowed high-amplitude bands to
dominate some generated examples. Output whitening is intentionally not used:
it would force both projected variances to be equal and erase the line-shape
signal.

## Displayed descriptors

For centered projected points and covariance eigenvalues
`lambda1 >= lambda2`, the interface reports:

- **2D variance retained**: `(lambda1 + lambda2) / sum(all eigenvalues)`;
- **line score**: `(lambda1 - lambda2) / (lambda1 + lambda2)`;
- **closure**: endpoint distance divided by RMS radius;
- **radial CV**: standard deviation of radius divided by mean radius;
- **winding**: absolute accumulated wrapped polar angle divided by `2*pi`;
- **angular coherence**: absolute signed angular change divided by total
  absolute angular change;
- **roughness**: squared second differences divided by squared first
  differences on the fixed grid.

The visible pattern tag is deliberately labeled **demo-calibrated**:

- line-like when line score is at least 0.85;
- otherwise loop-like when line score is at most 0.50, winding is at least 0.75,
  angular coherence is at least 0.50, and closure is at most 1.0;
- otherwise other/irregular.

Closure alone is not evidence for a loop: a symmetric peak travels out and back
and can have nearly coincident endpoints. Winding alone is also unstable when a
trajectory passes close to its centroid. The combined descriptors make these
failure modes visible but do not turn the heuristic into a trained classifier.

## Generated-demo calibration

A deterministic 200-seed run of the implemented JavaScript generators and
analysis gave the following medians and 5th--95th percentiles. It is reproducible
with:

```sh
node tests/calibrate-analysis.cjs --runs 200
```

The harness uses `LCG(1664525,1013904223,2^32)`, inclusive seeds 1--200, and
selects percentile index `floor((n - 1) * p)` from sorted samples:

| demo | 2D variance | line score | winding | angular coherence | assigned pattern |
| --- | --- | --- | --- | --- | --- |
| random | 0.6677 [0.6557, 0.6811] | 0.1153 [0.0437, 0.2000] | 0.0636 [0.0344, 0.0873] | 0.0418 [0.0226, 0.0559] | other: 200/200 |
| peak | 0.9976 [0.9972, 0.9980] | 0.9177 [0.9089, 0.9253] | 0.0143 [0.0080, 0.0215] | 0.0126 [0.0069, 0.0189] | line-like: 200/200 |
| periodic | 0.6838 [0.6643, 0.7057] | 0.2604 [0.2044, 0.3103] | 1.1361 [0.9793, 1.2113] | 0.5950 [0.5420, 0.6458] | loop-like: 195/200; other: 5/200 |

These measurements test the intended generated patterns only. They are not
sensitivity, specificity, or accuracy estimates for astronomical classes.
Searching hundreds of projections for the most circular result was rejected as
the default approach because such a search can manufacture convincing loops in
non-periodic data while discarding much of the variance.

## Limitations

- The 201 interpolated points are correlated display samples, not 201 independent
  measurements.
- Sparse cadence and large gaps can create visually smooth interpolated arcs.
- Photometric uncertainties are not yet used; standardization can amplify a
  low-signal band.
- Results from objects with different available bands or common intervals are
  not automatically comparable.
- Per-object PCA is adaptive and unsupervised. It optimizes retained variance,
  not class separation.
- Eigenvectors can rotate when eigenvalues are nearly equal, even though the 2D
  subspace remains stable.
- A periodic source observed over a non-integer number of cycles may not close.
- The generated `random` example is a mixture of synthetic patterns, not a model
  of all irregular astronomical variability.

## Path to a real classifier

The next scientifically valid stage requires a labeled population, not further
threshold tuning on these three demos:

1. define a versioned preprocessing contract, including cadence, uncertainties,
   band dropout, and interpolation;
2. extract these descriptors plus non-projected baseline light-curve features;
3. split by object into training, validation, and untouched test sets;
4. select projection parameters and models on training folds only;
5. report macro-F1, balanced accuracy, per-class recall, calibration, and
   uncertainty intervals on held-out objects;
6. compare against the non-projected baseline and test robustness to missing
   bands, cadence changes, photometric noise, and class imbalance.

Only that held-out evaluation can justify replacing the current
`demo-calibrated` wording with astrophysical class predictions.
