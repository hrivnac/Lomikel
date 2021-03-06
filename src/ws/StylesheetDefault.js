// Graph stylesheet
// can use gremlin/js:
//   nodes: label, title, subtitle, group, shape, value, borderDashes, borderRadius, borderWidth, actions, actions.url
//   edges: label, title, subtitle, group, arrows, value, actions, actions.url
// nodes (but not edges) can use properties name
var stylesheet = {
  nodes: {
    "default": {
      graphics: {
        label:{gremlin:"label()"},        
        title:{gremlin:"properties('title')"},      
        subtitle:{gremlin:"properties('subtitle')"},
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
        {name:"info", url:"http://hrivnac.home.cern.ch/hrivnac/Activities/Packages/Lomikel"}
        ]
      },
    "MetaGraph": {
      graphics: {
        label:{gremlin:"properties('MetaLabel')"},        
        title:{gremlin:"properties('title')"},      
        subtitle:{gremlin:"properties('subtitle')"},
        group:" ",         
        shape:"ellipse",      
                           
        image:" ",         
        borderRadius:"0",  
        borderWidth:"1",
        borderDashes:[1,0],
        value:"0"
        },
      actions:[
        {name:"info", url:"http://hrivnac.home.cern.ch/hrivnac/Activities/Packages/Lomikel"}
        ]
      },
    },
  edges: {
    "default": {
      graphics: {
        label:" ",
        title:" ",
        subtitle:" ",
        arrows:"middle",
        value:"0",
        group:" "
        },
      actions: [
        {name:"info", url:"http://hrivnac.home.cern.ch/hrivnac/Activities/Packages/Lomikel"}
        ]
      },
    "MetaGraph": {
      graphics: {
        label:{gremlin:"properties('MetaLabel')"},        
        title:" ",
        subtitle:" ",
        arrows:"middle",
        value:"0",
        group:" "
        },
      actions: [
        {name:"info", url:"http://hrivnac.home.cern.ch/hrivnac/Activities/Packages/Lomikel"}
        ]
      }
    }
  };
 

 
