const helpModal = document.getElementById("help-modal");
const helpButton = document.getElementById("help-btn");
const closeHelpButton = document.getElementById("close-help");
const backgroundElements = [...document.querySelectorAll(
  "header, #workspace, #tooltip, #status, #loading-spinner",
)];
const previousInert = new Map();

function openHelp() {
  for (const element of backgroundElements) {
    previousInert.set(element, element.inert);
    element.inert = true;
  }
  helpModal.hidden = false;
  helpButton.setAttribute("aria-expanded", "true");
  closeHelpButton.focus();
}

function closeHelp() {
  if (helpModal.hidden) return;
  helpModal.hidden = true;
  for (const element of backgroundElements) {
    if (previousInert.get(element)) element.inert = true;
    else element.inert = false;
  }
  previousInert.clear();
  helpButton.setAttribute("aria-expanded", "false");
  helpButton.focus();
}

helpButton.addEventListener("click", openHelp);
closeHelpButton.addEventListener("click", closeHelp);
helpModal.addEventListener("click", (event) => {
  if (event.target === helpModal) closeHelp();
});
document.addEventListener("keydown", (event) => {
  if (helpModal.hidden) return;
  if (event.key === "Escape") closeHelp();
  if (event.key === "Tab") {
    event.preventDefault();
    closeHelpButton.focus();
  }
});
