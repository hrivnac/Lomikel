<%@ page import="com.Lomikel.WebService.HBase2Table" %>
<%@ page import="com.astrolabsoftware.FinkBrowser.HBaser.FinkHBaseClient" %>
<%@ page import="com.astrolabsoftware.FinkBrowser.WebService.FinkHBaseColumnsProcessor" %>

<%
  String hbaseRowName = "alert";
  String hbaseRowKey  = "rowkey";
  String alwaysColumns = "i:jd,i:objectId";
  String column4latests = "i:objectId";
  HBase2Table.changeColumnsProcessor(new FinkHBaseColumnsProcessor());
  String timestampField = "jd";
  String dirField       = "direction";
  String vertexName     = "values('lbl').toList().toString().replace(']', '').replace('[', '').replaceAll(' ', '').replaceAll(',', '.')";
  String vertexTitle    = "";
  %>
