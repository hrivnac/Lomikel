let plotUpdateScheduled = false;

function schedulePlotUpdate() {
  if (plotUpdateScheduled) return;
  plotUpdateScheduled = true;
  requestAnimationFrame(() => {
    plotUpdateScheduled = false;
    updatePlot();
    });
  }

function updateFormulas() {
  let fx, fy;
  const curve = lightcurve || demo;
  const selectedBands = Array.isArray(coeffs.bands) ? coeffs.bands : filters;
  const availableBands = filters.filter(f => selectedBands.includes(f)
    && curve?.[f]?.times?.length && curve?.[f]?.values?.length);
  const terms = axis => availableBands.map(f => `${(coeffs[axis][f] ?? 0).toPrecision(5)}·${f}`).join(" + ");
  if (xTime) {
    fx = "x = ΔMJD";
    }
  else {
    fx = availableBands.length
      ? `x = ${(coeffs.offsetX || 0).toFixed(3)} + ${terms("x")}`
      : "x = no available bands";
    }
  fy = availableBands.length
    ? `y = ${(coeffs.offsetY || 0).toFixed(3)} + ${terms("y")}`
    : "y = no available bands";
  document.getElementById('formulaX').textContent = fx;
  document.getElementById('formulaY').textContent = fy;
  }

function updatePlot() {
  if (lightcurve) {
    demo = lightcurve;
    }
  const {L, M, R, startJD, endJD, missingBands = []} = projectXY(demo, coeffs);
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
          title:'ΔMJD (days)',
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
  const annotations = M.length ? [] : [{
    x: 0.5, y: 0.5, xref: "paper", yref: "paper", showarrow: false,
    text: missingBands.length === filters.length
      ? "No light curves are available for projection."
      : "The available bands have no common observing interval.",
    }];
  Plotly.react('plot', traces, {
    margin:{t:24},
    xaxis:{title:'X'},
    yaxis:{title:'Y'},
    legend:{orientation:'h'},
    annotations
    }, {responsive: true});
  }
  
function plotLightCurves(data) {
  const observedTimes = filters.flatMap(f => data[f] ? data[f].times : []);
  if (!observedTimes.length) {
    Plotly.react("lightcurvePlot", [], {height: 300}, {displayModeBar: false, responsive: true});
    return;
    }
  const minMJD = Math.min(...observedTimes);
  const totalPoints = filters.reduce((count, band) => count + (data[band]?.times.length || 0), 0);
  const traceType = totalPoints > 2000 ? "scattergl" : "scatter";
  const traces = [];
  for (const band of filters) {
    if (data[band] && data[band].times.length > 0) {
      traces.push({
        type: traceType,
        x: data[band].times.map(t => t - minMJD),
        y: data[band].values,
        mode: "lines+markers",
        name: band,
        line: {color: bandColors[band]},
        marker: {size: 6, color: bandColors[band]},
        });
      }
    }
  Plotly.react("lightcurvePlot",
               traces,
               {margin: {t: 20},
                xaxis: {title: "ΔMJD (days)"},
                yaxis: {title: "Magnitude", autorange: "reversed"},
                height: 300,
                legend: {
                  orientation: "h",
                  x: 0, y: -0.2,
                  bgcolor: "rgba(0,0,0,0)"
                  }
                },
               {displayModeBar: false, responsive: true});
  }
  
function updateSlidersFromCoeffs() {
  if (!window.sliderHandles) return;
  const {handles} = window.sliderHandles;
  handles.forEach(updateCoefficientHandle);
  }

function update(){
  updateFormulas();
  updatePlot();
  updateSlidersFromCoeffs();
  }
  
