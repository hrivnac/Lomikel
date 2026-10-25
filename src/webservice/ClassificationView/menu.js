function makeDraggable(header, panel) {
  const state = {
    x: 0,
    y: 0,
    animation: null,
    activePointer: null,
    startX: 0,
    startY: 0,
    originX: 0,
    originY: 0,
    startRect: null,
  };

  const applyTranslation = (x, y) => {
    state.animation?.cancel();
    state.animation = panel.animate(
      [{ transform: `translate(${x}px, ${y}px)` }],
      { duration: 1, fill: "forwards" },
    );
    state.animation.finish();
    state.x = x;
    state.y = y;
  };

  const stopDragging = (event) => {
    if (event.pointerId !== state.activePointer) return;
    state.activePointer = null;
  };

  header.addEventListener("pointerdown", (event) => {
    if (window.matchMedia("(max-width: 760px)").matches) return;
    state.activePointer = event.pointerId;
    state.startX = event.clientX;
    state.startY = event.clientY;
    state.originX = state.x;
    state.originY = state.y;
    state.startRect = panel.getBoundingClientRect();
    header.setPointerCapture(event.pointerId);
  });

  header.addEventListener("pointermove", (event) => {
    if (event.pointerId !== state.activePointer) return;
    const parentRect = panel.offsetParent.getBoundingClientRect();
    const rawLeft = state.startRect.left + event.clientX - state.startX;
    const rawTop = state.startRect.top + event.clientY - state.startY;
    const left = Math.min(
      parentRect.right - state.startRect.width,
      Math.max(parentRect.left, rawLeft),
    );
    const top = Math.min(
      parentRect.bottom - state.startRect.height,
      Math.max(parentRect.top, rawTop),
    );
    applyTranslation(
      state.originX + left - state.startRect.left,
      state.originY + top - state.startRect.top,
    );
  });

  header.addEventListener("pointerup", stopDragging);
  header.addEventListener("pointercancel", stopDragging);
  header.addEventListener("lostpointercapture", stopDragging);
}

makeDraggable(document.getElementById("controls-header"), document.getElementById("controls"));
makeDraggable(document.getElementById("list-header"), document.getElementById("list"));

const controlsForm = document.getElementById("controlsForm");
controlsForm.addEventListener("submit", (event) => {
  event.preventDefault();
  if (controlsForm.reportValidity()) loadNeighborhood();
});

document.getElementById("cancelBtn").addEventListener("click", cancelNeighborhoodLoad);
document.getElementById("resetBtn").addEventListener("click", () => window.resetZoom?.());
