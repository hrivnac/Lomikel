// ra*dec to x*
function raDecToXY(ra, dec, renorm = false) {
  if (renorm) {
    if (ra < 0) {
      ra = -ra;
      }
    else {
      ra = 360 - ra;
      }
    }
  const dx = (ra - camera.currentCenter.ra) / 360;
  const dy = (dec - camera.currentCenter.dec) / 180;
  return {
    x: canvas.width / 2 + dx * canvas.width * camera.currentZoom,
    y: canvas.height / 2 - dy * canvas.height * camera.currentZoom
    };
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
  return { ra, dec: decDeg };
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
