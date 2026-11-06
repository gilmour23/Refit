import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import * as fabrication from '../web/fabrication.mjs';
const tick=()=>new Promise(resolve=>setImmediate(resolve));
test('switching people cannot print stale photographs and locking removes print access',async()=>{
 const nodes=new Map(),decodes=[];
 function element(tag='div'){
  return {tag,hidden:false,disabled:false,value:'',textContent:'',dataset:{},children:[],elements:[],style:{setProperty(){}},classList:{toggle(){}},setAttribute(){},removeAttribute(){},append(...children){this.children.push(...children);},replaceChildren(...children){this.children=children;},querySelectorAll(){return [];},querySelector(){return element('h2');},decode(){return new Promise(resolve=>decodes.push(resolve));}};
 }
 function node(id){if(!nodes.has(id))nodes.set(id,element());return nodes.get(id);}
 const a='a'.repeat(32),b='b'.repeat(32),record=id=>({id,display_name:id===a?'제작자 A':'제작자 B',hand:id===a?'left':'right',created_at:new Date().toISOString(),status:'complete',message:'측정 자료',assets:['top_overlay','side_overlay'],result:{dims_mm:Object.fromEntries(Array.from({length:11},(_,i)=>[String(i+1),20+i]))}});
 let printed=0;
 const ctx=vm.createContext({...fabrication,document:{getElementById:node,querySelectorAll:()=>[],createElement:element},window:{addEventListener(){},print(){printed++;}},location:{hash:''},history:{replaceState(){}},URL,AbortController,AbortSignal,setTimeout,clearTimeout,setInterval,clearInterval,
  readBoundedJson:async r=>r.json(),fetch:async path=>({ok:true,json:async()=>record(path.split('/')[3]),blob:async()=>new Blob(['image'],{type:'image/jpeg'})})});
 const source=fs.readFileSync(new URL('../web/operator.js',import.meta.url),'utf8').split('\n').filter(l=>!l.startsWith('import ')).join('\n');vm.runInContext(source,ctx);
 vm.runInContext(`key='test-only';globalThis.first=selectRecord('${a}');`,ctx);await tick();assert.equal(decodes.length,2);
 vm.runInContext(`globalThis.second=selectRecord('${b}');`,ctx);await tick();assert.equal(decodes.length,4);
 decodes[0]();decodes[1]();await ctx.first;
 assert.equal(node('record-name').textContent,'제작자 B');assert.equal(node('record-hand').textContent,'오른손');assert.equal(node('print-sheet').disabled,true);
 decodes[2]();await tick();assert.equal(node('print-sheet').disabled,true);
 decodes[3]();await ctx.second;assert.equal(node('print-sheet').disabled,false);
 node('print-sheet').onclick();assert.equal(printed,1);
 node('logout').onclick();assert.equal(node('print-sheet').disabled,true);node('print-sheet').onclick();assert.equal(printed,1);
});
