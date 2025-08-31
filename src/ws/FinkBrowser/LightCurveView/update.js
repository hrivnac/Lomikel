
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
  activeX = M.map(p => p.x);
  activeY = M.map(p => p.y);
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
  
function plotLightCurves(data) {
  let traces = [];
  for (let band of filters) {
    if (data[band] && data[band].times.length > 0) {
      // Filter out zero magnitudes
      let times = [];
      let mags  = [];
      for (let i = 0; i < data[band].times.length; i++) {
        if (data[band].values[i] !== 0) {
          times.push(data[band].times[i]);
          mags.push(data[band].values[i]);
          }
        }
      if (times.length > 0) {
        traces.push({
          x: times,
          y: mags,
          mode: 'lines+markers',
          name: band,
          line: {color: bandColors[band]},
          marker: {size: 6,
                   color: bandColors[band]}
          });
        }
      }
    }  
  Plotly.newPlot("lightcurvePlot", 
                 traces, 
                 {margin: {t: 20},
                  xaxis: {title: "MJD"},
                  yaxis: {title: "Magnitude"},  // mag axis inverted
                  height: 300,
                  legend: {
                    orientation: "h",        // horizontal legend
                    x: 0, y: -0.2,           // place it below the plot
                    bgcolor: "rgba(0,0,0,0)" // transparent background
                    }
                  },
                 {displayModeBar: false      // hide Plotly toolbar
                });
  }

function update(){
  updateFormulas();
  updatePlot();
  }
  
