<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- JHTools Exception Handler -->
<!-- @author Julius.Hrivnac@cern.ch  -->

<%@ page isErrorPage="true" %>

<%@ page import="java.net.InetAddress" %>
<%@ page import="java.lang.StackTraceElement" %>

<%@ page import="com.JHTools.Utils.NotifierMail" %>
<%@ page import="com.JHTools.Utils.NotifierURL" %>
<%@ page import="com.JHTools.Utils.Info" %>
<%@ page import="com.JHTools.Utils.CommonException" %>

<%@ page import="org.apache.log4j.Logger" %>

<%! static Logger log = Logger.getLogger(ExceptionHandler_jsp.class); %>

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
        if (exception == null) {
          exception = new CommonException("No known Exception");
          }
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
        catch (Exception e) {
          log.error("Cannot find host", e);
          }                                    
        //try {
        //  NotifierMail.postMail("ERROR: " + exception.toString(), host + "[" + addr + "]\n\n==================================================\n\n" + message);
        //  }
        //catch (Exception e) {
        //  log.error("Cannot send mail", e);
        //  }
        NotifierURL.notify(message);
        log.error("Handled ERROR: " + exception.toString() + "\n\n" + host + "[" + addr + "]\n\n==================================================\n\n" + message);
        %>

      </body>
    </html>
