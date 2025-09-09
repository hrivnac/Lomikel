// Reset handlers update existing sliders 

let xTime = false;

function resetRandom() {
  xTime = false;
  filters.forEach(f => {
    coeffs.x[f] = +(Math.random() * 2 - 1).toFixed(2);
    coeffs.y[f] = +(Math.random() * 2 - 1).toFixed(2);
  });
  update();
  }
  
function resetZero() {
  xTime = false;
  filters.forEach(f => {
    coeffs.x[f] = 0;
    coeffs.y[f] = 0;
    });
  update();
  }
  
function resetRainbow() {
  xTime = true;
  const rainbow = rainbowCoefficients();
  filters.forEach(f => {
    coeffs.x[f] = rainbow[f];
    coeffs.y[f] = rainbow[f];
    });
  update();
  }

