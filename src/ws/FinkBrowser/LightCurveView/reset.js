// Reset handlers update existing sliders 

function resetRandom() {
  filters.forEach(f => {
    coeffs.x[f] = +(Math.random() * 2 - 1).toFixed(2);
    coeffs.y[f] = +(Math.random() * 2 - 1).toFixed(2);
    document.getElementById(`x_${f}`).value = coeffs.x[f];
    document.getElementById(`y_${f}`).value = coeffs.y[f];
    });
  update();
  }
  
function resetZero() {
  filters.forEach(f => {
    coeffs.x[f] = 0;
    coeffs.y[f] = 0;
    document.getElementById(`x_${f}`).value = 0;
    document.getElementById(`y_${f}`).value = 0;
    });
  update();
  }

