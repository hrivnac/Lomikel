function portalUrl(survey, objectId) {
  const hosts = { LSST: "lsst.fink-portal.org", ZTF: "ztf.fink-portal.org" };
  const host = hosts[survey];
  if (!host || !/^[A-Za-z0-9_.:-]+$/.test(String(objectId))) return null;
  const url = new URL(`https://${host}/${encodeURIComponent(String(objectId))}`);
  return url.href;
}

function appendClasses(parent, classes) {
  const summary = Object.entries(classes || {})
    .map(([name, weight]) => ({ name, weight }))
    .sort((first, second) => second.weight - first.weight)
    .map(({ name, weight }) => `${name}: ${weight.toFixed(4)}`)
    .join(" · ");
  if (!summary) return;
  const line = document.createElement("div");
  line.className = "class-weight class-weight-summary";
  line.textContent = summary;
  parent.append(line);
}

function objectLink(survey, objectId) {
  const url = portalUrl(survey, objectId);
  if (!url) return null;
  const link = document.createElement("a");
  link.href = url;
  link.target = "_blank";
  link.rel = "noopener noreferrer";
  link.textContent = "Portal ↗";
  link.setAttribute("aria-label", `Open ${objectId} in Fink Portal (new tab)`);
  return link;
}

function appendObjectRow(parent, objectId, value, survey, main = false) {
  const row = document.createElement("div");
  row.className = `objLine${main ? " mainObj" : ""}`;

  const heading = document.createElement("div");
  heading.className = "object-heading";
  const identity = document.createElement("div");
  const strong = document.createElement("strong");
  strong.textContent = String(objectId);
  identity.append(strong);
  if (!main) {
    const distance = document.createElement("span");
    distance.className = "object-distance";
    distance.textContent = `distance ${Number(value.distance).toPrecision(4)}`;
    identity.append(distance);
  }
  heading.append(identity);

  const actions = document.createElement("div");
  actions.className = "object-actions";
  if (!main) {
    const centerButton = document.createElement("button");
    centerButton.type = "button";
    centerButton.textContent = "Center";
    centerButton.addEventListener("click", () => loadNeighborhood(objectId));
    actions.append(centerButton);
  }
  const link = objectLink(survey, objectId);
  if (link) actions.append(link);
  heading.append(actions);

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
    .sort((first, second) => Number(first.value.distance) - Number(second.value.distance))
    .forEach(({ objectId, value }) => appendObjectRow(panel, objectId, value, survey));
}
