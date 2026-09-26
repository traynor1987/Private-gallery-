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
test('playing video shows save beside fullscreen only after native media classification', () => {
  const listeners = {}, documentListeners = {}, elements = [], prompts = [];
  class HTMLVideoElement {}
  const video = {isConnected: true, paused: false, ended: false, getBoundingClientRect() {
    return {width: 360, height: 220, right: 360, top: 30, bottom: 250};
  }, requestFullscreen() {}};
  const document = {hidden: false, readyState: 'loading', fullscreenElement: null,
    addEventListener(name, fn) {documentListeners[name] = fn;},
    createElement() {return {style: {}, setAttribute() {}, addEventListener(name, fn) {this[name] = fn;}, remove() {}};},
    documentElement: {appendChild(element) {elements.push(element);}},
    querySelectorAll() {return [video];}};
  vm.runInNewContext(fs.readFileSync('app/src/main/assets/browser-video-assistant.js', 'utf8'), {
    window: {addEventListener(name, fn) {listeners[name] = fn;}, frames: [], prompt(value) {prompts.push(value);}},
    document, HTMLVideoElement, innerWidth: 400, innerHeight: 800, console: {info() {}}, clearTimeout() {}, setTimeout() {return 1;},
  });
  documentListeners.playing({target: Object.setPrototypeOf(video, HTMLVideoElement.prototype)});
  assert.equal(elements.length, 2);
  assert.equal(elements[1].hidden, true);
  listeners.message({data: {type: 'private-gallery-video-save-available', available: true}});
  assert.equal(elements[1].hidden, false);
  elements[1].click({preventDefault() {}, stopPropagation() {}});
  assert.deepEqual(prompts, ['private-gallery-save-video']);
  listeners.message({data: {type: 'private-gallery-video-save-available', available: false}});
  assert.equal(elements[1].hidden, true);
});
