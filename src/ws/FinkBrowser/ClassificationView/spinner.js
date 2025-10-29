function showSpinner(show) {
  document.getElementById("loading-spinner").style.display = show ? "flex" : "none";
  }
  
function setSpinnerColor(color) {
  const spinner = document.getElementById("loading-spinner");
  if (spinner) spinner.style.borderTopColor = color;
}

setSpinnerColor("#00ff00"); 
