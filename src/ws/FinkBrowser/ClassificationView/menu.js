function makeDraggable(header, panel) {
  let offsetX = 0; let offsetY = 0; let active = false;
  header.addEventListener("pointerdown", (event) => {
    active = true; header.setPointerCapture(event.pointerId);
    offsetX = event.clientX - panel.offsetLeft; offsetY = event.clientY - panel.offsetTop;
  });
  header.addEventListener("pointermove", (event) => {
    if (!active) return;
    panel.style.left = `${Math.max(0, event.clientX - offsetX)}px`;
    panel.style.top = `${Math.max(0, event.clientY - offsetY)}px`;
  });
  header.addEventListener("pointerup", () => { active = false; });
}
makeDraggable(document.getElementById("controls-header"), document.getElementById("controls"));
makeDraggable(document.getElementById("list-header"), document.getElementById("list"));

document.getElementById("showBtn").addEventListener("click", () => loadNeighborhood());
document.getElementById("resetBtn").addEventListener("click", () => window.resetZoom?.());
document.getElementById("survey").addEventListener("change", () => loadNeighborhood());
