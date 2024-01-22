function storeData(fn, dataS) {
  return "FITSView.jsp?fn=" + fn + "&data=" + encodeURIComponent(dataS);
  }

stylesheet.nodes.AstroLabNet = {
  properties:{},
  graphics: {
    label:"AstroLabNet", 
    title:"AstroLabNet",        
    subtitle:" ",     
    group:" ",        
    shape:"image",      
    image:"AstroLab.png",        
    borderRadius:"0", 
    borderWidth:"1",  
    borderDashes:[1,0],
    value:"0"         
    },
  actions:[
    {name:"Fink Data Explorer Home",  url:"https://cern.ch/hrivnac/Activities/Packages/FinkBrowser",                target:"external"},
    {name:"Show",                     url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Node.jsp?id=\")"}, target:"result"  }
    ]
  }
stylesheet.nodes.site = {
  properties:{gremlin:"valueMap('lbl', 'Livy', 'Spark', 'Spark History', 'Ganglia', 'Hadoop', 'HBase', 'Prometheus', 'Mesos', 'Grafana', 'Zeppelin', 'Tomcat', 'HBase_ZTF_Season1', 'HBase_Test_Tiny_3').toList()[0]"},
  graphics: {
    label:"title", 
    title:"title",        
    subtitle:" ",     
    group:" ",        
    shape:"image",      
    image:{js:"title.replace('site:', '') + '.png'"},
    borderRadius:"0", 
    borderWidth:"1",  
    borderDashes:[1,0],
    value:"0"         
    },
  actions:[
    {name:"Livy",               url:"Livy"         , target:"external"},
    {name:"Spark",              url:"Spark"        , target:"external"},
    {name:"Spark History",      url:"Spark History", target:"external"},
    {name:"Ganglia",            url:"Ganglia"      , target:"external"},
    {name:"Hadoop",             url:"Hadoop"       , target:"external"},
    {name:"HBase",              url:"HBase"        , target:"external"},
    {name:"Prometheus",         url:"Prometheus"   , target:"external"},
    {name:"Mesos",              url:"Mesos"        , target:"external"},
    {name:"Grafana",            url:"Grafana"      , target:"external"},
    {name:"Zeppelin",           url:"Zeppelin"     , target:"external"},
    {name:"Tomcat",             url:"Tomcat"       , target:"external"}
    ]
  }
stylesheet.nodes.datalink = {
  properties:{gremlin:"valueMap('name', 'technology').toList()[0]"},
  graphics: {
    label:"name",
    title:"name",        
    subtitle:"technology",
    group:" ",        
    shape:"dot",      
    image:"",        
    borderRadius:"0", 
    borderWidth:"1",  
    borderDashes:[1,0],
    value:"0"        
    },
  actions:[                                                                 
    {name:"Link",    url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"DataLink.jsp?id=\")"    }, target:"result"  },
    {name:"Fits",    url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"DataLinkFits.jsp?id=\")"}, target:"result"  },
    {name:"Show",    url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Node.jsp?id=\")"        }, target:"result"  },
    {name:"Table",   url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Nodes.jsp?id=\")"       }, target:"table"   }
    ]
  }
stylesheet.nodes.alert = {
  properties:{gremlin:"valueMap('lbl').toList()[0]"},
  graphics: {
    label:"lbl",
    title:"lbl",        
    subtitle:" ",
    group:{gremlin:"values('objectId').toList()[0]"},        
    shape:"hexagon",      
    image:"",        
    borderRadius:"0", 
    borderWidth:"2",  
    borderDashes:[1,1],
    value:{gremlin:"out().out().has('lbl', 'prv_candidate').count().join().toString()"}        
    },
  actions:[                                                                 
    {name:"Show",    url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Node.jsp?id=\")" }, target:"result"  },
    {name:"Table",   url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Nodes.jsp?id=\")"}, target:"table"   }
    ]
  }
stylesheet.nodes.PCA = {
  properties:{gremlin:"valueMap('lbl').toList()[0]"},
  graphics: {
    label:"lbl",
    title:"lbl",        
    subtitle:" ",
    group:{gremlin:"values('objectId').toList()[0]"},        
    shape:"box",      
    image:"",        
    borderRadius:"0", 
    borderWidth:"2",  
    borderDashes:[1,1],
    value:"0"        
    },
  actions:[                                                                 
    {name:"Show",    url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Node.jsp?id=\")" }, target:"result"  },
    {name:"Table",   url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Nodes.jsp?id=\")"}, target:"table"   }
    ]
  }
stylesheet.nodes.candidate = {
  properties:{gremlin:"valueMap('candid').toList()[0]"},
  graphics: {
    label:"candid",
    title:"candid",        
    subtitle:" ",
    group:{gremlin:"in().has('lbl', 'alert').values('objectId').toList()[0]"},        
    shape:"dot",      
    image:"",        
    borderRadius:"0", 
    borderWidth:"3",  
    borderDashes:[1,0],
    value:"0"        
    },
  actions:[
    {name:"Show",     url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Node.jsp?id=\")"},       target:"result" },
    {name:"Table",    url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Nodes.jsp?id=\")"},      target:"table"  }
    ]
  }
stylesheet.nodes.prv_candidates = {
  properties:{},
  graphics: {
    label:"prv_candidates",
    title:"prv_candidates",        
    subtitle:" ",
    group:{gremlin:"in().has('lbl','alert').values('objectId').toList()[0]"},        
    shape:"dot",      
    image:"",        
    borderRadius:"0", 
    borderWidth:"3",  
    borderDashes:[1,1],
    value:{gremlin:"out().count().join().toString()"}        
    },
  actions:[
    {name:"Show",  url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Node.jsp?id=\")"},  target:"result"},
    {name:"Table", url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Nodes.jsp?id=\")"}, target:"table" }
    ]
  }
