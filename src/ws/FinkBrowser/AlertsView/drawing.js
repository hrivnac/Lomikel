// Draw Star
function drawStar(x, y, radius, color, alpha, sparklePhase = 0, sp = 10, animateSparkle = true) {
  const spikes = sp;
  let rot = Math.PI / 2 * 3;
  const step = Math.PI / spikes;
  ctx.beginPath();
  for (let i = 0; i < spikes; i++) {
    const sparkle = animateSparkle
      ? 0.1 * Math.sin(Date.now() * 0.02 + sparklePhase + i)
      : 0;
    const outerRadius = radius * (1 + sparkle);
    const innerRadius = radius / 2;
    ctx.lineTo(x + Math.cos(rot) * outerRadius, y + Math.sin(rot) * outerRadius);
    rot += step;
    ctx.lineTo(x + Math.cos(rot) * innerRadius, y + Math.sin(rot) * innerRadius);
    rot += step;
    }
  ctx.closePath();
  ctx.fillStyle = `rgba(${color},${alpha})`;
  ctx.fill();
  }

// Draw Ecliptic
function generateEclipticPoints(nPoints = 360) {
  const eps = 23.439 * Math.PI/180; // obliquity in radians
  const points = [];
  for (let i = 0; i <= nPoints; i++) {
    const lambda = i * 2 * Math.PI / nPoints; // ecliptic longitude
    const delta = Math.asin(Math.sin(eps) * Math.sin(lambda));
    const alpha = Math.atan2(Math.cos(eps) * Math.sin(lambda), Math.cos(lambda));
    // Convert to degrees
    let ra = alpha * 180 / Math.PI;
    if (ra < 0) ra += 360;
    const dec = delta * 180 / Math.PI;
    points.push([ra, dec]);
    }
  return points;
  }

function generateEclipticMonths() {
  const months = ["Mar", "Feb", "Jan", "Dec", "Nov", "Oct", "Sep", "Aug", "Jul", "Jun", "May", "Apr"];
  return months.map((month, i) => {
    const {ra, dec} = eclipticToEquatorial(25 + i * 30);
    return {month, ra, dec};
    });
  }

function generateGalacticPlanePoints() {
  const points = [];
  for (let l = 0; l <= 360; l += 1) {
    const {ra, dec} = galacticToEquatorial(l, 0);
    points.push([ra, dec]);
    }
  return points;
  }

const staticSkyGeometry = {
  ecliptic: generateEclipticPoints(360),
  eclipticMonths: generateEclipticMonths(),
  galacticPlane: generateGalacticPlanePoints()
  };

function drawEcliptic() {
  const points = staticSkyGeometry.ecliptic.map(([ra, dec]) => raDecToXY(ra, dec));
  ctx.save();
  ctx.strokeStyle = "rgb(200,200,100,0.5)";
  ctx.lineWidth = 1.5;
  drawProjectedPolyline(points);
  ctx.restore();
  }
  
// Draw Ecliptic Months
function drawEclipticMonths() {
  ctx.save();
  ctx.fillStyle = "rgba(255,215,0,0.8)";
  ctx.font = "12px Arial";
  ctx.textAlign = "center";
  staticSkyGeometry.eclipticMonths.forEach(({month, ra, dec}) => {
    const pos = raDecToXY(ra, dec);
    for (const wrappedPosition of getWrappedScreenPositions(pos, 20)) {
      ctx.fillText(month, wrappedPosition.x, wrappedPosition.y);
      }
    });
  ctx.restore();
  }  
  
// Draw Galactic
function drawGalacticPlane() {
  const points = staticSkyGeometry.galacticPlane.map(([ra, dec]) => raDecToXY(ra, dec));
  ctx.save();
  ctx.strokeStyle = "rgba(100,200,200,0.5)";
  ctx.lineWidth = 2;
  drawProjectedPolyline(points);
  ctx.restore();
  }

