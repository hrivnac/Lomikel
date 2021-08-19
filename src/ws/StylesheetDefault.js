// Graph stylesheet
// can use gremlin/js:
//   nodes: label, title, subtitle, group, shape, value, borderDashes, borderRadius, borderWidth, actions, actions.url
//   edges: label, title, subtitle, group, arrows, value, actions, actions.url
// nodes (but not edges) can use properties name
var stylesheet = {
  nodes: {
    "default": {
      properties:{gremlin:"valueMap('lbl', 'title').toList()[0]"},
      graphics: {
        label:"lbl",       // or label:{js:"lbl"}    
        title:"title",      
        subtitle:" ",
        group:" ",         // specifies graphics properties
        shape:"dot",       // in text:  ellipse, circle, database, box, text
                           // out text: image, circularImage, diamond, dot, star, triangle, triangleDown, hexagon, square, icon
        image:" ",         // should be present only if shape:"image"
        borderRadius:"0",  // should be present only if shape:"box"
        borderWidth:"1",
        borderDashes:[1,0],
        value:"0"
        },
      actions:[
        {name:"info", url:"http://hrivnac.home.cern.ch/hrivnac/Activities/Packages/Lomikel",        target:"external"},
        {name:"help", url:"Result.jsp",                                                             target:"result"  },
        {name:"show", url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Node.jsp?id=\")"}, target:"result"  }
        ]
      },
    "MetaGraph": {
      properties:{gremlin:"valueMap('title', 'MetaLabel').toList()[0]"},
      graphics: {
        label:"MetaLabel",        
        title:"title",      
        subtitle:" ",
        group:" ",         
        shape:"ellipse",      
                           
        image:" ",         
        borderRadius:"0",  
        borderWidth:"1",
        borderDashes:[1,0],
        value:"0"
        },
      actions:[
        {name:"info", url:"http://hrivnac.home.cern.ch/hrivnac/Activities/Packages/Lomikel", target:"external"},
        {name:"help", url:"Result.jsp",                                                      target:"result"  }
        ]
      },
    },
  edges: {
    "default": {
      properties:{},
      graphics: {
        label:" ",
        title:" ",
        subtitle:" ",
        arrows:"middle",
        value:"0",
        group:" "
        },
      actions: [
        {name:"info", url:"http://hrivnac.home.cern.ch/hrivnac/Activities/Packages/Lomikel", target:"external"},
        {name:"help", url:"Result.jsp",                                                      target:"result"  }
        ]
      },
    "MetaGraph": {
      properties:{gremlin:"valueMap('MetaLabel')"},
      graphics: {
        label:"MetaLabel",        
        title:" ",
        subtitle:" ",
        arrows:"middle",
        value:"0",
        group:" "
        },
      actions: [
        {name:"info", url:"http://hrivnac.home.cern.ch/hrivnac/Activities/Packages/Lomikel", target:"external"},
        {name:"help", url:"Result.jsp",                                                      target:"result"}
        ]
      }
    }
  };
 

 
