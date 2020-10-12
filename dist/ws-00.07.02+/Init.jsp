<%@ page import="com.Lomikel.Utils.Info" %>
<%@ page import="com.Lomikel.Utils.NotifierURL" %>

<%
  NotifierURL.notify("", "LomikelWS", Info.release());
  %>
