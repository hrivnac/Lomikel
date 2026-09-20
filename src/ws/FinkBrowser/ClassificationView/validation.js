"use strict";

(function exposeValidation(root) {
  const MAX_RENDERED_NEIGHBORS = 200;
  const MAX_CLASSES_PER_OBJECT = 128;
  const MAX_TOTAL_CLASSES = 128;
  const SAFE_OBJECT_ID = /^[A-Za-z0-9_.:-]+$/;
  const NON_NEGATIVE_NUMBER_TEXT = /^(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/;

  function isRecord(value) {
    return value !== null && typeof value === "object" && !Array.isArray(value);
  }

  function parseNeighborhoodLimit(rawValue) {
    const source = String(rawValue).trim();
    if (!source) throw new TypeError("neighbor limit is required");
    const value = Number(source);
    const isCutoff = value >= 0 && value < 1;
    const isCount = Number.isInteger(value) && value >= 1 && value <= 20;
    if (!Number.isFinite(value) || (!isCutoff && !isCount)) {
      throw new RangeError(
        "neighbor limit must be 0, a cutoff between 0 and 1, or an integer from 1 to 20",
      );
    }
    return value;
  }

  function validateClasses(classes, context) {
    if (!isRecord(classes)) throw new TypeError(`${context} classes must be an object`);
    const entries = Object.entries(classes);
    if (entries.length > MAX_CLASSES_PER_OBJECT) {
      throw new RangeError(`${context} has too many classes to render safely`);
    }
    return Object.fromEntries(entries.map(([name, rawWeight]) => {
      const weight = typeof rawWeight === "number"
        ? rawWeight
        : typeof rawWeight === "string" && NON_NEGATIVE_NUMBER_TEXT.test(rawWeight)
          ? Number(rawWeight)
          : Number.NaN;
      if (
        !name.trim() ||
        !Number.isFinite(weight) ||
        weight < 0
      ) {
        throw new TypeError(`${context} class weight for ${name || "<empty>"} is invalid`);
      }
      return [name, weight];
    }));
  }

  function validateNeighborhoodData(data, requestedObjectId) {
    if (!isRecord(data)) throw new TypeError("neighborhood response must be an object");
    if (typeof data.objectId !== "string") {
      throw new TypeError("neighborhood object ID must be a string to preserve it exactly");
    }
    if (data.objectId !== requestedObjectId) {
      throw new Error("neighborhood object ID does not match the requested object");
    }
    if (!isRecord(data.objects)) throw new TypeError("neighborhood objects must be an object");
    const objectEntries = Object.entries(data.objects);
    if (objectEntries.length > MAX_RENDERED_NEIGHBORS) {
      throw new RangeError(
        `neighborhood contains ${objectEntries.length} objects; choose a smaller limit (maximum rendered: ${MAX_RENDERED_NEIGHBORS})`,
      );
    }

    const objectClassification = validateClasses(
      data.objectClassification,
      `object ${requestedObjectId}`,
    );
    const objects = Object.fromEntries(objectEntries.map(([id, object]) => {
      if (!id || !SAFE_OBJECT_ID.test(id)) {
        throw new TypeError(`neighbor object ID ${id || "<empty>"} is invalid`);
      }
      if (!isRecord(object)) throw new TypeError(`neighbor ${id} must be an object`);
      const rawDistance = object.distance;
      if (
        typeof rawDistance !== "number" ||
        !Number.isFinite(rawDistance) ||
        rawDistance < 0
      ) {
        throw new TypeError(`neighbor ${id} distance is invalid`);
      }
      return [id, {
        distance: rawDistance,
        classes: validateClasses(object.classes, `neighbor ${id}`),
      }];
    }));
    const totalClasses = new Set(Object.keys(objectClassification));
    for (const object of Object.values(objects)) {
      Object.keys(object.classes).forEach((name) => totalClasses.add(name));
      if (totalClasses.size > MAX_TOTAL_CLASSES) {
        throw new RangeError(
          `neighborhood has too many distinct classes to render safely (maximum: ${MAX_TOTAL_CLASSES})`,
        );
      }
    }
    if (totalClasses.size > MAX_TOTAL_CLASSES) {
      throw new RangeError(
        `neighborhood has too many distinct classes to render safely (maximum: ${MAX_TOTAL_CLASSES})`,
      );
    }

    return {
      objectId: requestedObjectId,
      objectClassification,
      objects,
    };
  }

  root.parseNeighborhoodLimit = parseNeighborhoodLimit;
  root.validateNeighborhoodData = validateNeighborhoodData;
  if (typeof module === "object" && module.exports) {
    module.exports = { parseNeighborhoodLimit, validateNeighborhoodData };
  }
})(typeof globalThis === "object" ? globalThis : this);
