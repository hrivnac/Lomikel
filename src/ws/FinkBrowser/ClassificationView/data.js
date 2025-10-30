async function fetchNeighborhood(params) {
  const query = new URLSearchParams(params).toString();
  const url = `/FinkBrowser/Neighborhood.jsp?${query}`;
  try {
    showSpinner(true, "green");
    const response = await fetch(url);
    if (!response.ok) throw new Error("Network error");
    return await response.json();
    }
  catch (err) {
    window.alert("Neighborhood search failed, using demo data");
    console.warn("Neighborhood.jsp failed, using demo data:", err);
    return {
      objectId: "ZTF23abdlxeb",
      objects: {
        "ZTF19actbknb": {
          distance: 0.0023,
          classes: {"YSO_Candidate": 0.8571, "SN candidate": 0.1429}
          },
        "ZTF19actfogx": {
          distance: 0.0363,
          classes: {"Radio": 0.4707, "YSO_Candidate": 0.0608, "CataclyV*_Candidate": 0.1943, "CV*_Candidate": 0.2623}
          }
        },
      objectClassification: {"YSO_Candidate": 0.8333, "SN candidate": 0.1667}
      };
    }
  finally {
    showSpinner(false);
    }
  }
  
async function loadNeighborhood(objectId = null) {
  const nmaxText = document.getElementById("nmaxValue").textContent;
  const nmaxVal = parseFloat(nmaxText);
  const params = {objectId: objectId || document.getElementById("objectId").value,
                  classifier: document.getElementById("classifier").value,
                  reclassifier: document.getElementById("reclassifier").value,
                  alg: document.getElementById("alg").value,
                  nmax: nmaxVal
                  };
  const data = await fetchNeighborhood(params);
  updateDetailsPanel(data);
  showObjectNeighborhood(data);
  }

