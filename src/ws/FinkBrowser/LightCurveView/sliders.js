function initSliders(){
  const panel = document.getElementById('sliders');
  panel.innerHTML = '';
  filters.forEach(f => {
    // random initial values for more interesting start
    coeffs.x[f] = + (Math.random() * 2 - 1).toFixed(2);
    coeffs.y[f] = + (Math.random() * 2 - 1).toFixed(2);
    // X slider
    const rowX = document.createElement('div');
    rowX.className = 'slider-row';
    const labX = document.createElement('label');
    labX.textContent = `${f} (x)`;
    labX.style.color = bandColors[f];
    const sX = document.createElement('input');
    sX.type = 'range';
    sX.min = -2;
    sX.max = 2;
    sX.step = 0.01;
    sX.value = coeffs.x[f];
    sX.id = `x_${f}`;
    sX.style.accentColor = bandColors[f]; // works in modern browsers
    sX.addEventListener('input', () => {
      coeffs.x[f] = + sX.value;
      update();
      });
    rowX.appendChild(labX);
    rowX.appendChild(sX);
    panel.appendChild(rowX);
    // Y slider
    const rowY = document.createElement('div');
    rowY.className = 'slider-row';
    const labY = document.createElement('label');
    labY.textContent = `${f} (y)`;
    labY.style.color = bandColors[f];
    const sY = document.createElement('input');
    sY.type = 'range'; 
    sY.min = -2;
    sY.max = 2;
    sY.step = 0.01;
    sY.value = coeffs.y[f];
    sY.id = `y_${f}`;
    sY.style.accentColor = bandColors[f]; // works in modern browsers
    sY.addEventListener('input', () => {
      coeffs.y[f] = +sY.value;
      update();
      });
    rowY.appendChild(labY);
    rowY.appendChild(sY);
    panel.appendChild(rowY);
    });
  }
