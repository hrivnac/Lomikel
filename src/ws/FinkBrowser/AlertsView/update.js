function updateStatusPanel() {
  const statusPanel = document.getElementById("statusPanel");
  const now = new Date().toLocaleTimeString();
  const configInfo = `<span style="color:lightblue">
    survey=${survey},
    fetchPeriod=${fetchPeriod}m, 
    fetchStart=${fetchStart}h, 
    nAlerts=${nAlerts},
    magMax=${magMax}
  </span>`;
  const updateInfo = `<span style="color:green">
    Last update: ${now}, Alerts loaded: ${alertsPool.length}
  </span>`;
  statusPanel.innerHTML = `${configInfo} &nbsp;&nbsp; ${updateInfo}`;
  }
  