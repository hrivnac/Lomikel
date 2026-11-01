'use strict';
// Show all edges through offline GraphSON transport, for both graph controllers.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../../webservice');
const origin = fs.readFileSync(path.join(root, 'graph-table-origin.js'), 'utf8');
const vertex = id => ({type:'vertex', id, label:'object', element:[]});
const edge = (id, label, from, to) => ({'@type':'g:Edge', '@value':{
  id:{'@value':{relationId:id}}, label, outV:{'@value':from}, inV:{'@value':to}}});
const result = items => JSON.stringify({result:{data:{'@value':items}}});
async function exercise(script) {
  const controls = Object.fromEntries(['vis','select','filter','expandTo','expandFrom',
    'add2graph','clusterize','zoom','stabilize','commands','feedback'].map(id =>
    [id,{value:'',checked:false,children:[],appendChild(c){this.children.push(c);},
      replaceChildren(){this.children=[];}}]));
  controls.vis.offsetHeight = 500;
  controls.expandTo.checked = controls.expandFrom.checked = true;
  const context = vm.createContext({console,setTimeout,showTab(){},
    document:{getElementById:id=>controls[id]||null,
      createTextNode:text=>({text}),createElement:tag=>({tag,appendChild(){},addEventListener(){}})},
    vis:{Network:class {constructor(){this.listeners={};}on(n,cb){this.listeners[n]=cb;}
      once(){}destroy(){}isCluster(){return false;}getScale(){return 1;}
      getViewPosition(){return {x:0,y:0};}moveTo(){} }}, options4Graph:{},
    stylesheet:{nodes:{default:{properties:{},graphics:{title:'node',subtitle:'',label:'node',
      group:' ',value:'0',shape:'dot',borderWidth:'1',borderDashes:[1,0]},actions:[]}},
      edges:{default:{properties:{},graphics:{title:'edge',subtitle:'',label:'edge',
        group:' ',value:'1',arrows:'to'},actions:[]},
        overlaps:{properties:{},graphics:{title:'overlap',subtitle:'',label:'overlap',
          group:' ',value:'1',arrows:''},actions:[]}}}});
  context.window=context;
  vm.runInContext(fs.readFileSync(path.join(root,script),'utf8'),context,{filename:script});
  vm.runInContext(origin,context,{filename:'graph-table-origin.js'});
  context.server='//CC';context.show([vertex(11),vertex(12)]);
  context.server='//IJCLab';context.show([vertex(21),vertex(22)]);
  const calls=[];
  const responses={
    '//CC': {
      '11:outE':[edge('ab1','overlaps',11,12),edge('ab2','overlaps',11,12),
        edge('direct-ab','directed',11,12),edge('loop','overlaps',11,11)],
      '12:inE':[edge('ab1','overlaps',11,12),edge('ab2','overlaps',11,12),edge('direct-ab','directed',11,12)],
      '12:outE':[edge('ba1','overlaps',12,11),edge('ba2','overlaps',12,11),
        edge('ba3','overlaps',12,11),edge('direct-ba','directed',12,11)],
      '11:inE':[edge('ba1','overlaps',12,11),edge('ba2','overlaps',12,11),
        edge('ba3','overlaps',12,11),edge('direct-ba','directed',12,11)]
    },
    '//IJCLab': {
      '21:outE':[edge('ij-ab','overlaps',21,22)],
      '22:outE':[edge('ij-ba','overlaps',22,21)]
    }
  };
  context.XMLHttpRequest=class {
    open(method,url,sync){assert.equal(method,'GET');assert.equal(sync,false);this.url=url;}
    send(){
      const server=this.url.startsWith('//CC')?'//CC':'//IJCLab';
      const query=new URL('http:'+this.url).searchParams.get('gremlin');
      calls.push({server,query});
      const match=query.match(/\.V\((\d+)\)\.(outE|inE)\(\)/);
      assert.ok(match,query);
      this.readyState=4;this.status=200;
      this.responseText=result(responses[server][match[1]+':'+match[2]]||[]);
      this.onload();
    }
  };
  context.fillEdges();
  // callGremlinGraph is async, though the mocked XHR and legacy send are synchronous.
  await Promise.resolve();
  assert.equal(calls.length,8,script+': query both directions for every vertex');
  assert.equal(context.server,'//IJCLab',script+': selected server remains unchanged');
  const ids=()=>Array.from(context.edges,e=>e.id);
  assert.deepEqual(ids(),['ab1','ab2','direct-ab','loop','ba3','direct-ba','ij-ab'],
    script+': pair reciprocal overlaps once, keep extra parallel, directed and loop edges');
  const kept=context.edges.find(e=>e.id==='ab1');
  context.network.listeners.click({nodes:[],edges:['ab1']});
  assert.equal(context.selectedEdge,kept,script+': retained edge keeps its real ID and action target');
  assert.equal(context.graphTableServerForEdge('ab1'),'//CC');
  assert.equal(context.graphTableServerForEdge('ij-ab'),'//IJCLab');
  context.fillEdges();await Promise.resolve();
  assert.equal(calls.length,16,script+': repeated action retains complete query coverage');
  assert.deepEqual(ids(),['ab1','ab2','direct-ab','loop','ba3','direct-ba','ij-ab'],
    script+': replay does not add a suppressed reciprocal edge');
  // Reset/replace must not keep pairing state from a previous graph.
  context.edges.length=0;context.nodes.length=0;
  context.server='//CC';context.show([vertex(11),vertex(12)]);
  context.show(context.parseGraph(result([edge('solo','overlaps',11,12)])));
  assert.deepEqual(ids(),['solo'],script+': one-sided overlap remains visible');
  context.edges.length=0;
  context.show(context.parseGraph(result([edge('ba1','overlaps',12,11),
    edge('ab1','overlaps',11,12),edge('ab2','overlaps',11,12)])));
  assert.deepEqual(ids(),['ba1','ab2'],script+': reverse-first pairing and reset retain unmatched parallel');
}
(async()=>{for(const script of ['GraphView.js','GraphViewNew.js']) await exercise(script);
  console.log('Reciprocal overlap GraphSON Show all edges tests passed');})().catch(error=>{console.error(error);process.exitCode=1;});
