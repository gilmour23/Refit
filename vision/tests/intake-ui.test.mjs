import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import test from 'node:test';
import * as receipts from '../web/receipt.mjs';
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
const ctx = vm.createContext({...receipts,
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

test('completed receipts do not wait for the removed approval step and retain retake requests',async()=>{
 const nodes=new Map();const node=id=>{if(!nodes.has(id))nodes.set(id,{textContent:'',hidden:false,classList:{toggle(){}}});return nodes.get(id);};
 let status={status:'complete',review_status:'unreviewed',revision:2};
 const ctx=vm.createContext({...receipts,document:{getElementById:node},window:{addEventListener(){}},URL,AbortSignal,AbortController,FormData,setTimeout:()=>1,clearTimeout(){},
  readBoundedJson:async r=>r.json(),fetch:async()=>({ok:true,json:async()=>status})});
 const source=fs.readFileSync(new URL('../web/intake.js',import.meta.url),'utf8').split('\n').filter(l=>!l.startsWith('import ')&&!l.startsWith('const joined=')).join('\n');vm.runInContext(source,ctx);
 vm.runInContext("receipt={id:'receipt-example',receipt:'receipt-token'};",ctx);
 for(const legacy of ['unreviewed','reviewed']){
  status={status:'complete',review_status:legacy,revision:2};await vm.runInContext('checkStatus()',ctx);
  assert.equal(node('receipt-title').textContent,'치수 계산이 끝났어요');
  assert.equal(node('receipt-message').textContent,'사진과 치수가 운영자 화면에 정리됐어요.');
 }
 status={status:'complete',review_status:'retake_requested',revision:2,retake_views:['side'],retake_reason:'측면 사진의 손목 아래를 더 담아주세요.'};await vm.runInContext('checkStatus()',ctx);
 assert.equal(node('retake-form').hidden,false);
 assert.equal(node('retake-top-panel').hidden,true);
 assert.equal(node('retake-side-panel').hidden,false);
 assert.equal(node('receipt-message').textContent,'재촬영 요청: 측면 사진의 손목 아래를 더 담아주세요.');
});
