"use strict";

(function exposeLayout(root) {
  const CLASS_ANCHOR_SCALE = 0.58;
  const GRAPH_DISTANCE_SCALE = 0.32;

  function finiteWeight(value) {
    const weight = Number(value);
    return Number.isFinite(weight) && weight > 0 ? weight : 0;
  }

  function classificationAnchor(classMap, classPositions, geometry) {
    let weightedX = 0;
    let weightedY = 0;
    let totalWeight = 0;

    for (const [name, rawWeight] of Object.entries(classMap || {})) {
      const position = classPositions[name];
      const weight = finiteWeight(rawWeight);
      if (!position || weight === 0) continue;
      weightedX += position.x * weight;
      weightedY += position.y * weight;
      totalWeight += weight;
    }

    if (totalWeight === 0) {
      return { x: geometry.centerX, y: geometry.centerY };
    }

    const x = weightedX / totalWeight;
    const y = weightedY / totalWeight;
    return {
      x: geometry.centerX + (x - geometry.centerX) * CLASS_ANCHOR_SCALE,
      y: geometry.centerY + (y - geometry.centerY) * CLASS_ANCHOR_SCALE,
    };
  }

  function deterministicDirection(id) {
    let hash = 2166136261;
    for (const character of String(id)) {
      hash ^= character.codePointAt(0);
      hash = Math.imul(hash, 16777619);
    }
    const angle = ((hash >>> 0) / 0x100000000) * Math.PI * 2;
    return { x: Math.cos(angle), y: Math.sin(angle) };
  }

  function unitDirection(from, toward, id) {
    const dx = toward.x - from.x;
    const dy = toward.y - from.y;
    const length = Math.hypot(dx, dy);
    return length > 1e-9
      ? { x: dx / length, y: dy / length }
      : deterministicDirection(id);
  }

  function edgeLabelPosition(from, toward, offset = 8) {
    const dx = toward.x - from.x;
    const dy = toward.y - from.y;
    const length = Math.hypot(dx, dy) || 1;
    return {
      x: (from.x + toward.x) / 2 - (dy / length) * offset,
      y: (from.y + toward.y) / 2 + (dx / length) * offset,
    };
  }

  function computeObjectLayout(data, classPositions, geometry) {
    const main = {
      id: String(data.objectId),
      classes: data.objectClassification || {},
      ...classificationAnchor(data.objectClassification, classPositions, geometry),
    };
    const rawNeighbors = Object.entries(data.objects || {}).map(([rawId, object]) => {
      const rawDistance = object.distance;
      if (
        typeof rawDistance !== "number" ||
        !Number.isFinite(rawDistance) ||
        rawDistance < 0
      ) {
        throw new TypeError(`neighbor ${rawId} distance must be finite and non-negative`);
      }
      return {
        id: String(rawId),
        classes: object.classes || {},
        distance: rawDistance,
      };
    });
    const maxDistance = rawNeighbors.reduce(
      (maximum, object) => Math.max(maximum, object.distance),
      0,
    );
    const maxVisualDistance = geometry.radius * GRAPH_DISTANCE_SCALE;
    const zeroDistanceIds = rawNeighbors
      .filter((object) => object.distance === 0)
      .map((object) => object.id)
      .sort();
    const zeroDistanceIndex = new Map(
      zeroDistanceIds.map((id, index) => [id, index]),
    );
    const zeroDistancePhase = Math.atan2(
      deterministicDirection(main.id).y,
      deterministicDirection(main.id).x,
    );

    const neighbors = rawNeighbors.map((object) => {
      const anchor = classificationAnchor(object.classes, classPositions, geometry);
      const visuallyOffset = object.distance === 0;
      const directionUndefined = Math.hypot(anchor.x - main.x, anchor.y - main.y) <= 1e-9;
      let direction = unitDirection(main, anchor, object.id);
      let visualDistance;
      if (visuallyOffset) {
        visualDistance = geometry.radius * 0.06;
        if (zeroDistanceIds.length > 1) {
          const angle = zeroDistancePhase
            + (2 * Math.PI * zeroDistanceIndex.get(object.id)) / zeroDistanceIds.length;
          direction = { x: Math.cos(angle), y: Math.sin(angle) };
        }
      } else {
        visualDistance = Math.sqrt(object.distance / maxDistance) * maxVisualDistance;
      }
      return {
        ...object,
        visuallyOffset,
        directionUndefined,
        collisionOffset: false,
        x: main.x + direction.x * visualDistance,
        y: main.y + direction.y * visualDistance,
      };
    });

    const collisions = new Map();
    for (const object of neighbors) {
      const key = `${object.x.toFixed(8)}|${object.y.toFixed(8)}`;
      const group = collisions.get(key) || [];
      group.push(object);
      collisions.set(key, group);
    }
    for (const group of collisions.values()) {
      if (group.length < 2) continue;
      group.sort((first, second) => first.id.localeCompare(second.id));
      const distance = Math.hypot(group[0].x - main.x, group[0].y - main.y);
      const baseAngle = Math.atan2(group[0].y - main.y, group[0].x - main.x);
      const angularStep = Math.min(0.24, 0.7 / group.length);
      group.forEach((object, index) => {
        const angle = baseAngle + (index - (group.length - 1) / 2) * angularStep;
        object.x = main.x + Math.cos(angle) * distance;
        object.y = main.y + Math.sin(angle) * distance;
        object.collisionOffset = true;
      });
    }

    return { main, neighbors, maxDistance };
  }

  root.computeObjectLayout = computeObjectLayout;
  root.edgeLabelPosition = edgeLabelPosition;
  if (typeof module === "object" && module.exports) {
    module.exports = { computeObjectLayout, edgeLabelPosition };
  }
})(typeof globalThis === "object" ? globalThis : this);
