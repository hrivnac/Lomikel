const ANALYSIS_GRID_SIZE = 201;
const ANALYSIS_MIN_OBSERVATIONS = 3;
const ANALYSIS_MIN_BANDS = 3;
const ANALYSIS_EIGEN_TOLERANCE = 1e-12;

function analysisBandCandidates(data, minObservations = ANALYSIS_MIN_OBSERVATIONS) {
  const eligible = filters.filter(band => {
    const curve = data && data[band];
    return curve
      && curve.times.length >= minObservations
      && curve.values.length >= minObservations
      && curve.times[curve.times.length - 1] > curve.times[0];
  });
  const candidates = [];
  for (let mask = 1; mask < (1 << eligible.length); mask++) {
    const bands = eligible.filter((_, index) => mask & (1 << index));
    if (bands.length < ANALYSIS_MIN_BANDS) continue;
    const start = Math.max(...bands.map(band => data[band].times[0]));
    const end = Math.min(...bands.map(band => data[band].times[data[band].times.length - 1]));
    if (!(end > start)) continue;
    const observations = bands.reduce((total, band) => total + data[band].times.length, 0);
    candidates.push({bands, start, end, span: end - start, observations});
  }
  candidates.sort((left, right) =>
    right.bands.length - left.bands.length
    || right.span - left.span
    || right.observations - left.observations
    || filters.map(band => left.bands.includes(band) ? "0" : "1").join("")
      .localeCompare(filters.map(band => right.bands.includes(band) ? "0" : "1").join(""))
  );
  return {eligible, candidates};
}

function synchronizedMatrix(data, candidate, gridSize = ANALYSIS_GRID_SIZE) {
  const times = Array.from({length: gridSize}, (_, index) =>
    index === gridSize - 1
      ? candidate.end
      : candidate.start + index * (candidate.end - candidate.start) / (gridSize - 1)
  );
  const matrix = times.map(time => candidate.bands.map(band =>
    interp1D(data[band].times, data[band].values, time).val
  ));
  return {times, matrix};
}

function standardizeMatrix(matrix, bands) {
  const rows = matrix.length;
  const means = bands.map((_, column) =>
    matrix.reduce((sum, row) => sum + row[column], 0) / rows
  );
  const scales = bands.map((_, column) => {
    const sumSquares = matrix.reduce((sum, row) => {
      const centered = row[column] - means[column];
      return sum + centered * centered;
    }, 0);
    return Math.sqrt(sumSquares / Math.max(1, rows - 1));
  });
  const retained = bands.map((band, index) => ({band, index}))
    .filter(({index}) => scales[index] > Math.sqrt(Number.EPSILON) * Math.max(1, Math.abs(means[index])));
  if (retained.length < ANALYSIS_MIN_BANDS) return null;
  const retainedBands = retained.map(item => item.band);
  const retainedMeans = retained.map(item => means[item.index]);
  const retainedScales = retained.map(item => scales[item.index]);
  const standardized = matrix.map(row => retained.map((item, retainedIndex) =>
    (row[item.index] - retainedMeans[retainedIndex]) / retainedScales[retainedIndex]
  ));
  return {bands: retainedBands, means: retainedMeans, scales: retainedScales, matrix: standardized};
}

function correlationMatrix(standardized) {
  const rows = standardized.length;
  const columns = standardized[0].length;
  return Array.from({length: columns}, (_, i) =>
    Array.from({length: columns}, (_, j) =>
      standardized.reduce((sum, row) => sum + row[i] * row[j], 0) / Math.max(1, rows - 1)
    )
  );
}

