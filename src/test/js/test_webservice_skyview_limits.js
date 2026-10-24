'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const page = fs.readFileSync(path.join(__dirname, '../../webservice/d3/skyview.jsp'), 'utf8');
// Form decoding must not delegate to the servlet's unbounded parameter parser.
assert.doesNotMatch(page, /request\.getParameter\s*\(/);
assert.match(page, /request\.getInputStream\s*\(/);
assert.match(page, /MAX_REQUEST_BYTES\s*=\s*\d+/);
assert.match(page, /getContentLengthLong\(\)\s*>\s*MAX_REQUEST_BYTES/);
assert.match(page, /input\.read\(chunk,\s*0,\s*Math\.min\(chunk\.length,\s*MAX_REQUEST_BYTES\s*\+\s*1\s*-\s*bytes\.size\(\)\)\)/);
assert.match(page, /bytes\.size\(\)\s*\+\s*n\s*>\s*MAX_REQUEST_BYTES/);
assert.match(page, /response\.setStatus\([^;]*413[^;]*\)/);
assert.match(page, /id="sky-summary"[^>]*><%=errorMessage/);
assert.match(page, /if \(errorMessage == null\).*skyview\.js/);
assert.ok(page.indexOf('request.getInputStream()') < page.indexOf('new JSONArray(fields.get("points"))'),
  'bound the POST before parsing the points JSON');
assert.match(page, /points\.length\(\)\s*>\s*4000/);
assert.match(page, /URLDecoder\.decode/);
console.log('SkyView JSP bounded POST contract passed');
