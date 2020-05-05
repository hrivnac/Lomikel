async function search() {
  // TBD: add version,size
  var hbase   = document.getElementById("hbase"  ).value;
  var table   = document.getElementById("table"  ).value;
  var key     = document.getElementById("key"    ).value;
  var krefix  = document.getElementById("krefix" ).value;
  var columns = document.getElementById("columns").value;
  var filters = document.getElementById("filters").value;
  var limit   = document.getElementById("limit"  ).value;
  var period  = document.getElementById("period" ).value;
  var query = "hbase=" + hbase + "&table=" + table + "&key=" + key + "&krefix=" + krefix + "&filters=" + filters + "&columns=" + columns + "&limit=" + limit + "&period=" + period;
  document.getElementById('hbasetable').innerHTML = await(await fetch("HBaseTable.jsp?" + query)).text();
  }
