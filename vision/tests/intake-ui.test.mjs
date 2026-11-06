import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import test from 'node:test';
test('a prior submission response cannot overwrite the next receipt', async()=>{
let resolveOld;
const nodes = new Map();
function node(id) {
  if (!nodes.has(id)) nodes.set(id, {
    id, textContent: '', hidden: false, disabled: false, elements: [],
    classList: { toggle() {} }, reset() {}, scrollIntoView() {},
  });
  return nodes.get(id);
}
const ctx = vm.createContext({
  document: { getElementById: node }, window: { addEventListener() {} },
  URL, console, setTimeout, clearTimeout, AbortSignal, AbortController, FormData,
  readBoundedJson: async r => r.json(), fetch: () => new Promise(r => { resolveOld = r; }),
});
const source = fs.readFileSync(new URL('../web/intake.js',import.meta.url), 'utf8')
  .split('\n').filter(l => !l.startsWith('import ') && !l.startsWith('const joined=')).join('\n');
vm.runInContext(source, ctx);
vm.runInContext("receipt={id:'old-record',receipt:'old-token'};globalThis.oldPoll=checkStatus();", ctx);
node('new-submission').onclick();
vm.runInContext("receipt={id:'new-record',receipt:'new-token'};document.getElementById('receipt-number').textContent='new-record';document.getElementById('receipt-title').textContent='사진이 접수됐어요';", ctx);
resolveOld({ ok: true, json: async () => ({ id: 'old-record', status: 'complete', message: 'OLD measurement complete' }) });
await ctx.oldPoll;
assert.equal(node('receipt-number').textContent, 'new-record');
assert.equal(node('receipt-title').textContent, '사진이 접수됐어요', 'Stale response must not overwrite new receipt title');
assert.notEqual(node('receipt-message').textContent, 'OLD measurement complete');
});
