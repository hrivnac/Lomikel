// Keep all layers in physical RA and reverse only the screen direction.
function normalizeRa(ra) {
  return ((Number(ra) % 360) + 360) % 360;
  }

function signedRaDelta(ra, center) {
  return ((normalizeRa(ra) - normalizeRa(center) + 540) % 360) - 180;
  }

function getCircularRaBounds(ras) {
  const values = ras.map(normalizeRa).sort((a, b) => a - b);
  if (values.length === 0) return null;
  if (values.length === 1) return {center: values[0], span: 0};
  let largestGap = -1;
  let largestGapIndex = -1;
  for (let i = 0; i < values.length; i++) {
    const next = (i + 1 < values.length) ? values[i + 1] : values[0] + 360;
    const gap = next - values[i];
    if (gap > largestGap) {
      largestGap = gap;
      largestGapIndex = i;
      }
    }
  const start = values[(largestGapIndex + 1) % values.length];
  const span = 360 - largestGap;
  return {center: normalizeRa(start + span / 2), span};
  }

function interpolateRa(current, target, amount) {
  return normalizeRa(current + signedRaDelta(target, current) * amount);
  }

function raDecToXY(ra, dec) {
  const dx = signedRaDelta(ra, camera.currentCenter.ra) / 360;
  const dy = (dec - camera.currentCenter.dec) / 180;
  return {
    x: canvas.width / 2 - dx * canvas.width * camera.currentZoom,
    y: canvas.height / 2 - dy * canvas.height * camera.currentZoom
    };
  }

function forEachWrappedScreenPosition(position, padding, callback, data1, data2) {
  const period = canvas.width * camera.currentZoom;
  if (!(period > 0)) {
    callback(position.x, position.y, data1, data2);
    return;
    }
  const firstCopy = Math.ceil((-padding - position.x) / period);
  const lastCopy = Math.floor((canvas.width + padding - position.x) / period);
  for (let copy = firstCopy; copy <= lastCopy; copy++) {
    callback(position.x + copy * period, position.y, data1, data2);
    }
  }

function getWrappedScreenPositions(position, padding = 0) {
  const positions = [];
  forEachWrappedScreenPosition(
    position,
    padding,
    (x, y) => positions.push({...position, x, y})
    );
  return positions;
  }

function splitProjectedPolyline(points, viewportWidth) {
  const segments = [];
  let segment = [];
  for (const point of points) {
    const previous = segment[segment.length - 1];
    if (previous && Math.abs(point.x - previous.x) >= viewportWidth / 2) {
      segments.push(segment);
      segment = [];
      }
    segment.push(point);
    }
  if (segment.length > 0) segments.push(segment);
  return segments;
  }

function galacticToEquatorial(lDeg, bDeg) {
  const l = lDeg * Math.PI/180;
  const b = bDeg * Math.PI/180;
  const sinDec = Math.sin(b)*Math.sin(deltaGP) + Math.cos(b)*Math.cos(deltaGP)*Math.sin(l - lOmega);
  const dec = Math.asin(sinDec);
  const y = Math.cos(b)*Math.cos(l - lOmega);
  const x = Math.sin(b)*Math.cos(deltaGP) - Math.cos(b)*Math.sin(deltaGP)*Math.sin(l - lOmega);
  let ra = Math.atan2(y, x) + alphaGP;
  // ensure 0–360°
  ra = (ra*180/Math.PI + 360) % 360;
  const decDeg = dec*180/Math.PI;
  return {ra, dec: decDeg};
  }

// λ in degrees along ecliptic, β = 0 for the Sun's path
function eclipticToEquatorial(lambdaDeg) {
  const lambda = lambdaDeg * Math.PI/180;
  const beta = 0;
  const sinDec = Math.sin(beta)*Math.cos(epsilon) + Math.cos(beta)*Math.sin(epsilon)*Math.sin(lambda);
  const dec = Math.asin(sinDec);
  const y = Math.sin(lambda) * Math.cos(epsilon) - Math.tan(beta) * Math.sin(epsilon);
  const x = Math.cos(lambda);
  const ra = Math.atan2(y, x);
  return {
    ra: (ra*180/Math.PI + 360)%360,
    dec: dec*180/Math.PI
    };
  }

function formatStartDateUtc(hours, now = new Date()) {
  const past = new Date(now.getTime() - hours * 60 * 60 * 1000);
  return past.toISOString().slice(0, 19).replace('T', ' ');
  }

function getStartDateParam() {
  getQueryParams();
  return formatStartDateUtc(fetchStart);
  }

function getPortalUrl(alert) {
  const portalOrigins = {
    ZTF: "https://ztf.fink-portal.org/",
    LSST: "https://lsst.fink-portal.org/"
    };
  const origin = portalOrigins[alert.survey];
  if (!origin || alert.objectId === undefined || alert.objectId === null) return null;
  return origin + encodeURIComponent(String(alert.objectId));
  }
  
function getQueryParams() {
  const params = new URLSearchParams(window.location.search);
  const boundedInt = (name, current, minimum, maximum) => {
    const value = Number.parseInt(params.get(name), 10);
    return Number.isFinite(value) ? Math.min(maximum, Math.max(minimum, value)) : current;
    };
  fetchPeriod = boundedInt("fetchPeriod", fetchPeriod, 1, 1440);
  fetchStart  = boundedInt("fetchStart",  fetchStart,  1, 720);
  nAlerts     = boundedInt("nAlerts",     nAlerts,     1, 100);
  magMax      = boundedInt("magMax",      magMax,     -2, 6);
  const lsstParam = params.get("fetchLSST");
  if (lsstParam !== null) {
    fetchLSST = ["1", "true", "yes"].includes(lsstParam.toLowerCase());
    }
  }
  
  
  