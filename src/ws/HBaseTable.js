async function search() {
  // TBD: add version,size
  var hbase   = document.getElementById("hbase"  ).value;
  var htable  = document.getElementById("htable" ).value;
  var key     = document.getElementById("key"    ).value;
  var krefix  = document.getElementById("krefix" ).value;
  var columns = document.getElementById("columns").value;
  var filters = document.getElementById("filters").value;
  var limit   = document.getElementById("limit"  ).value;
  var period  = document.getElementById("period" ).value;
  var query = "hbase=" + hbase + "&htable=" + htable + "&key=" + key + "&krefix=" + krefix + "&filters=" + filters + "&columns=" + columns + "&limit=" + limit + "&period=" + period;
  $("#hbasetable").load("HBaseTable.jsp?" + query);
  }
  
