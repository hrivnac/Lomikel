function portalUrl(survey, objectId) {
  const hosts = { LSST: "lsst.fink-portal.org", ZTF: "ztf.fink-portal.org" };
  const host = hosts[survey];
  if (!host || !/^[A-Za-z0-9_.:-]+$/.test(String(objectId))) return null;
  const url = new URL(`https://${host}/${encodeURIComponent(String(objectId))}`);
  return url.href;
}

function appendClasses(parent, classes) {
  for (const [name, weight] of Object.entries(classes || {})) {
    const line = document.createElement("div");
    line.textContent = `${name}: ${Number(weight).toFixed(4)}`;
    parent.append(line);
  }
}

function objectLink(survey, objectId) {
  const url = portalUrl(survey, objectId);
  if (!url) return null;
  const link = document.createElement("a");
  link.href = url;
  link.target = "_blank";
  link.rel = "noopener noreferrer";
  link.textContent = "Fink Portal";
  return link;
}

function appendObjectRow(parent, objectId, value, survey, main = false) {
  const row = document.createElement("div");
  row.className = `objLine${main ? " mainObj" : ""}`;
  const heading = document.createElement("div");
  const strong = document.createElement("strong");
  strong.textContent = String(objectId);
  heading.append(strong);
  const link = objectLink(survey, objectId);
  if (link) {
    heading.append(" (", link, ")");
  }
  if (!main) heading.append(` — distance ${Number(value.distance).toFixed(4)}`);
  row.append(heading);
  appendClasses(row, main ? value : value.classes);
  parent.append(row);
}

function updateDetailsPanel(data, survey) {
  const panel = document.getElementById("objectList");
  panel.replaceChildren();
  appendObjectRow(panel, data.objectId, data.objectClassification, survey, true);
  Object.entries(data.objects || {})
    .map(([objectId, value]) => ({ objectId: String(objectId), value }))
    .sort((a, b) => Number(a.value.distance) - Number(b.value.distance))
    .forEach(({ objectId, value }) => appendObjectRow(panel, objectId, value, survey));
}