function symmetricEigenJacobi(input, maxSweeps = 50, tolerance = ANALYSIS_EIGEN_TOLERANCE) {
  const size = input.length;
  const matrix = input.map(row => row.slice());
  const vectors = Array.from({length: size}, (_, row) =>
    Array.from({length: size}, (_, column) => row === column ? 1 : 0)
  );
  let converged = size < 2;
  const rotationsPerSweep = Math.max(1, size * (size - 1) / 2);
  const maxRotations = maxSweeps * rotationsPerSweep;
  for (let rotation = 0; rotation < maxRotations && !converged; rotation++) {
    let p = 0;
    let q = 1;
    let largest = 0;
    for (let row = 0; row < size; row++) {
      for (let column = row + 1; column < size; column++) {
        const magnitude = Math.abs(matrix[row][column]);
        if (magnitude > largest) {
          largest = magnitude;
          p = row;
          q = column;
        }
      }
    }
    const scale = Math.max(1, ...matrix.map((row, index) => Math.abs(row[index])));
    if (largest <= tolerance * scale) {
      converged = true;
      break;
    }
    const app = matrix[p][p];
    const aqq = matrix[q][q];
    const apq = matrix[p][q];
    const angle = 0.5 * Math.atan2(2 * apq, aqq - app);
    const cosine = Math.cos(angle);
    const sine = Math.sin(angle);
    for (let index = 0; index < size; index++) {
      if (index === p || index === q) continue;
      const aip = matrix[index][p];
      const aiq = matrix[index][q];
      matrix[index][p] = matrix[p][index] = cosine * aip - sine * aiq;
      matrix[index][q] = matrix[q][index] = sine * aip + cosine * aiq;
    }
    matrix[p][p] = cosine * cosine * app - 2 * sine * cosine * apq + sine * sine * aqq;
    matrix[q][q] = sine * sine * app + 2 * sine * cosine * apq + cosine * cosine * aqq;
    matrix[p][q] = matrix[q][p] = 0;
    for (let row = 0; row < size; row++) {
      const vip = vectors[row][p];
      const viq = vectors[row][q];
      vectors[row][p] = cosine * vip - sine * viq;
      vectors[row][q] = sine * vip + cosine * viq;
    }
  }
  if (!converged) return {converged: false, values: [], vectors: []};
  const order = Array.from({length: size}, (_, index) => index)
    .sort((left, right) => matrix[right][right] - matrix[left][left] || left - right);
  const values = order.map(index => Math.max(0, matrix[index][index]));
  const orderedVectors = order.map(column => vectors.map(row => row[column]));
  for (const vector of orderedVectors) {
    let pivot = 0;
    for (let index = 1; index < vector.length; index++) {
      if (Math.abs(vector[index]) > Math.abs(vector[pivot])) pivot = index;
    }
    if (vector[pivot] < 0) {
      for (let index = 0; index < vector.length; index++) vector[index] *= -1;
    }
  }
  return {converged: true, values, vectors: orderedVectors};
}

function wrapAngle(angle) {
  let wrapped = angle;
  while (wrapped <= -Math.PI) wrapped += 2 * Math.PI;
  while (wrapped > Math.PI) wrapped -= 2 * Math.PI;
  return wrapped;
}

