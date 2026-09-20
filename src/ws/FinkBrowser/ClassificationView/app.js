const startupObjectId = new URLSearchParams(window.location.search).get("objectId");
if (startupObjectId) {
  document.getElementById("objectId").value = startupObjectId;
}
setStatus("Choose parameters, then press Show neighborhood. Graph queries can take several seconds.", "idle");
