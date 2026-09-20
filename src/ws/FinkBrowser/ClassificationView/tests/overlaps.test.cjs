"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");

const { normalizeOverlapLinks } = require("../overlaps.js");

test("symmetric overlap records become one undirected class link", () => {
  const records = [
    { first: { class: "A" }, second: { class: "B" }, overlap: 10 },
    { first: { class: "B" }, second: { class: "A" }, overlap: 10 },
    { first: { class: "A" }, second: { class: "A" }, overlap: 50 },
    { first: { class: "A" }, second: { class: "outside" }, overlap: 30 },
  ];

  assert.deepEqual(normalizeOverlapLinks(records, ["A", "B"]), {
    links: [{ source: "A", target: "B", value: 10 }],
    maxOverlap: 10,
  });
});

test("duplicate overlap records keep the strongest finite value", () => {
  const records = [
    { first: { class: "A" }, second: { class: "B" }, overlap: 3 },
    { first: { class: "B" }, second: { class: "A" }, overlap: 8 },
    { first: { class: "A" }, second: { class: "B" }, overlap: Number.NaN },
    { first: { class: "A" }, second: { class: "B" }, overlap: -5 },
    { first: { class: "A" }, second: { class: "B" }, overlap: 0 },
  ];

  assert.equal(normalizeOverlapLinks(records, ["A", "B"]).links[0].value, 8);
});

test("non-positive intersection counts are ignored", () => {
  const records = [
    { first: { class: "A" }, second: { class: "B" }, overlap: -5 },
    { first: { class: "B" }, second: { class: "A" }, overlap: 0 },
  ];
  assert.deepEqual(normalizeOverlapLinks(records, ["A", "B"]), {
    links: [],
    maxOverlap: 0,
  });
});

test("non-numeric JSON types and numeric strings are not overlap counts", () => {
  const records = [
    { first: { class: "A" }, second: { class: "B" }, overlap: true },
    { first: { class: "B" }, second: { class: "A" }, overlap: "7" },
  ];
  assert.deepEqual(normalizeOverlapLinks(records, ["A", "B"]), {
    links: [],
    maxOverlap: 0,
  });
});