function scoreProjectedTrajectory(points, eigenvalues) {
  const count = points.length;
  const meanX = points.reduce((sum, point) => sum + point.x, 0) / count;
  const meanY = points.reduce((sum, point) => sum + point.y, 0) / count;
  const centered = points.map(point => ({x: point.x - meanX, y: point.y - meanY}));
  const varianceX = centered.reduce((sum, point) => sum + point.x * point.x, 0) / Math.max(1, count - 1);
  const varianceY = centered.reduce((sum, point) => sum + point.y * point.y, 0) / Math.max(1, count - 1);
  const total2D = varianceX + varianceY;
  const lambda1 = eigenvalues[0];
  const lambda2 = eigenvalues[1];
  const totalVariance = eigenvalues.reduce((sum, value) => sum + value, 0);
  const lineScore = total2D > 0 ? (lambda1 - lambda2) / (lambda1 + lambda2) : null;
  const rmsRadius = Math.sqrt(centered.reduce((sum, point) => sum + point.x * point.x + point.y * point.y, 0) / count);
  const closure = rmsRadius > 0
    ? Math.hypot(
      centered[centered.length - 1].x - centered[0].x,
      centered[centered.length - 1].y - centered[0].y
    ) / rmsRadius
    : null;
  const radii = centered.map(point => Math.hypot(point.x, point.y));
  const meanRadius = radii.reduce((sum, radius) => sum + radius, 0) / count;
  const radialVariance = radii.reduce((sum, radius) => sum + (radius - meanRadius) ** 2, 0) / Math.max(1, count - 1);
  const radialCv = meanRadius > 0 ? Math.sqrt(radialVariance) / meanRadius : null;
  let signedAngle = 0;
  let absoluteAngle = 0;
  for (let index = 0; index < centered.length - 1; index++) {
    const delta = wrapAngle(
      Math.atan2(centered[index + 1].y, centered[index + 1].x)
      - Math.atan2(centered[index].y, centered[index].x)
    );
    signedAngle += delta;
    absoluteAngle += Math.abs(delta);
  }
  const winding = Math.abs(signedAngle) / (2 * Math.PI);
  const angularMonotonicity = absoluteAngle > 0 ? Math.abs(signedAngle) / absoluteAngle : 0;
  let curvatureEnergy = 0;
  let segmentEnergy = 0;
  for (let index = 0; index < centered.length - 1; index++) {
    const dx = centered[index + 1].x - centered[index].x;
    const dy = centered[index + 1].y - centered[index].y;
    segmentEnergy += dx * dx + dy * dy;
  }
  for (let index = 1; index < centered.length - 1; index++) {
    const ddx = centered[index + 1].x - 2 * centered[index].x + centered[index - 1].x;
    const ddy = centered[index + 1].y - 2 * centered[index].y + centered[index - 1].y;
    curvatureEnergy += ddx * ddx + ddy * ddy;
  }
  const roughness = segmentEnergy > 0 ? curvatureEnergy / segmentEnergy : null;
  let pattern = "other";
  if (lineScore != null && lineScore >= 0.85) pattern = "line-like";
  else if (lineScore != null && lineScore <= 0.5
           && winding >= 0.75 && angularMonotonicity >= 0.5
           && closure != null && closure <= 1) pattern = "loop-like";
  return {
    explainedVariance: totalVariance > 0 ? (lambda1 + lambda2) / totalVariance : null,
    lineScore, closure, radialCv, winding, angularMonotonicity, roughness, pattern,
  };
}

function rawProjection(analysisBands, means, scales, vectors) {
  const rawAxes = vectors.slice(0, 2).map(vector => vector.map((value, index) => value / scales[index]));
  const offsets = rawAxes.map(axis => -axis.reduce((sum, value, index) => sum + value * means[index], 0));
  const maxCoefficient = Math.max(...rawAxes.flat().map(Math.abs));
  const divisor = Math.max(1, maxCoefficient / 2);
  const projection = {x: {}, y: {}, offsetX: offsets[0] / divisor, offsetY: offsets[1] / divisor, divisor};
  for (const band of filters) {
    projection.x[band] = 0;
    projection.y[band] = 0;
  }
  analysisBands.forEach((band, index) => {
    projection.x[band] = rawAxes[0][index] / divisor;
    projection.y[band] = rawAxes[1][index] / divisor;
  });
  return projection;
}

function analyzeTrajectory(data, options = {}) {
  const gridSize = options.gridSize || ANALYSIS_GRID_SIZE;
  const minObservations = options.minObservations || ANALYSIS_MIN_OBSERVATIONS;
  const {eligible, candidates} = analysisBandCandidates(data, minObservations);
  if (eligible.length < ANALYSIS_MIN_BANDS) {
    return {status: "insufficientBands", bands: eligible, excludedBands: filters.filter(band => !eligible.includes(band))};
  }
  let best = null;
  let previousCandidateSize = candidates[0]?.bands.length || 0;
  let sawNumericalFailure = false;
  for (const candidate of candidates) {
    if (candidate.bands.length < previousCandidateSize) {
      if (best && best.bands.length >= previousCandidateSize) break;
      previousCandidateSize = candidate.bands.length;
    }
    const synchronized = synchronizedMatrix(data, candidate, gridSize);
    const standardized = standardizeMatrix(synchronized.matrix, candidate.bands);
    if (!standardized) continue;
    const eigen = symmetricEigenJacobi(correlationMatrix(standardized.matrix));
    if (!eigen.converged) {
      sawNumericalFailure = true;
      continue;
    }
    const threshold = Math.max(1, eigen.values[0]) * 1e-10;
    if (!(eigen.values[1] > threshold)) continue;
    const points = standardized.matrix.map(row => ({
      x: row.reduce((sum, value, index) => sum + value * eigen.vectors[0][index], 0),
      y: row.reduce((sum, value, index) => sum + value * eigen.vectors[1][index], 0),
    }));
    const projection = rawProjection(standardized.bands, standardized.means, standardized.scales, eigen.vectors);
    projection.bands = standardized.bands.slice();
    projection.interval = {start: candidate.start, end: candidate.end};
    const observations = standardized.bands.reduce((total, band) => total + data[band].times.length, 0);
    const signature = filters.map(band => standardized.bands.includes(band) ? "0" : "1").join("");
    const result = {
      status: "ok",
      bands: standardized.bands,
      excludedBands: filters.filter(band => !standardized.bands.includes(band)),
      interval: {start: candidate.start, end: candidate.end},
      points,
      projection,
      scores: scoreProjectedTrajectory(points, eigen.values),
      diagnostics: {eigenvalues: eigen.values, means: standardized.means, scales: standardized.scales},
      selection: {span: candidate.span, observations, signature},
    };
    if (!best
        || result.bands.length > best.bands.length
        || (result.bands.length === best.bands.length && result.selection.span > best.selection.span)
        || (result.bands.length === best.bands.length && result.selection.span === best.selection.span
            && result.selection.observations > best.selection.observations)
        || (result.bands.length === best.bands.length && result.selection.span === best.selection.span
            && result.selection.observations === best.selection.observations
            && result.selection.signature < best.selection.signature)) {
      best = result;
    }
  }
  if (best) {
    delete best.selection;
    return best;
  }
  if (sawNumericalFailure) return {status: "numericalFailure", bands: eligible};
  return {status: candidates.length ? "rankOne" : "noCommonInterval", bands: eligible};
}

