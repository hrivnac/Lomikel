<div id="hbaseTableSelector" style="width:100%; background-color:#eeeeee;">

  <button onClick="w2popup.load({url:'Help-HBaseCustom.html', showMax: true})" style="position:absolute; right:0">
    <img src="images/Help.png" width="10"/>
    </button>

  <style>
    #slider-ra, #slider-dec, #slider-ra0, #slider-dec0, #slider-del {
      float: left;
      clear: left;
      width: 300px;
      margin: 15px;
      }
    #slider-ra   .ui-slider-range  {background:   #ef2929;}
    #slider-ra   .ui-slider-handle {border-color: #ef2929;}
    #slider-dec  .ui-slider-range  {background:   #8ae234;}
    #slider-dec  .ui-slider-handle {border-color: #8ae234;}
    #slider-ra0  .ui-slider-range  {background:   #ef2929;}
    #slider-ra0  .ui-slider-handle {border-color: #ef2929;}
    #slider-dec0 .ui-slider-range  {background:   #8ae234;}
    #slider-dec0 .ui-slider-handle {border-color: #8ae234;}
    #slider-del  .ui-slider-range  {background:   #4444ee;}
    #slider-del  .ui-slider-handle {border-color: #4444ee;}
    </style>
  
  <table style="background-color:#eeeeee">
    <tr><td rowspan="2"><b>Area<br/>Selectors</b></td>
        <td><label for="amount-ra">ra <small>[deg]</small></label></td>
        <td><input type="text" id="amount-ra"  readonly style="border:0; color:#ef2929; font-weight:bold;"><div id="slider-ra" ></div></td></tr>
    <tr><td><label for="amount-dec">dec <small>[deg]</small></label></td>
        <td><input type="text" id="amount-dec" readonly style="border:0; color:#8ae234; font-weight:bold;"><div id="slider-dec"></div></td></tr>
    <tr><td rowspan="3"><b>Cone<br/>Selectors</b></td>
        <td><label for="amount-ra0">ra <small>[deg]</small></label></td>
        <td><input type="text" id="amount-ra0"  readonly style="border:0; color:#ef2929; font-weight:bold;"><div id="slider-ra0" ></div></td></tr>
    <tr><td><label for="amount-dec0">dec <small>[deg]</small></label></td>
        <td><input type="text" id="amount-dec0" readonly style="border:0; color:#8ae234; font-weight:bold;"><div id="slider-dec0"></div></td></tr>
    <tr><td><label for="amount-del">del <small>[sec]</small></label></td>
        <td><input type="text" id="amount-del" readonly style="border:0; color:#4444ee; font-weight:bold;"><div id="slider-del"></div></td></tr>
    <tr><td colspan="2"><b>Free-form<br/>Selector</b></td>
        <td><input type="text" id="ffselector" name="ffselector" size="60"></td></tr>
    <tr><td colspan="2"><b>other<br/>columns</b></td>
        <td><input type="text" id="othercol" name="othercol" size="60">&nbsp;(all gives all columns)</td></tr>
    </table>
    
  <input type='button' onclick='searchRanges("<%=url%>")' value='Search Ranges' style="background-color:#dddddd">

  <hr/>
  
  </div>
