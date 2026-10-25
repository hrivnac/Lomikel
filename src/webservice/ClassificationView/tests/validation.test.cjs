"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");

const {
  parseNeighborhoodLimit,
  validateNeighborhoodData,
} = require("../validation.js");

test("neighbor limit accepts supported count, cutoff, and all modes", () => {
  assert.equal(parseNeighborhoodLimit("5"), 5);
  assert.equal(parseNeighborhoodLimit("0.25"), 0.25);
  assert.equal(parseNeighborhoodLimit("0"), 0);
});

test("neighbor limit rejects empty and unsupported values", () => {
  for (const value of ["", "   ", "1.01", "21", "-1", "nope"]) {
    assert.throws(() => parseNeighborhoodLimit(value), /neighbor limit/i);
  }
});

test("neighborhood validation preserves exact IDs and finite measurements", () => {
  const data = validateNeighborhoodData({
    objectId: "170028486134595648",
    objectClassification: { A: "0.75" },
    objects: {
      "170028486134595649": { distance: 0.125, classes: { B: "1e0" } },
    },
  }, "170028486134595648");

  assert.equal(data.objectId, "170028486134595648");
  assert.deepEqual(data.objects["170028486134595649"], {
    distance: 0.125,
    classes: { B: 1 },
  });
  assert.deepEqual(data.objectClassification, { A: 0.75 });
});

test("neighborhood validation rejects invalid scientific measurements", () => {
  for (const distance of [undefined, "", "bad", "1", false, true, [], [1], -1, Infinity, NaN]) {
    assert.throws(() => validateNeighborhoodData({
      objectId: "target",
      objectClassification: { A: 1 },
      objects: { neighbor: { distance, classes: { A: 1 } } },
    }, "target"), /distance/i);
  }
  assert.throws(() => validateNeighborhoodData({
    objectId: "target",
    objectClassification: { A: -0.1 },
    objects: {},
  }, "target"), /weight/i);

  for (const weight of [false, true, "   ", " 1", "1 ", "1x", "-1", [], [1]]) {
    assert.throws(() => validateNeighborhoodData({
      objectId: "target",
      objectClassification: { A: weight },
      objects: {},
    }, "target"), /weight/i);
  }
});

test("neighborhood validation bounds total class-layout work", () => {
  const manyClasses = Object.fromEntries(
    Array.from({ length: 129 }, (_, index) => [`class-${index}`, 1]),
  );
  assert.throws(() => validateNeighborhoodData({
    objectId: "target",
    objectClassification: manyClasses,
    objects: {},
  }, "target"), /too many.*classes/i);
});

test("neighborhood validation rejects lossy or mismatched target IDs", () => {
  assert.throws(() => validateNeighborhoodData({
    objectId: 170028486134595648,
    objectClassification: {},
    objects: {},
  }, "170028486134595648"), /string/i);
  assert.throws(() => validateNeighborhoodData({
    objectId: "different",
    objectClassification: {},
    objects: {},
  }, "target"), /does not match/i);
});
