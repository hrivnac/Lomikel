function updateStatusPanel() {
  const statusPanel = document.getElementById("statusPanel");
  const describe = (survey, status) => {
    if (status.state === "paused") return `${survey}: paused (no recent alert production)`;
    if (status.state === "loading") return `${survey}: loading`;
    const updated = status.updatedAt
      ? `${status.updatedAt.toISOString().slice(11, 19)} UTC`
      : "not updated";
    if (status.state === "error") return `${survey}: update failed (${status.errors.length} errors)`;
    if (status.state === "partial") return `${survey}: ${status.count} alerts, partial update (${status.errors.length} errors), ${updated}`;
    if (status.state === "empty") return `${survey}: no recent alerts, ${updated}`;
    return `${survey}: ${status.count} alerts, ${updated}`;
    };
  const configInfo = `fetchPeriod=${fetchPeriod}m, fetchStart=${fetchStart}h, nAlerts/class=${nAlerts}, magMax=${magMax}`;
  statusPanel.textContent = `${configInfo} | ${describe("ZTF", surveyStatus.ZTF)} | ${describe("LSST", surveyStatus.LSST)}`;
  }
  