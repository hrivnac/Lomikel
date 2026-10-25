"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");

const {
  computeObjectLayout,
  edgeLabelPosition,
} = require("../layout.js");

function separation(first, second) {
  return Math.hypot(first.x - second.x, first.y - second.y);
}

test("object layout preserves target-neighbor graph-distance ordering", () => {
  const layout = computeObjectLayout(
    {
      objectId: "target",
      objectClassification: { A: 1 },
      objects: {
        near: { distance: 0.25, classes: { B: 1 } },
        far: { distance: 1, classes: { C: 1 } },
      },
    },
    {
      A: { x: 100, y: 0 },
      B: { x: 0, y: 100 },
      C: { x: -100, y: 0 },
    },
    { centerX: 0, centerY: 0, radius: 100 },
  );

  const near = layout.neighbors.find((object) => object.id === "near");
  const far = layout.neighbors.find((object) => object.id === "far");
  assert.ok(separation(layout.main, near) < separation(layout.main, far));
  assert.ok(Math.abs(separation(layout.main, near) - 16) < 1e-9);
  assert.ok(Math.abs(separation(layout.main, far) - 32) < 1e-9);
});

test("object layout keeps points inside the class circle", () => {
  const layout = computeObjectLayout(
    {
      objectId: "target",
      objectClassification: { A: 1 },
      objects: {
        same: { distance: 1, classes: { A: 1 } },
      },
    },
    { A: { x: 150, y: 50 } },
    { centerX: 50, centerY: 50, radius: 100 },
  );

  for (const object of [layout.main, ...layout.neighbors]) {
    assert.ok(Math.hypot(object.x - 50, object.y - 50) <= 90 + 1e-9);
  }
});

test("coincident classification anchors use deterministic directions", () => {
  const input = {
    objectId: "target",
    objectClassification: { A: 1 },
    objects: { candidate: { distance: 1, classes: { A: 1 } } },
  };
  const positions = { A: { x: 100, y: 0 } };
  const geometry = { centerX: 0, centerY: 0, radius: 100 };

  assert.deepEqual(
    computeObjectLayout(input, positions, geometry),
    computeObjectLayout(input, positions, geometry),
  );
});

test("zero-distance neighbors receive a small deterministic visibility offset", () => {
  const layout = computeObjectLayout(
    {
      objectId: "target",
      objectClassification: { A: 1 },
      objects: {
        first: { distance: 0, classes: { A: 1 } },
        second: { distance: 0, classes: { A: 1 } },
      },
    },
    { A: { x: 100, y: 0 } },
    { centerX: 0, centerY: 0, radius: 100 },
  );

  const [first, second] = layout.neighbors;
  assert.ok(separation(layout.main, first) > 0);
  assert.ok(separation(layout.main, first) <= 8);
  assert.ok(separation(first, second) > 0);
  assert.deepEqual(
    layout,
    computeObjectLayout(
      {
        objectId: "target",
        objectClassification: { A: 1 },
        objects: {
          first: { distance: 0, classes: { A: 1 } },
          second: { distance: 0, classes: { A: 1 } },
        },
      },
      { A: { x: 100, y: 0 } },
      { centerX: 0, centerY: 0, radius: 100 },
    ),
  );
});

test("edge labels offset perpendicularly to avoid the central star", () => {
  assert.deepEqual(
    edgeLabelPosition({ x: 0, y: 0 }, { x: 10, y: 0 }, 6),
    { x: 5, y: 6 },
  );
  assert.deepEqual(
    edgeLabelPosition({ x: 0, y: 0 }, { x: -10, y: 0 }, 6),
    { x: -5, y: -6 },
  );
});

test("object layout rejects invalid graph distances instead of plotting zero", () => {
  for (const distance of [undefined, "bad", "1", false, true, [], [1], -1, Infinity, NaN]) {
    assert.throws(() => computeObjectLayout(
      {
        objectId: "target",
        objectClassification: { A: 1 },
        objects: { candidate: { distance, classes: { A: 1 } } },
      },
      { A: { x: 100, y: 0 } },
      { centerX: 0, centerY: 0, radius: 100 },
    ), /distance/i);
  }
});

test("equal-distance equal-affinity neighbors remain separately selectable", () => {
  const layout = computeObjectLayout(
    {
      objectId: "target",
      objectClassification: { A: 1 },
      objects: {
        first: { distance: 0.5, classes: { B: 1 } },
        second: { distance: 0.5, classes: { B: 1 } },
      },
    },
    { A: { x: 100, y: 0 }, B: { x: 0, y: 100 } },
    { centerX: 0, centerY: 0, radius: 100 },
  );

  const [first, second] = layout.neighbors;
  assert.notDeepEqual({ x: first.x, y: first.y }, { x: second.x, y: second.y });
  assert.ok(Math.abs(separation(layout.main, first) - separation(layout.main, second)) < 1e-9);
  assert.ok(first.collisionOffset);
  assert.ok(second.collisionOffset);
});