stylesheet.nodes.prv_candidate = {
  properties:{gremlin:"valueMap('jd').toList()[0]"},
  graphics: {
    label:"jd",
    title:"jd",        
    subtitle:" ",
    group:{gremlin:"in().in().has('lbl','alert').values('objectId').toList()[0]"},        
    shape:"dot",      
    image:"",        
    borderRadius:"0", 
    borderWidth:"1",  
    borderDashes:[1,1],
    value:"0"        
    },
  actions:[
    {name:"Show",  url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Node.jsp?id=\")"},  target:"result"},
    {name:"Table", url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Nodes.jsp?id=\")"}, target:"table" }
    ]
  }
stylesheet.nodes.cutout = {
  properties:{},
  graphics: {
    label:"cutout",
    title:"cutout",        
    subtitle:" ",
    group:{gremlin:"in().has('lbl', 'alert').values('objectId').toList()[0]"},        
    shape:"triangle",      
    image:"",        
    borderRadius:"0", 
    borderWidth:"3",  
    borderDashes:[1,0],
    value:{gremlin:"both().count().join().toString()"}        
    },
  actions:[                                                                 
    {name:"Show",       url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Node.jsp?id=\")" }, target:"result"  },
    {name:"Table",      url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Nodes.jsp?id=\")"}, target:"table"   }
    ]
  }
stylesheet.nodes.AlertsCollection = {
  properties:{gremlin:"valueMap('title').toList()[0]"},
  graphics: {
    label:"title", 
    title:"title",        
    subtitle:" ",     
    group:" ",        
    shape:"image",      
    image:"Alerts.png",        
    borderRadius:"0", 
    borderWidth:"1",  
    borderDashes:[1,0],
    value:{gremlin:"both().count().join().toString()"}        
    },
  actions:[
    {name:"Show", url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Node.jsp?id=\")"}, target:"result"}
    ]
  }
stylesheet.nodes.AlertsOfInterest = {
  properties:{gremlin:"valueMap('title').toList()[0]"},
  graphics: {
    label:"alertType", 
    title:"alertType",        
    subtitle:" ",     
    group:" ",        
    shape:"image",      
    image:"Alerts.png",        
    borderRadius:"0", 
    borderWidth:"1",  
    borderDashes:[1,0],
    value:{gremlin:"both().count().join().toString()"}        
    },
  actions:[
    {name:"Show", url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Node.jsp?id=\")"}, target:"result"}
    ]
  }
stylesheet.nodes.SourcesOfInterest = {
  properties:{gremlin:"valueMap('title').toList()[0]"},
  graphics: {
    label:"sourceType", 
    title:"sourceType",        
    subtitle:" ",     
    group:" ",        
    shape:"image",      
    image:"Alerts.png",        
    borderRadius:"0", 
    borderWidth:"1",  
    borderDashes:[1,0],
    value:{gremlin:"both().count().join().toString()"}        
    },
  actions:[
    {name:"Show", url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Node.jsp?id=\")"}, target:"result"}
    ]
  }
stylesheet.nodes.Imports = {
  properties:" ",
  graphics: {
    label:" ", 
    title:" ",        
    subtitle:" ",     
    group:" ",        
    shape:"dot",      
    image:"",        
    borderRadius:"0", 
    borderWidth:"2",  
    borderDashes:[1,0],
    value:{gremlin:"both().count().join().toString()"}        
    },
  actions:[
    {name:"Show", url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Node.jsp?id=\")"}, target:"result"}
    ]
  }
stylesheet.nodes.Import = {
  properties:{gremlin:"valueMap('importDate', 'importSource').toList()[0]"},
  graphics: {
    label:"importSource", 
    title:"importSource",        
    subtitle:"importDate",     
    group:" ",        
    shape:"dot",      
    image:"",        
    borderRadius:"0", 
    borderWidth:"1",  
    borderDashes:[1,0],
    value:"0"        
    },
  actions:[
    {name:"Show", url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Node.jsp?id=\")"}, target:"result"}
    ]
  }
stylesheet.edges.has = {
  properties:{},
  graphics: {
    label:" ",
    title:" ",
    subtitle:" ",
    arrows:{to:{enabled:true, type:"vee"}},
    value:"0.1",
    group:" "
    },
  actions: [
     {name:"Show",  url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Edge.jsp?id=\")"},   target:"result"  },
     {name:"Table", url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Edges.jsp?id=\")"},  target:"table"   }
    ]
  }
stylesheet.edges.holds = {
  properties:{},
  graphics: {
    label:" ",
    title:" ",
    subtitle:" ",
    arrows:{to:{enabled:true, type:"vee"}},
    value:"0.1",
    group:" "
    },
  actions: [
    {name:"Show",  url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Edge.jsp?id=\")"},   target:"result"  },
    {name:"Table", url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Edges.jsp?id=\")"},  target:"table"   }
    ]
  }
stylesheet.edges.contains = {
  properties:{gremlin:"valueMap('weight').toList()[0]"},
  graphics: {
    label:" ",
    title:" ",
    subtitle:" ",
    arrows:{to:{enabled:true, type:"vee"}},
    value:"weight",        
    group:" "
    },
  actions: [
    {name:"Show",  url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Edge.jsp?id=\")"},   target:"result"  },
    {name:"Table", url:{gremlin:"id().next().toString().replaceFirst(\"^\", \"Edges.jsp?id=\")"},  target:"table"   }
    ]
  }
