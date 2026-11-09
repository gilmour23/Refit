import fs from 'node:fs';
import vm from 'node:vm';
import * as fabrication from '../web/fabrication.mjs';
import * as geometry from '../web/geometry.mjs';
import * as workbench from '../web/workbench.mjs';

// Every referenced ID must exist in the actual shipped HTML.
export function operatorHarness({deferImages=false,record=exampleRecord}={}){
 const nodes=new Map(),decodes=[],requests=[],revoked=[],downloads=[];let blobId=0;
 function element(tag='div'){
  const classes=new Set();
  return {tag,hidden:false,disabled:false,open:false,value:'',checked:false,textContent:'',dataset:{},children:[],attributes:{},style:{setProperty(){}},clientWidth:700,clientHeight:600,
   classList:{toggle(k,on){if(on??!classes.has(k))classes.add(k);else classes.delete(k);},contains:k=>classes.has(k)},
   setAttribute(k,v){this.attributes[k]=v;},removeAttribute(k){delete this.attributes[k];if(k==='src')delete this.src;},append(...children){this.children.push(...children);},replaceChildren(...children){this.children=children;},
   querySelectorAll(selector){return this.children.flatMap(c=>[...(selector==='button'&&c.tag==='button'?[c]:[]),...(c.querySelectorAll?.(selector)||[])]);},querySelector(){return element('h2');},
   getBoundingClientRect(){return {left:0,top:0,width:400,height:800};},decode(){return deferImages?new Promise(resolve=>decodes.push(resolve)):Promise.resolve();},
   showModal(){this.open=true;},close(){this.open=false;},click(){downloads.push({href:this.href,download:this.download});},addEventListener(){}};
 }
 const html=fs.readFileSync(new URL('../web/operator.html',import.meta.url),'utf8');
 for(const m of html.matchAll(/<([\w-]+)\b([^>]*\bid="([^"]+)"[^>]*)>/g)){const e=element(m[1]);e.hidden=/\bhidden(?:\s|=|$)/.test(m[2]);nodes.set(m[3],e);}
 const files=[...html.matchAll(/<button\b[^>]*data-download="([^"]+)"[^>]*>/g)].map(m=>{const e=element('button');e.dataset.download=m[1];return e;});
 function node(id){if(!nodes.has(id))throw Error('Missing HTML element: '+id);return nodes.get(id);}
 node('photo-zoom').value='1';
 const ctx=vm.createContext({...fabrication,...geometry,...workbench,document:{getElementById:node,querySelectorAll:s=>s==='[data-download]'?files:[],createElement:element,createElementNS:(_,tag)=>element(tag)},
  window:{addEventListener(){},confirm:()=>true},location:{hash:''},history:{replaceState(){}},URL:{createObjectURL:()=>`blob:case-${++blobId}`,revokeObjectURL:url=>revoked.push(url)},AbortController,AbortSignal,setTimeout,clearTimeout,setInterval,clearInterval,
  readBoundedJson:async r=>r.json(),fetch:async(path,options={})=>{
   requests.push({path,options});const u=new URL(path,'http://test.local');
   return {ok:true,json:async()=>u.pathname==='/admin/intakes'?{count:0,items:[]}:record(u.pathname.split('/')[3],Number(u.searchParams.get('revision'))||2),blob:async()=>new Blob(['private-photo'],{type:'image/jpeg'})};
  }});
 const source=fs.readFileSync(new URL('../web/operator.js',import.meta.url),'utf8').split('\n').filter(l=>!l.startsWith('import ')).join('\n');vm.runInContext(source,ctx);vm.runInContext("key='test-only';",ctx);
 return {ctx,node,files,decodes,requests,revoked,downloads,run:code=>vm.runInContext(code,ctx)};
}
export function exampleRecord(id='a'.repeat(32),revision=2){return {id,revision,current_revision:2,display_name:id.startsWith('a')?'참가자 A':'참가자 B',hand:id.startsWith('a')?'left':'right',created_at:'2026-11-09T01:00:00Z',updated_at:'2026-11-09T01:00:00Z',status:'complete',review_status:'unreviewed',message:'완료',assets:['top_overlay','side_overlay','report','csv','top_original','side_original'],history:[{revision:2,source:'correction',status:'complete'},{revision:1,source:'upload',status:'complete'}],overrides:{},result:{geometry:{top:{width:400,height:800,lines:{'1':[[100,500],[250,500]]}},side:{width:400,height:800,lines:{'2':[[130,500],[210,500]]}}},dims_mm:Object.fromEntries(Array.from({length:11},(_,i)=>[String(i+1),20+i]))}};}
export const tick=()=>new Promise(resolve=>setImmediate(resolve));
