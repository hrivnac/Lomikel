document.getElementById("help-btn").onclick = () => document.getElementById("help-modal").style.display = "block";
document.getElementById("close-help").onclick = () => document.getElementById("help-modal").style.display = "none";
window.onclick = (event) => {if (event.target === document.getElementById("help-modal")) document.getElementById("help-modal").style.display = "none";};
