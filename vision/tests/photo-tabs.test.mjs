import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
function screen(file){
 const nodes=new Map();
 function node(id){if(!nodes.has(id))nodes.set(id,{id,hidden:false,disabled:false,textContent:'',elements:[],attributes:{},classList:{toggle(){}},setAttribute(k,v){this.attributes[k]=v;},getAttribute(k){return this.attributes[k];}});return nodes.get(id);}
 const context=vm.createContext({document:{getElementById:node,querySelectorAll:()=>[]},window:{addEventListener(){}},location:{hash:''},history:{replaceState(){}},URL,AbortController,AbortSignal,FormData,setTimeout,clearTimeout,setInterval,clearInterval});
 const source=fs.readFileSync(new URL('../web/'+file,import.meta.url),'utf8').split('\n').filter(line=>!line.startsWith('import ')&&!line.startsWith('const joined=')).join('\n');
 vm.runInContext(source,context);return {node,context};
}
test('participant photo tabs switch instruction and original preview together',()=>{
 const {node}=screen('intake.js');
 assert.equal(typeof node('side-tab').onclick,'function');node('side-tab').onclick();
 assert.equal(node('top-photo-panel').hidden,true);assert.equal(node('side-photo-panel').hidden,false);
 assert.equal(node('side-tab').getAttribute('aria-selected'),'true');
 node('top-tab').onclick();assert.equal(node('top-photo-panel').hidden,false);assert.equal(node('side-photo-panel').hidden,true);
});
test('operator photo tab switches only the displayed view, keeping both record images available',()=>{
 const {node}=screen('operator.js');node('top-photo').src='top-private-blob';node('side-photo').src='side-private-blob';
 assert.equal(typeof node('result-side-tab').onclick,'function');node('result-side-tab').onclick();
 assert.equal(node('result-top-panel').hidden,true);assert.equal(node('result-side-panel').hidden,false);
 assert.equal(node('top-photo').src,'top-private-blob');assert.equal(node('side-photo').src,'side-private-blob');
 node('result-top-tab').onclick();assert.equal(node('result-top-panel').hidden,false);assert.equal(node('result-side-tab').getAttribute('aria-selected'),'false');
});