function renderAnalysisResult(result) {
  if (typeof document === "undefined") return;
  const section = document.getElementById("analysis-results");
  if (!section) return;
  section.hidden = false;
  const labels = {
    "line-like": "Line-like (demo-calibrated)",
    "loop-like": "Loop-like (demo-calibrated)",
    other: "Other / irregular (demo-calibrated)",
  };
  const put = (id, value) => {
    const element = document.getElementById(id);
    if (element) element.textContent = value;
  };
  const format = value => Number.isFinite(value) ? value.toFixed(3) : "Not available";
  put("analysis-pattern", labels[result.scores.pattern]);
  put("analysis-bands", result.bands.join(", "));
  put("analysis-variance-text", `${(100 * result.scores.explainedVariance).toFixed(1)}%`);
  put("analysis-line", format(result.scores.lineScore));
  put("analysis-winding", format(result.scores.winding));
  put("analysis-monotonicity", format(result.scores.angularMonotonicity));
  put("analysis-closure", format(result.scores.closure));
  put("analysis-radial", format(result.scores.radialCv));
  put("analysis-roughness", format(result.scores.roughness));
  const meter = document.getElementById("analysis-variance");
  if (meter) meter.value = result.scores.explainedVariance;
}

function applyAutomaticProjection() {
  const source = lightcurve || demo;
  const result = analyzeTrajectory(normalizeLightcurve(source));
  if (result.status !== "ok") {
    const reasons = {
      insufficientBands: "Automatic PCA needs at least three bands with three observations each.",
      noCommonInterval: "Available bands have no common observing interval.",
      rankOne: "The synchronized light curves are effectively one-dimensional.",
      numericalFailure: "Automatic PCA did not converge.",
    };
    setLoadStatus(reasons[result.status] || "Automatic PCA is unavailable for this light curve.", "warning");
    return false;
  }
  coeffs.x = {...result.projection.x};
  coeffs.y = {...result.projection.y};
  coeffs.offsetX = result.projection.offsetX;
  coeffs.offsetY = result.projection.offsetY;
  coeffs.bands = result.projection.bands.slice();
  coeffs.interval = {...result.projection.interval};
  coeffs.source = "automatic-pca";
  xTime = false;
  activeTrajectoryAnalysis = result;
  update();
  renderAnalysisResult(result);
  const excluded = result.excludedBands.length ? ` Excluded: ${result.excludedBands.join(", ")}.` : "";
  setLoadStatus(
    `Automatic PCA projection ready: ${result.bands.length} bands, ${(100 * result.scores.explainedVariance).toFixed(1)}% of standardized variance retained.${excluded}`
  );
  return true;
}

let activeTrajectoryAnalysis = null;
