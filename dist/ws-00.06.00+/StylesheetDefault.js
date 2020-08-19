// Graph stylesheet
// can use gremlin/js:
//   nodes: label, title, subtitle, group, shape, value, borderDashes, borderRadius, borderWidth, actions, actions.url
//   edges: label, title, subtitle, group, arrows, value, actions, actions.url
var stylesheet = {
  nodes: {
    "default": {
      graphics: {
        label:{gremlin:"label()"},        
        title:{gremlin:"properties('title').value()"},      
        subtitle:{gremlin:"properties('subtitle').value()"},
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
        label:{gremlin:"properties('MetaLabel').value()"},        
        title:{gremlin:"properties('title').value()"},      
        subtitle:{gremlin:"properties('subtitle').value()"},
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
        label:{gremlin:"properties('MetaLabel').value()"},        
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
 

 
