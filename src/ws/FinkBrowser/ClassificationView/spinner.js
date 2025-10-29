function showSpinner(show, color = "3498db") {
  const spinner = document.getElementById("loading-spinner");
  if (!spinner) return;
  spinner.style.display = show ? "flex" : "none";
  document.getElementById("spinner-wheel").style.borderTopColor = color;
  }
  
  
