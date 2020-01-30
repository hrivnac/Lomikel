$(function() {
  var onSampleResized = function(e) {
    var columns = $(e.currentTarget).find("td");
    var rows = $(e.currentTarget).find("tr");
    var columnsize;
    var rowsize;
    columns.each(function() {
      columnsize += $(this).attr('id') + "" + $(this).width() + "" + $(this).height() + ";";
      });
    rows.each(function () {
      rowsize += $(this).attr('id') + "" + $(this).width() + "" + $(this).height() + ";";
      });
    document.getElementById("hf_columndata").value = columnsize;
    document.getElementById("hf_rowdata").value = rowsize;
    };
  $("#Table tr").resizable();
  $("#Table td").resizable();
  });
