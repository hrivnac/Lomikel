const helpModal = document.getElementById("help-modal");
const helpButton = document.getElementById("help-btn");
helpButton.addEventListener("click", () => {
  helpModal.hidden = false;
  helpButton.setAttribute("aria-expanded", "true");
  document.getElementById("close-help").focus();
});
document.getElementById("close-help").addEventListener("click", () => {
  helpModal.hidden = true;
  helpButton.setAttribute("aria-expanded", "false");
  helpButton.focus();
});
helpModal.addEventListener("click", (event) => {
  if (event.target === helpModal) document.getElementById("close-help").click();
});
