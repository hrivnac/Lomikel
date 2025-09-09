// Reset handlers update existing sliders 

let xTime = false;

function resetRandom() {
  xTime = false;
  filters.forEach(f => {
    coeffs.x[f] = +(Math.random() * 2 - 1).toFixed(2);
    coeffs.y[f] = +(Math.random() * 2 - 1).toFixed(2);
    document.getElementById(`x_${f}`).value = coeffs.x[f];
    document.getElementById(`y_${f}`).value = coeffs.y[f];
    });
  update();
  }
  
function resetZero() {
  xTime = false;
  filters.forEach(f => {
    coeffs.x[f] = 0;
    coeffs.y[f] = 0;
    document.getElementById(`x_${f}`).value = 0;
    document.getElementById(`y_${f}`).value = 0;
    });
  update();
  }
  
function resetRainbow() {
  xTime = true;
  const rainbow = rainbowCoefficients();
  filters.forEach(f => {
    coeffs.x[f] = rainbow[f];
    coeffs.y[f] = rainbow[f];
    document.getElementById(`x_${f}`).value = coeffs.x[f];;
    document.getElementById(`y_${f}`).value = coeffs.y[f];
    });
  update();
  }

