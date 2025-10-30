function makeDraggable(header, panel) {
  let offsetX, offsetY, isDown = false;
  header.addEventListener('mousedown', e => {isDown = true;
                                             offsetX = e.clientX - panel.offsetLeft;
                                             offsetY = e.clientY - panel.offsetTop;
                                             });
  window.addEventListener('mouseup', () => isDown = false);
  window.addEventListener('mousemove', e => {if (!isDown) return;
                                             panel.style.left = (e.clientX - offsetX) + 'px';
                                             panel.style.top = (e.clientY - offsetY) + 'px';
                                             });
  }
makeDraggable(document.getElementById("controls-header"), document.getElementById("controls"));
makeDraggable(document.getElementById("list-header"),     document.getElementById("colistntrols"));

const nmaxSlider = document.getElementById("nmax");

function sliderToNmax(t) {
  // t in [0,1]
  if (t <= 0.5) {
    // left half maps linearly to 0..1
    return t * 2.0;
    }
  else {
    // right half maps logarithmically to 1..20
    const u = (t - 0.5) / 0.5; // 0..1
    return Math.pow(20, u);   // 20^0..20^1 -> 1..20
    }
  }
  
nmaxSlider.oninput = () => {const t = parseFloat(nmaxSlider.value);
                            let nmax = sliderToNmax(t);
                            if (nmax > 1) nmax = Math.round(nmax); // integers in 1..20
                            // format display
                            const disp = (nmax > 1) ? String(nmax) : nmax.toFixed(2).replace(/\.?0+$/, '');
                            document.getElementById("nmaxValue").textContent = disp;
                            };
nmaxSlider.dispatchEvent(new Event('input'));
