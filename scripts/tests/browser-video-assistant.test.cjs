const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
test('document visibility change does not pause WebView-owned video; native lifecycle owns background policy', () => {
  const listeners = {};
  let pauses = 0;
  const video = {pause() { pauses++; }};
  const document = {hidden: false, readyState: 'loading', addEventListener(name, fn) {listeners[name] = fn;}, querySelectorAll() {return [video];}};
  vm.runInNewContext(fs.readFileSync('app/src/main/assets/browser-video-assistant.js', 'utf8'), {
    window: {addEventListener() {}}, document, clearTimeout() {}, setTimeout() {},
  });
  document.hidden = true;
  listeners.visibilitychange();
  assert.equal(pauses, 0, 'fullscreen visibility transitions must not pause the playing media');
});
test('native tab/background pause signal pauses embedded media without reading its source', () => {
  const listeners = {};
  let pauses = 0, forwarded = 0;
  const video = {pause() { pauses++; }};
  const document = {hidden: false, readyState: 'loading', addEventListener() {}, querySelectorAll() {return [video];}};
  vm.runInNewContext(fs.readFileSync('app/src/main/assets/browser-video-assistant.js', 'utf8'), {
    window: {addEventListener(name, fn) {listeners[name] = fn;}, frames: [{postMessage(message, origin) {
      assert.equal(message.type, 'private-gallery-pause-media'); assert.equal(origin, '*'); forwarded++;
    }}]}, document, clearTimeout() {}, setTimeout() {},
  });
  listeners.message({data: {type: 'unrelated'}});
  assert.equal(pauses, 0);
  listeners.message({data: {type: 'private-gallery-pause-media'}});
  assert.equal(pauses, 1);
  assert.equal(forwarded, 1);
});
