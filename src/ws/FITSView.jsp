<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel FITS View-->
<!-- @author Julius.Hrivnac@cern.ch  -->

<%@ page import="com.Lomikel.Utils.Info" %>
<%@ page import="com.Lomikel.Utils.SmallHttpClient" %>

<%@ page import="java.io.File"%>
<%@ page import="java.io.FileOutputStream"%>
<%@ page import="java.net.URL"%>
<%@ page import="java.net.HttpURLConnection"%>
<%@ page import="java.io.DataInputStream"%>
<%@ page import="java.util.Base64"%>
<%@ page import="java.net.URLDecoder" %>
<%@ page import="org.apache.log4j.Logger" %>

<%! static Logger log = Logger.getLogger(FITSView_jsp.class); %>

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

<jsp:useBean id="dr" class="com.Lomikel.WebService.DataRepository" scope="session"/>

<%
  // TBD: should be deleted after
  // TBD: parametrise fitsdir
  String id   = request.getParameter("id");
  String fn   = request.getParameter("fn");
  String data = request.getParameter("data");
  String lfn = "";
  File file;
  FileOutputStream fos;
  // DataRepository or data
  if (id != null || data != null) {
    byte[] content = null;
    if (id != null) {
      content = dr.get(id);
      }
    else if (data != null) {
      //content = Base64.getDecoder().decode(URLDecoder.decode(data.replaceAll("%25", "%"), "UTF-8"));
      //content = Base64.getDecoder().decode(URLDecoder.decode(data, "UTF-8"));
      content = Base64.getDecoder().decode(data);
      }
    lfn = Info.tmp() + "/FITS/" + id;
    new File(Info.tmp() +"/FITS").mkdirs();
    file = new File(lfn);
    file.deleteOnExit();      
    fos = new FileOutputStream(file);
    fos.write(content);
    fos.close();
    }
  // HDFS
  else if (fn != null) {
    String fitsdir = "http://localhost:14000/webhdfs/v1/user/hrivnac/fits";
    URL url = new URL(fitsdir + "/" + fn + "?op=OPEN&user.name=hadoop");
    lfn = Info.tmp() + "/FITS/" + fn;
    HttpURLConnection conn = (HttpURLConnection)url.openConnection();
    DataInputStream dis = new DataInputStream(conn.getInputStream());
    new File(Info.tmp() +"/FITS").mkdirs();
    file = new File(lfn);
    file.deleteOnExit();      
    fos = new FileOutputStream(file);
    byte buffer[] = new byte[1024];
    int offset = 0;
    int bytes;
    while ((bytes = dis.read(buffer, offset, buffer.length)) > 0) {
      fos.write(buffer, 0, bytes);
      }
    fos.close();
    }
  String lurl = "FITSFile.jsp?fn=" + lfn;
  %>

<div id="fitsview">
  <div class="JS9Menubar" ></div>
  <div class="JS9Toolbar" ></div>
  <div class="JS9"        ></div>
  <div class="JS9Colorbar"></div>
  </div>
  
<script type="text/javascript">
  function init() {
    JS9.Preload("<%=lurl%>", {colormap:"heat"});
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


















