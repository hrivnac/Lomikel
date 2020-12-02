<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel -->
<!-- @author Julius.Hrivnac@cern.ch -->

<%@ page import="com.Lomikel.Utils.Info" %>
<%@ page import="com.Lomikel.Utils.NotifierURL" %>

<%@ page errorPage="ExceptionHandler.jsp" %>

<jsp:useBean id="profile" class="com.Lomikel.WebService.Profile" scope="session"/>
<jsp:useBean id="style"   class="com.Lomikel.WebService.Style"   scope="session"/>

<!DOCTYPE html>
<html>

  <head>
    <title>@NAME@</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
    <link href="vis-network-8.3.2/styles/vis-network.min.css"           rel="stylesheet" type="text/css" />
    <link href="vis-timeline-7.3.9/styles/vis-timeline-graph2d.min.css" rel="stylesheet" type="text/css" />
    <link href="bootstrap-4.4.1/css/bootstrap.min.css"                  rel="stylesheet" type="text/css">
    <link href="fontawesome-free-5.13.0-web/css/all.css"                rel="stylesheet" type="text/css">
    <link href="bootstrap-table-1.16.0/dist/bootstrap-table.min.css"    rel="stylesheet" type="text/css">
    <link href="jquery-ui-1.12.1/jquery-ui.min.css"                     rel="stylesheet" type="text/css"/>
    <link href="w2ui-1.5.rc1/w2ui-1.5.rc1.min.css"                      rel="stylesheet" type="text/css" />
    <link href="index.css"                                              rel="stylesheet" type="text/css"/>
    <link href="@ICON@"                                                 rel="icon"       type="image">
    </head>
  
  <body>
  
    <%
      String p = request.getParameter("profile");
      profile.setProfile(p);
      String s = request.getParameter("style");
      style.setStyle(s);
      %>
  
    <%@include file="Init.jsp"%>
      
    <script>
      var width = window.innerWidth
               || document.documentElement.clientWidth
               || document.body.clientWidth;
      var height = window.innerHeight
                || document.documentElement.clientHeight
                || document.body.clientHeight;
      var div = document.createElement("div");
      div.style.width = "100%";
      div.style.height = height + "px";
      div.id = "layout";
      document.body.appendChild(div);
      </script>
  
    <script type="text/javascript" src="vis-network-8.3.2/standalone/umd/vis-network.min.js"></script> 
    <script type="text/javascript" src="vis-timeline-7.3.9/standalone/umd/vis-timeline-graph2d.min.js"></script> 
    <script type="text/javascript" src="jquery-3.5.1.min.js"></script>
    <script type="text/javascript" src="jquery-ui-1.12.1/jquery-ui.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.14.7/umd/popper.min.js" integrity="sha384-UO2eT0CpHqdSJQ6hJty5KVphtPhzWj9WO1clHTMGa3JDZwrnQq4sF86dIHNDz0W1" crossorigin="anonymous"></script>
    <script type="text/javascript" src="bootstrap-4.4.1/js/bootstrap.min.js"></script>
    <script type="text/javascript" src="bootstrap-table-1.16.0/dist/bootstrap-table.min.js"></script>
    <script type="text/javascript" src="knockout-3.2.0.js"></script>
    <script type="text/javascript" src="moment-2.25.2.js"></script>
    <script type="text/javascript" src="w2ui-1.5.rc1/w2ui-1.5.rc1.min.js"></script>
    <script type="text/javascript" src="OptionsDefault.js"></script>
    <script type="text/javascript" src="Options.js"></script>
    <script type="text/javascript" src="Tabs.js"></script>      
    <script type="text/javascript" src="Sleep.js"></script>        
    <script type="text/javascript" src="LoadPane.js"></script>
    <script type="text/javascript" src="Help.js"></script>
      
    <script type="text/javascript">
      $(function () {
        var greenstyle     = 'border: 1px solid #dfdfdf; padding: 5px; background-color: #ddffdd';
        var darkgreenstyle = 'border: 1px solid #dfdfdf; padding: 5px; background-color: #aaffaa';
        var bluestyle      = 'border: 1px solid #dfdfdf; padding: 5px; background-color: #ddddff';
        var darkbluestyle  = 'border: 1px solid #dfdfdf; padding: 5px; background-color: #aaaaff';
        $('#layout').w2layout({
          name:'layout',
          panels:[
            {type:'left', size:'50%', resizable:true},
            {type:'main', size:'50%', resizable:true}
            ]
          });
        $().w2layout({
          name: 'layoutLeft',
          panels: [
            {type:'top',    size:'05%', resizable:true, overflow:false, style:darkgreenstyle},
            {type:'main',   size:'15%', resizable:true, overflow:false, style:greenstyle},
            {type:'bottom', size:'80%', resizable:true, overflow:false, style:greenstyle,            
              tabs: {
                name:'tabs',
                active:'graphTab',
                tabs: [
                  {id:'graphTab', caption:'Graph', tooltip:'Graph View'},
                  {id:'imageTab', caption:'Image', tooltip:'Image View'},
                  {id:'plotTab',  caption:'Plot' , tooltip:'Plot View' }
                  ],
                onClick:function (event) {
                  showTab(event.target.replace('Tab', ''));
                  }
                } 
              }
            ]
          });
        $().w2layout({
          name: 'layoutMain',
          panels: [
            {type:'main',   size:'90%', resizable:true, overflow:'auto', style:bluestyle},
            {type:'bottom', size:'10%', resizable:true, overflow:'auto', style:darkbluestyle}
            ]
          });        
        w2ui['layout'].html('left', w2ui['layoutLeft']);
        w2ui['layout'].html('main', w2ui['layoutMain']);
        w2ui['layoutLeft'].load('top',    'TopMini.jsp');
        w2ui['layoutLeft'].load('main',   'Top.jsp');
        w2ui['layoutLeft'].load('bottom', 'Tabs.jsp');
        w2ui['layoutMain'].load('main',   'Result.jsp');
        w2ui['layoutMain'].load('bottom', 'Feedback.jsp');  
        //w2ui['layoutLeft']['panels'][2]['tabs'].set('graphTab', {caption:'New Caption'});
        //w2ui['layoutLeft']['panels'][2]['tabs'].add([{ id:'tab3', text:'Tab 3' }]);        
        });
      var visheight;
      $.getScript("profiles/<%=p%>.js", function() {});
      </script>  
      
    <script>
      // TBD: move all feedback, commands calls here
      window.feedback = function(txt) {
        document.getElementById("feedback").innerHTML += txt + "</br>";
        }
      var helpButton  = "<button onClick=\"w2popup.load({url:'Help-Top.html', showMax: true})\" style=\"position:absolute; top:0; right:0\">";
          helpButton += "<img src=\"images/Help.png\" width=\"10\"/>";
          helpButton += "</button>";      
      window.commands = function(title, info, actions) {
        document.getElementById("commands").innerHTML = info + helpButton + "<hr/>" + "Actions: " + actions;
        }
      </script>
    
         
    </body>
    
  </html>
