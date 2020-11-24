function showSkyView(dataS, gMapS, name, xS, yS, zS, sS) {
  Celestial.display({
    form: true,
    formFields: {download: true},
    datapath: "../d3-celestial-0.7.32/data/",
    stars: {propername: true}
    });
  }