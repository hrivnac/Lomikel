<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel FITS View-->
<!-- @author Julius.Hrivnac@cern.ch  -->

<%@ page import="com.Lomikel.HBaser.BinaryDataRepository" %>
<%@ page import="com.Lomikel.Utils.Info" %>

<%@ page import="java.io.File"%>
<%@ page import="java.io.FileOutputStream"%>

<%@ page errorPage="ExceptionHandler.jsp" %>

<meta http-equiv="Content-Type" content="text/html; charset=utf-8">
<meta http-equiv="X-UA-Compatible" content="IE=Edge;chrome=1" > 
<meta name="viewport" content="width=device-width, initial-scale=1">
<link type="image/x-icon" rel="shortcut icon" href="./js9-3.0/favicon.ico">
<link type="text/css" rel="stylesheet" href="js9-3.0/js9support.css">
<link type="text/css" rel="stylesheet" href="js9-3.0/js9.css">
<link type="text/css" rel="stylesheet" href="FITSView.css">
<link rel="apple-touch-icon" href="js9-3.0/images/js9-apple-touch-icon.png">
<script type="text/javascript" src="js9-3.0/js9prefs.js"></script>
<script type="text/javascript" src="js9-3.0/js9support.min.js"></script>
<script type="text/javascript" src="js9-3.0/js9.min.js"></script>
<script type="text/javascript" src="js9-3.0/js9plugins.js"></script>

<jsp:useBean id="h2table" class="com.Lomikel.WebService.HBase2Table" scope="session"/>

<%
  // TBD: should be realy deleted
  String id = request.getParameter("id");
  byte[] content = h2table.repository().get(id);
  String name = id.substring(7);
  new File(Info.tmp() +"/FITS").mkdirs();
  String fn = Info.tmp() + "/FITS/" + name + ".fits";
  File file = new File(fn);
  file.deleteOnExit();      
  FileOutputStream fos = new FileOutputStream(file);
  fos.write(content);
  fos.close();
  String url = "FITSFile.jsp?fn=" + fn;                    
  %>

<div id="fitsview">
  <div class="JS9Menubar" ></div>
  <div class="JS9Toolbar" ></div>
  <div class="JS9"        ></div>
  <div class="JS9Colorbar"></div>
  </div>
  
<script type="text/javascript">
  function init() {
    JS9.Preload("<%=url%>", {colormap:"heat"});
    }
  $(document).ready(function() {
    init();
    });
  </script>    
  
<script type="text/javascript">
  $(document).ready(function() {
    $("#fitsview").draggable({
      handle: "#JS9Menubar",
      opacity: 0.35
      });
    });
  </script>  


















