async function getOverlapPositions(classifier, classList, radius, centerX, centerY) {
  try {
    showSpinner(true, "green");
    const url = `/FinkBrowser/Overlaps.jsp?classifier=${classifier}`;
    const response = await fetch(url);
    if (!response.ok) throw new Error("Failed to fetch overlaps");
    const overlaps = await response.json();
    if (!Array.isArray(overlaps) || overlaps.length === 0) {
      throw new Error("No overlap data");
      }
    // Filter and normalize overlaps
    const links = [];
    const overlapMap = {};
    let maxOverlap = 0;
    //
    for (const item of overlaps) {
      const c1 = item.first.class?.trim();
      const c2 = item.second.class?.trim();
      const val = parseFloat(item.overlap);
      if (!c1 || !c2 || c1 === c2 || isNaN(val)) continue; // ignore invalid or self
      if (!classList.includes(c1) || !classList.includes(c2)) continue; // keep only relevant classes
      overlapMap[c1] = overlapMap[c1] || {};
      overlapMap[c1][c2] = val;
      links.push({ source: c1, target: c2, value: val });
      if (val > maxOverlap) maxOverlap = val;
      }
    if (links.length === 0) throw new Error("No valid overlap links");
    // Create nodes
    const nodes = classList.map(c => ({ id: c }));
    // Define force simulation
    const minDist = radius * 0.15;
    const maxDist = radius * 1.2;
    const simLinks = links.map(l => ({source: l.source,
                                      target: l.target,
                                      distance: minDist + (1 - l.value / maxOverlap) * (maxDist - minDist),
                                      strength: 0.3 + 0.7 * (l.value / maxOverlap)
                                      }));
    const simulation = d3.forceSimulation(nodes)
                         .force("link", d3.forceLink(simLinks)
                                          .id(d => d.id)
                                          .distance(d => d.distance)
                                          .strength(d => d.strength))
                         .force("charge", d3.forceManyBody().strength(-radius * 0.6))
                         .force("center", d3.forceCenter(centerX, centerY))
                         .stop();
    // Run layout simulation
    for (let i = 0; i < 300; i++) simulation.tick();    
    // Normalize final positions onto a circular boundary
    const positions = {};
    nodes.forEach(n => {const angle = Math.atan2(n.y - centerY, n.x - centerX);           
                        positions[n.id] = {angle: angle,
                                           x: centerX + radius * Math.cos(angle),
                                           y: centerY + radius * Math.sin(angle)
                                           };
                        });
    return positions;
    }
  catch (err) {
    window.alert("Overlap layout failed, using equidistant fallback");
    console.warn("Overlap layout failed, using equidistant fallback:", err.message);
    // fallback layout
    const angleScale = d3.scaleLinear()
                         .domain([0, classList.length])
                         .range([0, 2 * Math.PI]);
    const pos = {};
    classList.forEach((c, i) => {const a = angleScale(i);
                                 pos[c] = {x: centerX + radius * Math.cos(a),
                                           y: centerY + radius * Math.sin(a)
                                           };
                                 });
    return pos;
    }
  finally {
    showSpinner(false);
    }
  }