function drawProjectedPolyline(points) {
  for (const segment of splitProjectedPolyline(points, canvas.width)) {
    if (segment.length < 2) continue;
    ctx.beginPath();
    ctx.moveTo(segment[0].x, segment[0].y);
    for (const point of segment.slice(1)) ctx.lineTo(point.x, point.y);
    ctx.stroke();
    }
  }

// Draw Stars
function drawCatalogStarAt(x, y, star, radius) {
  ctx.beginPath();
  ctx.arc(x, y, radius, 0, Math.PI * 2);
  ctx.font = "10px sans-serif";
  ctx.fillStyle = `rgba(255,255,255,${star.alpha})`;
  if (star.r > 2.5) {
    ctx.fillText(star.proper, x + 5, y - 5);
    }
  ctx.fill();
  }

function drawStars() {
  for (const s of stars) {
    const pos = raDecToXY(s.ra, s.dec);
    if (!prefersReducedMotion) {
      s.alpha += s.twinkleSpeed * (Math.random() < 0.5 ? 1 : -1);
      s.alpha = Math.max(0.3, Math.min(1, s.alpha));
      }
    const radius = s.r * camera.currentZoom;
    forEachWrappedScreenPosition(pos, radius + 100, drawCatalogStarAt, s, radius);
    }
  }
  
// Draw Constellations
function drawConstellations() {
  try {
    ctx.save();
    ctx.strokeStyle = "rgba(100,100,255,1)";
    ctx.lineWidth = 1;
    constellations.features.forEach(feature => {
      const multiLine = feature.geometry.coordinates;
      multiLine.forEach(line => {
        for (let i = 0; i < line.length - 1; i++) {
          const [ra1, dec1] = line[i    ];
          const [ra2, dec2] = line[i + 1];
          const p1 = raDecToXY(ra1, dec1);
          const p2 = raDecToXY(ra2, dec2);
          if (Math.abs(p1.x - p2.x) < canvas.width / 2) {
            ctx.beginPath();
            ctx.moveTo(p1.x, p1.y);
            ctx.lineTo(p2.x, p2.y);
            ctx.stroke();
            }
          }
        });
      });
    ctx.restore();
    }
  catch (e) {}
  }
  
// Draw Constellation Labels
function drawConstellationLabels() {
  try {
  ctx.save();
  ctx.fillStyle = "rgba(100,100,255,1)";
  ctx.font = "12px Arial";
  ctx.textAlign = "center";
  constellations.features.forEach(feature => {
    const name = feature.id || feature.properties?.name || "*";
    const points = [];
    feature.geometry.coordinates.forEach(multiLine => {
      multiLine.forEach(coord => {
        points.push(coord);
        });
      });
    if (points.length === 0) return;
    let sumRA = 0, sumDec = 0;
    points.forEach(([ra, dec]) => sumRA += ra);
    points.forEach(([ra, dec]) => sumDec += dec);
    const avgRA = sumRA / points.length;
    const avgDec = sumDec / points.length;
    const pos = raDecToXY(avgRA, avgDec);
    for (const wrappedPosition of getWrappedScreenPositions(pos, 100)) {
      ctx.fillText(name, wrappedPosition.x, wrappedPosition.y);
      }
    });
    ctx.restore();
    }
  catch(e){}
  }
  
// Update Legend
let legendSignature = null;
function updateLegend() {
  const legend = document.getElementById('legend');
  const activeClasses = [...new Set(flashes.map(flash => flash.alert.class))].sort();
  const signature = activeClasses.join("\u0000");
  if (signature === legendSignature) return;
  legendSignature = signature;
  const items = activeClasses.map(cls => {
    const rgb = classes[cls] || "255,255,255";
    const item = document.createElement('div');
    const swatch = document.createElement('span');
    swatch.style.backgroundColor = `rgb(${rgb})`;
    swatch.setAttribute?.("aria-hidden", "true");
    const label = document.createElement('span');
    label.textContent = cls;
    item.append(swatch, label);
    return item;
    });
  legend.replaceChildren(...items);
  }
