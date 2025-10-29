function showSpinnerx(show) {
  document.getElementById("loading-spinner").style.display = show ? "flex" : "none";
  }
  
function showSpinner(isLoading, color = "#ff0000") {
  const spinner = document.getElementById("loading-spinner");
  if (!spinner) return;
  spinner.style.borderTopColor = color;
  spinner.style.display = isLoading ? "flex" : "none";
}
  
  
