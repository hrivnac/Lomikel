// Color mapping by alert class
const classesZTF = {
  "Microlensing candidate": "255,255,0",
  "Early SN Ia candidate": "0,255,255",
  "SN candidate": "255,0,0",
  "Solar System candidate": "0,255,0",
  "Solar System MPC": "255,0,255"
  };
// Colors also identify the subset currently supported by the public tag REST
// endpoint. Other HBase tags remain in the shared list but are not polled there.
const LSST_REST_TAG_COLORS = {
  "extragalactic_lt20mag_candidate": "255,255,127",
  "extragalactic_new_candidate": "127,255,255",
  "hostless_candidate": "255,127,127",
  "in_tns": "127,255,127",
  "sn_near_galaxy_candidate": "255,127,255"
  };
const classesLSSTTags = Object.create(null);
async function loadLsstTagTables() {
  const controller = new AbortController();
  let timeoutId;
  const deadline = new Promise((_resolve, reject) => {
    timeoutId = setTimeout(() => {
      controller.abort();
      reject(new Error("Timed out loading LSST tag table list"));
      }, 3000);
    });
  let text;
  try {
    text = await Promise.race([
      fetch("LSSTTags.lst", {cache: "no-store", signal: controller.signal}).then(async response => {
        if (!response.ok) throw new Error(`LSST tag list unavailable: HTTP ${response.status}`);
        return response.text();
        }),
      deadline
      ]);
    }
  finally {
    clearTimeout(timeoutId);
    }
  const tables = text.replace(/\r?\n$/, "").split(/\r?\n/);
  if (!tables.length || tables.some(table => !/^rubin\.tag_[a-z0-9_]+$/.test(table)) ||
      new Set(tables).size !== tables.length) {
    throw new Error("Invalid or repeated LSST tag table entry");
    }
  for (const table of tables) {
    const name = table.slice("rubin.tag_".length);
    classesLSSTTags[name] = Object.prototype.hasOwnProperty.call(LSST_REST_TAG_COLORS, name)
      ? LSST_REST_TAG_COLORS[name] : "255,255,255";
    classesLSST[name] = classesLSSTTags[name];
    classes[name] = classesLSSTTags[name];
    }
  }
const classesLSSTLatest = {
  "LSST DIA source": "255,127,127",
  "LSST SS source": "255,200,80"
  };
const classesLSST = Object.assign(Object.create(null), classesLSSTTags, classesLSSTLatest);
const classes = Object.assign(Object.create(null), classesZTF, classesLSST);
 
let latestAlertsAvailable = false;
let fetchPeriod = 10; // minutes; 0 loads once
let fetchStart = 48; // hours before now; 0 requests the latest available alerts in server mode
let nAlerts = 10;
let magMax = 6;
let fetchLSST = false;
