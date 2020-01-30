// Graph stylesheet
var stylesheet = {
  nodes: {
    "default": {
      graphics: {
        label:" ",         // can be "", can be {gremlin:...}
        title:" ",         // can be "", can be {gremlin:...}
        subtitle:" ",      // can be "", can be {gremlin:...}
        group:" ",         // specifies graphics properties, can be "", can be {gremlin:...}
        shape:"dot",       // in text:  ellipse, circle, database, box, text
                           // out text: image, circularImage, diamond, dot, star, triangle, triangleDown, hexagon, square, icon
                           // can be {js:...}
        image:" ",         // should be present only if shape:"image"
        borderRadius:"0",  // should be present only if shape:"box"
        borderWidth:"1",
        borderDashes:[1,0],
        value:"0"          // can be {gremlin:...}
        },
      actions:[
        {name:"info", url:""}
        ]
      },
    "LAL": {
      graphics: {
        label:{gremlin:"label()"}, 
        title:" ",        
        subtitle:" ",     
        group:" ",        
        shape:"dot",      
        image:" ",        
        borderRadius:"0", 
        borderWidth:"1",  
        borderDashes:[1,0],
        value:"0"         
        },
      actions:[
        {name:"info", url:""}
        ]
      },
    "AstroLabNet": {
      graphics: {
        label:{gremlin:"label()"}, 
        title:" ",        
        subtitle:" ",     
        group:" ",        
        shape:"dot",      
        image:" ",        
        borderRadius:"0", 
        borderWidth:"1",  
        borderDashes:[1,0],
        value:"0"         
        },
      actions:[
        {name:"info", url:""}
        ]
      }
    },
  edges: {
    "default": {
      graphics: {
        label:" ",
        title:" ",
        subtitle:" ",
        value:"0",
        group:" "
        },
      actions: [
        {name:"info", url:""}
        ]
      }
    }
  }
 
