// === CONFIG ===
const DATA_FILE = "ztf_example.json";
const MAX_FLASHES = 250;
const TRAIL_LEN = 12;

// === UTIL ===
const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
const mod360 = x => ((x % 360) + 360) % 360;
const randInt = (a, b) => Math.floor(a + Math.random() * (b - a + 1));

// === CLASS COLORS ===
const classColorMap = {};
const hashStr = s => Array.from(s).reduce((h,c) => (h<<5)-h+c.charCodeAt(0),0);
const hslToRGB = (h,s,l) => {
  const c=(1 - Math.abs(2*l-1))*s;
  const x=c*(1 - Math.abs((h/60)%2-1));
  const m=l-c/2; let r=0,g=0,b=0;
  if(h<60){r=c;g=x;}else if(h<120){r=x;g=c;}
  else if(h<180){g=c;b=x;}else if(h<240){g=x;b=c;}
  else if(h<300){r=x;b=c;}else{r=c;b=x;}
  return `${Math.round((r+m)*255)},${Math.round((g+m)*255)},${Math.round((b+m)*255)}`;
};
const getColor = cls => {
  if(!classColorMap[cls]) classColorMap[cls] = hslToRGB(Math.abs(hashStr(cls)) % 360,0.7,0.5);
  return classColorMap[cls];
};

// === CANVAS ELEMENTS ===
const sky = document.getElementById("sky");
const ctx = sky.getContext("2d");
const overview = document.getElementById("overview");
const octx = overview.getContext("2d");
const tooltip = document.getElementById("tooltip");
const viewInfo = document.getElementById("viewInfo");
const btnWhole = document.getElementById("btnWhole");
const btnDynamic = document.getElementById("btnDynamic");
const legendEl = document.getElementById("legend");

// === STATE ===
let frames = [];
let alertsPool = []; // loaded alerts
let mode = "dynamic"; // or "whole"
let viewBox = { raMin: 0, raMax: 360, decMin: -90, decMax: 90 };

// === RESIZE ===
function resizeAll(){
  sky.width = sky.clientWidth;
  sky.height = sky.clientHeight;
  overview.width = overview.clientWidth;
  overview.height = overview.clientHeight;
}
window.addEventListener("resize",resizeAll);
resizeAll();

// === CONTROLS ===
btnWhole.onclick = ()=>{mode="whole"; btnWhole.classList.add("active"); btnDynamic.classList.remove("active");};
btnDynamic.onclick = ()=>{mode="dynamic"; btnDynamic.classList.add("active"); btnWhole.classList.remove("active");};

// === LEGEND ===
function updateLegend(){
  legendEl.innerHTML = "";
  Object.entries(classColorMap).slice(0,12).forEach(([cls,rgb]) => {
    const div=document.createElement("div");
    div.innerHTML = `<span style="background:rgb(${rgb});width:14px;height:14px;display:inline-block;margin-right:6px;"></span>${cls}`;
    legendEl.appendChild(div);
  });
}

// === FLASH ===
class Flash{
  constructor(a){
    this.ra=a.ra; this.dec=a.dec;
    this.objectId=a.objectId; this.jd=a.jd;
    this.cls=a.class;
    this.rgb=getColor(this.cls);
    this.birth=Date.now();
    this.UP=1000;
    this.DN=10000;
    this.pos={x:0,y:0};
  }
  age(){return Date.now()-this.birth;}
  alive(){return this.age()<this.UP+this.DN;}
  radius(){let t=this.age(); return t<this.UP ? 5+15*(t/this.UP) : 20*(1-((t-this.UP)/this.DN));}
  alpha(){let t=this.age(); return t<this.UP ? t/this.UP : 1-((t-this.UP)/this.DN);}
  draw(){
    const r=this.radius(), a=this.alpha();
    if(a<=0)return false;
    const p=raDecToXY(this.ra,this.dec,sky.width,sky.height,viewBox);
    drawStar(ctx,p.x,p.y,r,`rgba(${this.rgb},${a})`);
    ctx.font="bold 14px sans-serif";
    ctx.fillStyle = `rgba(${this.rgb},${a})`;
    ctx.fillText(this.cls,p.x+r+4,p.y-r-4);
    this.pos=p;
    return true;
  }
}

// === HELPERS ===
function raDecToXY(ra,dec,w,h,box){
  const x=(ra-box.raMin)/(box.raMax-box.raMin)*w;
  const y=h-((dec-box.decMin)/(box.decMax-box.decMin)*h);
  return {x,y};
}

// === DRAW STAR ===
function drawStar(ctx,x,y,r,color){
  ctx.beginPath();
  ctx.arc(x,y,r,0,2*Math.PI);
  ctx.fillStyle=color;
  ctx.fill();
}

