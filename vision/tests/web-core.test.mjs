import test from 'node:test';
import assert from 'node:assert/strict';
import { parseRaw, parseMeasurement, freshSignal, validUuid } from '../web/core.mjs';
test('signed little endian packets use exact raw units and reject partial samples',()=>{
  assert.deepEqual(parseRaw(new DataView(Uint8Array.from([0,128,255,127,255,255]).buffer)),[-32768,32767,-1]);
  assert.throws(()=>parseRaw(new DataView(new ArrayBuffer(3))));
});
test('measurement requires all eleven dimensions and an inline jpeg report',()=>{
  const result={ok:true,session_id:'B1',dims_mm:Object.fromEntries(Array.from({length:11},(_,i)=>[i+1,i+10])),report_jpg_base64:'/9j/2Q=='};
  assert.equal(parseMeasurement(result).dimensions.length,11);
  assert.throws(()=>parseMeasurement({...result,report_jpg_base64:null}));
  assert.throws(()=>parseMeasurement({...result,dims_mm:{...result.dims_mm,4:0}}));
  assert.throws(()=>parseMeasurement({...result,ok:false}));
});
test('zero is valid live data; disconnected and stale data never remain live',()=>{
  assert.equal(freshSignal({connected:true,lastAt:100,values:[0]},1000).rms,0);
  assert.equal(freshSignal({connected:true,lastAt:100,values:[12]},2101),null);
  assert.equal(freshSignal({connected:false,lastAt:100,values:[12]},1000),null);
  assert.equal(freshSignal({connected:true,lastAt:null,values:[]},1000),null);
  assert.equal(validUuid('0000ffe0-0000-1000-8000-00805f9b34fb'),true);
  assert.equal(validUuid('guess'),false);
});
test('resuming after a stale gap never revives old activity',async()=>{
 const {appendSamples}=await import('../web/core.mjs');
 const state={connected:true,lastAt:100,values:Array(100).fill(1000)};appendSamples(state,[0],3100);
 assert.deepEqual(state.values,[0]);assert.equal(freshSignal(state,3100).rms,0);
});
