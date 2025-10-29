function showSpinnerx(show) {
  document.getElementById("loading-spinner").style.display = show ? "flex" : "none";
  }
  
function showSpinner(show, color = "#ff0000") {
  const spinner = document.getElementById("loading-spinner");
  if (!spinner) return;
  document.getElementById("sspp").style.borderTopColor = color;
  spinner.style.display = show ? "flex" : "none";
}
  
  
