
function updateFormulas(){
  const fx = 'x = ' + filters.map(f => `${coeffs.x[f].toFixed(2)}·${f}`).join(' + ');
  const fy = 'y = ' + filters.map(f => `${coeffs.y[f].toFixed(2)}·${f}`).join(' + ');
  document.getElementById('formulaX').textContent = fx;
  document.getElementById('formulaY').textContent = fy;
  }
  
function updatePlot() {
  if (lightcurve != "") {
    demo = lightcurve;
    }
  const {L, M ,R , startJD, endJD} = projectXY(demo, coeffs);
  const traces = [];
  //if (L.length){
  //  traces.push({
  //    x: L.map(p => p.x),
  //    y: L.map(p => p.y),
  //    mode:'lines',
  //    line:{dash:'dot'},
  //    name:'Extrapolated (left)'
  //    });
  //  }
  if (M.length){
    traces.push({
      x: M.map(p => p.x),
      y: M.map(p => p.y),
      mode:'lines+markers',
      line: {color:'grey'},
      marker: {
        color: M.map(p => p.t),
        colorscale:'Viridis',
        size:6,
        colorbar: {
          title:'JD',
          len:0.5
          }
        },
      name:'Interpolated'
      });
    // endpoints (first/last of mid)
    traces.push({
      x:[M[0].x],
      y:[M[0].y],
      mode:'markers',
      marker: {
        color:'green',
        size:10},
        name:'First'
        });
    traces.push({
      x:[M[M.length - 1].x],
      y:[M[M.length - 1].y],
      mode:'markers',
      marker: {
        color:'red',
        size:10
        },
      name:'Last'
      });
    }
  //if (R.length){
  //  traces.push({
  //    x: R.map(p => p.x),
  //    y: R.map(p => p.y),
  //    mode:'lines',
  //    line:{
  //      dash:'dot'
  //      },
  //    name:'Extrapolated (right)'
  //    });
  //  }
  Plotly.newPlot('plot', traces, {
    margin:{t:24},
    xaxis:{title:'X'},
    yaxis:{title:'Y'},
    legend:{orientation:'h'}
    });
  }

function update(){
  updateFormulas();
  updatePlot();
  }