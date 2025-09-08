function updateStatusPanel(count) {
  const el = document.getElementById("statusPanel");
  const now = new Date();
  const timestamp = now.toLocaleTimeString();
  el.textContent = `Last update: ${timestamp} | Alerts: ${count}`;
  }