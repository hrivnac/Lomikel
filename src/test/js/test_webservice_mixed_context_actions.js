'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const root=path.resolve(__dirname,'../../webservice');
const origin=fs.readFileSync(path.join(root,'graph-table-origin.js'),'utf8');
const vertex=id=>({type:'vertex',id,label:'object',element:[]});
const edge=(id,from,to)=>({type:'edge',id,label:'link',outVid:from,inVid:to,element:[]});
function setup(script,withGremlinStyles=false){
 const controls=Object.fromEntries(['vis','select','filter','expandTo','expandFrom','removeOld','clusterize','zoom','stabilize','add2graph','feedback'].map(id=>[id,{value:'',checked:false,children:[],appendChild(x){this.children.push(x)}}]));
 controls.expandTo.checked=true;controls.expandFrom.checked=true;
 const ctx=vm.createContext({console,document:{getElementById:id=>controls[id]||null,createTextNode:x=>({text:x}),createElement:tag=>({tag})},
  vis:{Network:class{on(){}once(){}destroy(){}getScale(){return 1}getViewPosition(){return {x:0,y:0}}moveTo(){}isCluster(){return false}}},
  options4Graph:{},stylesheet:{nodes:{default:{properties:{},graphics:{title:'',subtitle:'',label:'',group:'',value:'0',shape:'dot',borderWidth:'1',borderDashes:''},actions:[]}},edges:{default:{properties:{},graphics:{title:'',subtitle:'',label:'',group:'',value:'1',arrows:''},actions:[]}}},showTab(){},setTimeout});
 ctx.window=ctx;
 vm.runInContext(fs.readFileSync(path.join(root,script),'utf8'),ctx,{filename:script});
 const valueCalls=[];const graphCalls=[];
 ctx.callGremlinValues=(query,source)=>{valueCalls.push({query,source:source||ctx.server});if(source)ctx.server=source;return ['value']};
 ctx.callGremlinGraph=(query,source)=>{graphCalls.push({query,source:source||ctx.server});if(source)ctx.server=source;};
 vm.runInContext(origin,ctx,{filename:'graph-table-origin.js'});
 if(withGremlinStyles){
  ctx.stylesheet.nodes.default.graphics.title={gremlin:'id().next()'};
  ctx.stylesheet.edges.default.graphics.title={gremlin:'id().next()'};
 }
 ctx.server='//CC';ctx.show([vertex(11),vertex(12),edge('cc-e',11,12)]);
 ctx.server='//IJ';ctx.show([vertex(21),vertex(22),edge('ij-e',21,22)]);
 assert.equal(ctx.graphTableServerForVertex(11),'//CC');
 ctx.findObjectByKey=(items,key,id)=>items.find(item=>item[key]===id);
 controls.result={innerHTML:''};
 return {ctx,controls,valueCalls,graphCalls};
}
for(const script of ['GraphView.js','GraphViewNew.js']){
 const {ctx,valueCalls,graphCalls,controls}=setup(script);
 for(const [query,expected] of [
  ["g.V('11').valueMap().toList().toString().replace(', ', '<br/>')",'//CC'],
  ["g.E('cc-e').valueMap().toList().toString().replace(', ', '<br/>')",'//CC'],
  ["g.V('11').id().next().toString().replaceFirst('^', 'Node.jsp?id=')",'//CC'],
  ["g.E('cc-e').values('sizeIn').next().toString()",'//CC'],
  ["g.E('ij-e').valueMap().toList().toString()",'//IJ'],
  ['g.V("11").id().next()', '//CC'],
  ['g.E("cc-e").id().next()', '//CC'],
  ["g.V('21').valueMap().next().toString()",'//IJ'],
  ["LomikelServer.getDataLink(g.V('11').next())",'//CC']]){
  ctx.callGremlinValues(query);
  assert.equal(valueCalls.at(-1).source,expected,script+' '+query);
  assert.equal(ctx.server,'//IJ','a scoped lookup must not change selected server');
 }
 ctx.callGremlinValues("g.E('cc-e').valueMap()",'//override');
 assert.equal(valueCalls.at(-1).source,'//override','explicit destination wins');
 ctx.server='//IJ';
 for(const [page,id,expected] of [['Node.jsp','11','//CC'],['Edge.jsp','cc-e','//CC'],
  ['Node.jsp','21','//IJ'],['Edge.jsp','ij-e','//IJ']]){
  const html=fs.readFileSync(path.join(root,page),'utf8');
  const inline=html.match(/<script type="text\/javascript">([\s\S]*?)<\/script>/)[1];
  vm.runInContext(inline.replace('<%=id%>',id),ctx,{filename:page});
  assert.equal(valueCalls.at(-1).source,expected,page+' should query the clicked element on its source');
  assert.equal(ctx.server,'//IJ',page+' should not switch the selected server');
 }
 ctx.expand(11);
 assert.equal(graphCalls.length,4,script+' expands both directions and both element kinds');
 assert.ok(graphCalls.every(c=>c.source==='//CC'),script+' expands the CC vertex on CC');
 assert.equal(ctx.server,'//IJ','expansion restores selected server');
 graphCalls.length=0;
 ctx.expandEdge('cc-e');
 assert.equal(graphCalls.length,8,script+' expands both endpoints on their own server');
 assert.ok(graphCalls.every(c=>c.source==='//CC'));
 assert.equal(ctx.server,'//IJ');
 graphCalls.length=0;
 controls.removeOld.checked=true;
 ctx.expand(21);
 assert.equal(graphCalls.length,4);
 assert.ok(graphCalls.every(c=>c.source==='//IJ'));
 assert.equal(ctx.graphTableServerForVertex(21),'//IJ');
 const missing=setup(script);
 missing.ctx.nodes.push({id:99,label:'unowned'});
 missing.ctx.expand(99);
 assert.equal(missing.graphCalls.length,0,'unowned vertices fail closed');
 missing.ctx.callGremlinValues("g.E('cc-e').valueMap()");
 assert.equal(missing.valueCalls.at(-1).source,'//CC','single-server context still works');
 missing.ctx.callGremlinValues("g.V('99').valueMap()");
 assert.equal(missing.valueCalls.length,1,'unowned displayed vertex does not query the selected server');
 const pending=setup(script);
 pending.ctx.server='//CC';pending.ctx.show([edge('cc-pending',11,99)]);
 pending.ctx.server='//IJ';pending.ctx.expandEdge('cc-pending');
 assert.equal(pending.graphCalls.length,8,'edge expansion reaches a not-yet-displayed endpoint');
 assert.ok(pending.graphCalls.every(c=>c.source==='//CC'),'missing endpoint inherits the edge origin');
 assert.equal(pending.ctx.server,'//IJ');
 const styled=setup(script,true);
 assert.equal(styled.valueCalls.length,6,'each new vertex/edge is queried before its origin is recorded');
 assert.ok(styled.valueCalls.slice(0,3).every(c=>c.source==='//CC'));
 assert.ok(styled.valueCalls.slice(3).every(c=>c.source==='//IJ'));
 assert.equal(styled.ctx.server,'//IJ');
}
for(const [page,script] of [['GraphView.jsp','GraphView.js'],['GraphViewNew.jsp','GraphViewNew.js']]){
 const html=fs.readFileSync(path.join(root,page),'utf8');
 assert.match(html,new RegExp(script.replace('.', '\\.')+'\\?v=edge-curvature-1'));
 assert.match(html,/graph-table-origin\.js\?v=mixed-context-1/);
}
console.log('mixed graph context queries, vertex/edge expansion and selected server routed by provenance');
