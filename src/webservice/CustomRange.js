if (typeof hideHBaseSelector === 'undefined' || !hideHBaseSelector) {
  $(function() {
    $("#slider-ra").slider({
      orientation: "horizontal",
      range: true,
      min: 0,
      max: 360,
      values: [0, 360],
      slide: function(event, ui) {
        $("#amount-ra").val(ui.values[0] + " - " + ui.values[1]);
        }
      });
    $("#amount-ra").val($("#slider-ra").slider("values", 0) + " - " +
                        $("#slider-ra").slider("values", 1)); 
    $("#slider-dec").slider({
      orientation: "horizontal",
      range: true,
      min: -90,
      max: 90,
      values: [-90, 90],
      slide: function(event, ui) {
        $("#amount-dec").val(ui.values[0] + " - " + ui.values[1]);
        }
      });
    $("#amount-dec").val($("#slider-dec").slider("values", 0) + " - " +
                         $("#slider-dec").slider("values", 1));
    $("#slider-ra0").slider({
      orientation: "horizontal",
      range: false,
      min: 0,
      max: 360,
      value: 180,
      slide: function(event, ui) {
        $("#amount-ra0").val(ui.value);
        }
      });
    $("#amount-ra0").val($("#slider-ra0").slider("value")); 
    $("#slider-dec0").slider({
      orientation: "horizontal",
      range: false,
      min: -90,
      max: 90,
      value: 0,
      slide: function(event, ui) {
        $("#amount-dec0").val(ui.value);
        }
      });
    $("#amount-dec0").val($("#slider-dec0").slider("value"));
    $("#slider-del").slider({
      orientation: "horizontal",
      range: false,
      min: 0,
      max: 180,
      value: 0,
      slide: function(event, ui) {
        $("#amount-del").val(ui.value);
        }
      });
    $("#amount-del").val($("#slider-del").slider("value"));
    });
  }
else {
  document.getElementById("hbaseTableSelector").style.display = 'none';
  }
