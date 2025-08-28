// Draw Star
function drawStar(x, y, radius, color, alpha, sparklePhase = 0) {
  const spikes = 10;
  let rot = Math.PI / 2 * 3;
  const step = Math.PI / spikes;
  ctx.beginPath();
  for (let i = 0; i < spikes; i++) {
    const sparkle = 0.1 * Math.sin(Date.now() * 0.02 + sparklePhase + i);
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
    points.push([ra, -dec]);
    }
  return points;
  }  
function drawEcliptic() {
  const points = generateEclipticPoints(360);
  ctx.save();
  ctx.strokeStyle = "rgb(200,200,100,0.5)";
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  points.forEach(([ra, dec], idx) => {
    const p = raDecToXY(ra, dec);
    if (idx === 0) ctx.moveTo(p.x, p.y);
    else ctx.lineTo(p.x, p.y);
    });
  ctx.stroke();
  ctx.restore();
  }
  
// Draw Ecliptic Months
function drawEclipticMonths() {
  const months = ["Mar", "Feb","Jan","Dec","Nov","Oct","Sep","Aug","Jul","Jun","May","Apr"];
  ctx.save();
  ctx.fillStyle = "rgba(255,215,0,0.8)";
  ctx.font = "12px Arial";
  ctx.textAlign = "center";
  months.forEach((month, i) => {
    const lambda = 25 + i * 30; // 30° per month
    const {ra, dec} = eclipticToEquatorial(lambda);
    const pos = raDecToXY(ra, -dec);
    ctx.fillText(month, pos.x, pos.y);
    });
  ctx.restore();
  }  
  
// Draw Galactic
function drawGalacticPlane() {
  const points = [];
  const step = 1; // 1° step in l
  for (let l = 0; l <= 360; l += step) {
    const {ra, dec} = galacticToEquatorial(l, 0);raDecToXY
    points.push(raDecToXY(ra-20, dec));
    }
  ctx.save();
  ctx.strokeStyle = "rgba(100,200,200,0.5)";
  ctx.lineWidth = 2;
  ctx.beginPath();
  points.forEach((p, i) => {
    if (i === 309) ctx.moveTo(p.x, p.y);
    else ctx.lineTo(p.x, p.y);
    });
  ctx.stroke();
  ctx.restore();
  }

// Draw Stars
function drawStars() {
  for (const s of stars) {
    const pos = raDecToXY(360 - s.ra, s.dec);
    s.alpha += s.twinkleSpeed * (Math.random() < 0.5 ? 1 : -1);
    s.alpha = Math.max(0.3, Math.min(1, s.alpha));
    ctx.beginPath();
    ctx.arc(pos.x, pos.y, s.r * camera.currentZoom, 0, Math.PI * 2);
    ctx.font = "10px sans-serif";
    ctx.fillStyle = `rgba(255,255,255,${s.alpha})`;
    if (s.r > 2.5) {
      ctx.fillText(s.proper, pos.x + 5, pos.y - 5);
      }
    ctx.fill();
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
          const p1 = raDecToXY(ra1, dec1, true);
          const p2 = raDecToXY(ra2, dec2, true);     
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
    const pos = raDecToXY(avgRA, avgDec, true);
    ctx.fillText(name, pos.x, pos.y);
    });
    ctx.restore();
    }
  catch(e){}
  }
