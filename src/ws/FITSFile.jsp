<%@ page contentType="applicaton/octet-stream" %><%@ page import="java.io.File"%><%@ page import="java.io.FileInputStream"%><%
String fn = request.getParameter("fn");
FileInputStream fis = new FileInputStream(new File(fn));
int ch;
while ((ch = fis.read()) != -1) {
  out.print((char)ch);
  }
fis.close();
%>














