<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<%@ page import="com.JHTools.WebService.BinaryDataRepository" %>

<head>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
  <meta http-equiv="X-UA-Compatible" content="IE=Edge;chrome=1" > 
  <meta name="viewport" content="width=device-width, initial-scale=1" >
  <link type="image/x-icon" rel="shortcut icon" href="./favicon.ico">
  <link type="text/css" rel="stylesheet" href="js9-3.0/js9support.css">
  <link type="text/css" rel="stylesheet" href="js9-3.0/js9.css">
  <script type="text/javascript" src="js9-3.0/js9prefs.js"></script>
  <script type="text/javascript" src="js9-3.0/js9support.min.js"></script>
  <script type="text/javascript" src="js9-3.0/js9.min.js"></script>
  <script type="text/javascript" src="js9-3.0/js9plugins.js"></script>
  <jsp:useBean id="bdr" class="com.JHTools.WebService.BinaryDataRepository" scope="session" />
  </head>
  
<body>
  <%
    String id = request.getParameter("id");
    String content = bdr.get64(id);
    String name = id.substring(4);
    %>
  <script type="text/javascript" src="js9-3.0/js9prefs.js"></script>
  <script type="text/javascript" src="js9-3.0/js9support.min.js"></script>
  <script type="text/javascript" src="js9-3.0/js9.min.js"></script>
  <script type="text/javascript" src="js9-3.0/plugins/core/menubar.js"></script>
  <script type="text/javascript" src="js9-3.0/plugins/core/info.js"></script>
  <div class="JS9Menubar"></div>
  <div class="JS9Toolbar"></div>
  <div class="JS9"></div>
  <div style="margin-top: 2px;"></div>
  <div class="JS9Colorbar"></div>
  <p><a href='javascript:JS9.Load("<%=content%>");'><%=id%></a></p>
  </body>
  
  