// === ANIMATE ===
function animate(){
  ctx.fillStyle="black"; ctx.fillRect(0,0,sky.width,sky.height);
  if(mode==="dynamic" && frames.length){
    const ras=frames.map(f=>f.ra), decs=frames.map(f=>f.dec);
    viewBox.raMin=Math.min(...ras)-5;
    viewBox.raMax=Math.max(...ras)+5;
    viewBox.decMin=Math.min(...decs)-5;
    viewBox.decMax=Math.max(...decs)+5;
  }
  if(mode==="whole") viewBox={raMin:0,raMax:360,decMin:-90,decMax:90};

  frames=frames.filter(f=>f.draw());
  drawOverview();
  requestAnimationFrame(animate);
}

// === OVERVIEW ===
function drawOverview(){
  octx.fillStyle="black"; octx.fillRect(0,0,overview.width,overview.height);
  frames.forEach(f=>{
    const p=raDecToXY(f.ra,f.dec,overview.width,overview.height,{raMin:0,raMax:360,decMin:-90,decMax:90});
    octx.fillStyle=`rgba(${f.rgb},0.9)`; octx.fillRect(p.x-1,p.y-1,3,3);
  });
  const x1=(viewBox.raMin/360)*overview.width, x2=(viewBox.raMax/360)*overview.width;
  const y1=overview.height-((viewBox.decMin+90)/180)*overview.height;
  const y2=overview.height-((viewBox.decMax+90)/180)*overview.height;
  octx.strokeStyle="red"; octx.strokeRect(x1,y2,x2-x1,y1-y2);

  viewInfo.textContent=`RA: ${viewBox.raMin.toFixed(1)}–${viewBox.raMax.toFixed(1)}, Dec: ${viewBox.decMin.toFixed(1)}–${viewBox.decMax.toFixed(1)}`;
}

// === TOOLTIP ===
sky.addEventListener("mousemove",e=>{
  const rect=sky.getBoundingClientRect(), mx=e.clientX-rect.left, my=e.clientY-rect.top;
  const f=frames.find(f=> {
    if(!f.pos)return false;
    const dx=f.pos.x-mx, dy=f.pos.y-my;
    return dx*dx+dy*dy<100;
  });
  if(f){
    tooltip.style.display="block";
    tooltip.style.left=`${e.clientX+12}px`; tooltip.style.top=`${e.clientY+12}px`;
    tooltip.innerHTML=`<b>${f.objectId}</b><br>JD: ${f.jd.toFixed(2)}<br>`+
      `<a href="https://fink-portal.org/${f.objectId}" target="_blank">View in Fink</a>`;
  } else tooltip.style.display="none";
});

// === DATA LOOP ===
function loopAlerts(){
  if(!alertsPool.length) return;
  const pick = alertsPool[randInt(0,alertsPool.length-1)];
  frames.push(new Flash({
    ra: pick['i:ra'], dec: pick['i:dec'],
    objectId: pick['i:objectId'], jd: pick['i:jd'], class: pick['v:classification']
  }));
  if(frames.length>MAX_FLASHES) frames.splice(0,frames.length-MAX_FLASHES);
  setTimeout(loopAlerts,1000+Math.random()*8000);
}

// === INIT ===
async function init(){
  try{
    //const resp = await fetch(DATA_FILE);
    //alertsPool = await resp.json();

const alertsPool = [
{
    "i:dec": -2.1304045,
    "i:jd": 2460913.7532639,
    "i:objectId": "ZTF25abixmgq",
    "i:ra": 317.3594119,
    "v:classification": "A"
  },
  {
    "i:dec": 34.6136593,
    "i:jd": 2460911.884919,
    "i:objectId": "ZTF25abjbadu",
    "i:ra": 24.5693835,
    "v:classification": "A"
  },
  {
    "i:dec": 47.0764717,
    "i:jd": 2460911.8657176,
    "i:objectId": "ZTF25abioriw",
    "i:ra": 353.9771969,
    "v:classification": "A"
  },
  {
    "i:dec": 47.0764876,
    "i:jd": 2460911.8276157,
    "i:objectId": "ZTF25abioriw",
    "i:ra": 353.9772349,
    "v:classification": "A"
  },
  {
    "i:dec": 33.5252544,
    "i:jd": 2460910.8378241,
    "i:objectId": "ZTF25abiqcie",
    "i:ra": 326.78721,
    "v:classification": "A"
  }];
    
    console.log(`Loaded ${alertsPool.length} alerts from ${DATA_FILE}`);
  }catch(e){
    console.error("Failed to load alerts:", e);
  }
  updateLegend();
  animate();
  loopAlerts();
}
init();
