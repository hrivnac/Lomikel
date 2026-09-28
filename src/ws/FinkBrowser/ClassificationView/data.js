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
  
let neighborhoodRequest = 0;

async function loadNeighborhood(objectId = null) {
  const request = ++neighborhoodRequest;
  const input = document.getElementById("objectId");
  if (objectId !== null) input.value = String(objectId);
  const id = input.value.trim();
  const survey = syncSurveyFromId(id);
  if (!survey) {
    window.alert("Enter a ZTF object ID (starting ZTF) or a numeric LSST object ID.");
    return;
  }
  const nmaxText = document.getElementById("nmaxValue").textContent;
  const nmaxVal = parseFloat(nmaxText);
  const params = {objectId: id,
                  survey: survey,
                  classifier: document.getElementById("classifier").value,
                  reclassifier: document.getElementById("reclassifier").value,
                  metric: document.getElementById("metric").value,
                  nmax: nmaxVal
                  };
  const data = await fetchNeighborhood(params);
  if (request !== neighborhoodRequest) return;
  updateDetailsPanel(data, survey);
  showObjectNeighborhood(data);
  }

