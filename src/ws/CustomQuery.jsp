<!-- Show a list of alerts on 'latest' parameter -->
<!-- Use the selection to send a new search      -->

<%
  if (column4latests != null) {
    h.addAlwaysColumns(alwaysColumns);
    String latestS = request.getParameter("latest");
    latestS = (latestS  == null || latestS.equals( "null")) ? "" : latestS.trim();
    int latest = 0;
    if (!latestS.equals("")) {
      latest = Integer.valueOf(latestS);
      Set<String> keys = h.latests(column4latests, null, latest, false);
      String[] keysA = keys.toArray(new String[0]);
      String keysS = String.join(",", keysA);
      out.println("<b><u>" + keys.size() + " " + column4latests + " registered in last " + latestS + " minutes:</u></b><br/>");
      String name;
      int n = 0;
      out.println("<table>");
      for (String k : keys) {
        name = k.split("_")[0];
        if (n == 0) {
          out.println("<tr>");
          }
        out.println("<td><input type='checkbox' class='details' value='" + name + "'>");
        out.println("<label for='" + name + "'>" + name + "</label></td>");
        n++;
        if (n == 5) {
          out.println("</tr>");
          n = 0;
          }        
        }
      if (n != 0) {
        out.println("</tr>");
        }
      out.println("</table>");
      showTable = false;
      out.println("<input type='button' onclick='searchDetails(\"" + url + "\")' value='Details'/>");
      }
    }
  %>
