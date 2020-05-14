<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<%@ page import="com.JHTools.WebService.BinaryDataRepository" %>

<%@ page import="java.io.File"%>
<%@ page import="java.io.FileOutputStream"%>

<html>
  <head>
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
    <title>JS9-Viewer</title>
    <jsp:useBean id="bdr" class="com.JHTools.WebService.BinaryDataRepository" scope="session" />
    </head>
    
  <body>
  
    <%
      // TBD: should be realy deleted
      String id = request.getParameter("id");
      byte[] content = bdr.get(id);
      String name = id.substring(4);
      new File("/tmp/FinkBrowser/FITS").mkdirs();
      String fn = "/tmp/FinkBrowser/FITS/" + name + ".fits";
      File file = new File(fn);
      file.deleteOnExit();      
      FileOutputStream fos = new FileOutputStream(file);
      fos.write(content);
      fos.close();
      String url = "FITSFile.jsp?fn=" + fn;                    
      %>
      
    <div id="centerdiv">
    <div class="JS9Menubar"  data-width="768px" data-height="54px"></div>
    <div class="JS9Toolbar"  data-width="768px" data-height="54px"></div>
    <div class="JS9"         data-width="768px" data-height="768px"></div>
    <div class="JS9Colorbar" data-width="768px" data-height="54px"></div>
      
    <script type="text/javascript">
      function init(){
        JS9.Preload("<%=url%>", {colormap:"heat"});
        }
      $(document).ready(function() {
        init();
        });
      </script>    
      
    <script type="text/javascript">
      $(document).ready(function() {
        $("#centerdiv").draggable({
          handle: "#JS9Menubar",
          opacity: 0.35
          });
        });
      </script>  
      
    </body>
  
  </html>




















