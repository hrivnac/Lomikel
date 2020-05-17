<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- JHTools Exception Handler -->
<!-- @author Julius.Hrivnac@cern.ch  -->

<%@ page isErrorPage="true" %>

<%@ page import="java.net.InetAddress" %>
<%@ page import="java.lang.StackTraceElement" %>

<%@ page import="com.JHTools.Utils.Notifier" %>
<%@ page import="com.JHTools.Utils.Info" %>

<html>
  <head><title>JHTools Exception Handler</title></head>
    <body bgcolor="#ffdddd">

      <font color="red">
        <p>
          <b>Nothing found !</b>
          </p>
        <p>If you think it is a problem, report it, please, to the <a href="mailto:<%= Info.manager() %>">service manager</a>.</p>
        </font>
      <hr>

      <%
        String message = "";
        boolean more = true;
        do {
          message += "<p><u><pre>" + exception + "</pre></u><br>";
          StackTraceElement[] stackTrace = exception.getStackTrace();
          message += "<pre>";
          for (StackTraceElement element : stackTrace) {
            message += element + "\n";
            }
          message += "</pre>";
          if (exception.getCause() != null) {
            exception = exception.getCause();
            message += "</p><hr><p>Caused by:<br><br>"; 
            }
          else {
            message += "</p>";
            more = false;
            }
          } while (more);
        String addr = request.getRemoteAddr();
        String host = addr;
        try {
          host = InetAddress.getByName(addr).getCanonicalHostName();
          }
        catch (Exception e) {}                                    
        try {
          Notifier.postMail("ERROR: " + exception.toString(), host + "[" + addr + "]\n\n==================================================\n\n" + message);
          }
        catch (Exception e) {}
        %>

      </body>
    </html>
