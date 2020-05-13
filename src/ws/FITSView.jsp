<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<%@ page import="com.JHTools.WebService.BinaryDataRepository" %>

<html>
  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=Edge;chrome=1" > 
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link type="image/x-icon" rel="shortcut icon" href="./js9-3.0/favicon.ico">
    <link type="text/css" rel="stylesheet" href="js9-3.0/js9support.css">
    <link type="text/css" rel="stylesheet" href="js9-3.0/js9.css">
    <link rel="apple-touch-icon" href="js9-3.0/images/js9-apple-touch-icon.png">
    <script type="text/javascript" src="js9-3.0/js9prefs.js"></script>
    <script type="text/javascript" src="js9-3.0/js9support.min.js"></script>
    <script type="text/javascript" src="js9-3.0/js9.min.js"></script>
    <script type="text/javascript" src="js9-3.0/js9plugins.js"></script>
    <style type="text/css">
      #centerdiv {
        position: absolute;
        margin: auto;
        top: 0;
        right: 0;
        bottom: 0;
        left: 0;
        width: 512px;
        height: 512px;
        }
      </style>
    <title>JS9-Viewer</title>
    <jsp:useBean id="bdr" class="com.JHTools.WebService.BinaryDataRepository" scope="session" />
    </head>
    
  <body>
    <%
      String id = request.getParameter("id");
      String content = bdr.get64(id);
      String name = id.substring(4);
      %>
    <div id="centerdiv">
    <div class="JS9Menubar"></div>
    <div class="JS9"></div>
    <div style="margin-top: 2px;"><div class="JS9Colorbar"></div></div>
      <p><a href='javascript:JS9.Load("stamp.fits");'>test</a></p>
      <p><a href='javascript:JS9.Load("<%=content%>");'><%=name%></a></p>
      </div>
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




















