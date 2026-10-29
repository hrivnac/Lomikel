'use strict';
// Mixed graph origins must govern Show all edges, not the most recent selector.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const root=path.resolve(__dirname,'../../webservice');
const origin=fs.readFileSync(path.join(root,'graph-table-origin.js'),'utf8');
const vertex=(id)=>({type:'vertex',id,label:'object',element:[{key:'lbl',value:'object'}]});
function setup(script){
  const ids=['vis','select','filter','expandTo','expandFrom','removeOld','clusterize','zoom',
    'stabilize','add2graph','commands','feedback'];
  const controls=Object.fromEntries(ids.map(id=>[id,{value:'',checked:false,children:[],
    appendChild(c){this.children.push(c);},replaceChildren(){this.children=[];}}]));
  controls.vis.offsetHeight=500;
  controls.select.value='limit(10)';
  controls.expandTo.checked=true;
  controls.expandFrom.checked=true;
  const ctx=vm.createContext({console,document:{getElementById:id=>controls[id]||null,
    createElement:tag=>({tag,addEventListener(){},appendChild(){}}),createTextNode:text=>({text})},
    vis:{Network:class {on(){} once(){} destroy(){} isCluster(){return false;}
      getScale(){return 1;}getViewPosition(){return {x:0,y:0};}moveTo(){} }},
    options4Graph:{},stylesheet:{nodes:{default:{properties:{},graphics:{title:'lbl',subtitle:'',
      label:'lbl',group:' ',value:'0',shape:'dot',borderWidth:'1',borderDashes:[1,0]},actions:[]}},
      edges:{default:{properties:{},graphics:{title:'lbl',subtitle:'',label:'lbl',group:' ',
        value:'1',arrows:' '},actions:[]}}},setTimeout,showTab(){}});
  ctx.window=ctx;
  vm.runInContext(fs.readFileSync(path.join(root,script),'utf8'),ctx,{filename:script});
  vm.runInContext(origin,ctx,{filename:'graph-table-origin.js'});
  const calls=[];
  const realCall=ctx.callGremlinGraph;
  ctx.callGremlinGraph=(query,explicitServer)=>{
    calls.push({query,server:explicitServer||ctx.server});
    if(explicitServer)ctx.server=explicitServer;
  };
  return {ctx,calls,controls,realCall};
}
function mix(ctx,ccIds,ijIds){
  ctx.server='//CC';ctx.show(ccIds.map(vertex));
  ctx.server='//IJCLab';ctx.show(ijIds.map(vertex));
}
function run(script){
  const {ctx,calls,controls}=setup(script);
  const isNew=script==='GraphViewNew.js';
  mix(ctx,[11,12],isNew?['ztf-1',22]:[21,22]);
  assert.equal(ctx.graphTableServerForVertex(11),'//CC');
  assert.equal(ctx.graphTableServerForVertex(22),'//IJCLab');
  ctx.fillEdges();
  assert.equal(calls.length,8,script+' should query both directions for four vertices');
  const perServer={'//CC':['11','12'],'//IJCLab':isNew?["'ztf-1'",'22']:['21','22']};
  for(const call of calls){
    const match=call.query.match(/\.V\((.*?)\)\.(outE|inE)\(\)\.filter\((?:inV|outV)\(\)\.hasId\(within\((.*?)\)\)\)/);
    assert.ok(match,call.query);
    const server=perServer['//CC'].includes(match[1])?'//CC':'//IJCLab';
    assert.equal(call.server,server,script+': vertex must be queried on its own server');
    assert.equal(match[3],perServer[server].join(','),script+': within() must use same-server IDs only');
  }
  assert.equal(ctx.server,'//IJCLab',script+': restore previously selected server');
  const single=setup(script);
  single.ctx.server='//CC';single.ctx.show([vertex(31),vertex(32)]);
  single.ctx.fillEdges();
  assert.equal(single.calls.length,4,script+': single-server graph retains both directions');
  assert.ok(single.calls.every(call=>call.server==='//CC'));
  const unowned=setup(script);
  mix(unowned.ctx,[41],[42]);
  unowned.ctx.nodes.push({id:99,label:'unowned'});
  unowned.ctx.fillEdges();
  assert.equal(unowned.calls.length,0,script+': mixed graph with unowned vertex fails closed');
  assert.ok(unowned.controls.feedback.children.length,script+': show a routing error');
}
function exerciseActualTransport(script){
  const {ctx,realCall}=setup(script);
  mix(ctx,[11,12],[21,22]);
  const requests=[];
  const edge=(id,from,to)=>({'@type':'g:Edge','@value':{
    id:{'@value':{relationId:id}},label:'link',
    outV:{'@value':from},inV:{'@value':to}}});
  // Use the real callGremlinGraph -> GraphSON -> show -> origin wrapper path.
  ctx.callGremlinGraph=realCall;
  ctx.XMLHttpRequest=class {
    open(method,url,async){assert.equal(method,'GET');assert.equal(async,false);this.url=url;}
    send(){
      const source=this.url.startsWith('//CC')?'//CC':'//IJCLab';
      const query=new URL('http:'+this.url).searchParams.get('gremlin');
      requests.push({source,query});
      const payload=query.includes('.outE()')&&query.includes('.V(11)')&&source==='//CC'
        ? [edge('cc-11-12',11,12)]
        : query.includes('.outE()')&&query.includes('.V(21)')&&source==='//IJCLab'
          ? [edge('ij-21-22',21,22)] : [];
      this.readyState=4;this.status=200;
      this.responseText=JSON.stringify({result:{data:{'@value':payload}}});
      this.onload();
    }
  };
  ctx.fillEdges();
  assert.equal(requests.length,8,script+': both directions reached the real transport');
  assert.ok(requests.some(r=>r.source==='//CC'&&r.query.includes('within(11,12)')));
  assert.ok(requests.some(r=>r.source==='//IJCLab'&&r.query.includes('within(21,22)')));
  assert.deepEqual(Array.from(ctx.edges,e=>e.id),['cc-11-12','ij-21-22']);
  assert.equal(ctx.server,'//IJCLab');
}
for(const script of ['GraphView.js','GraphViewNew.js']) run(script);
for(const script of ['GraphView.js','GraphViewNew.js']) exerciseActualTransport(script);
for(const [page,script] of [['GraphView.jsp','GraphView.js'],['GraphViewNew.jsp','GraphViewNew.js']])
  assert.match(fs.readFileSync(path.join(root,page),'utf8'),
    new RegExp(script.replace('.', '\\.')+'\\?v=mixed-edges-1'));
console.log('GraphView variants route all-edge requests only within their originating servers');
