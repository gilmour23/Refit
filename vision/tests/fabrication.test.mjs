import test from 'node:test';
import assert from 'node:assert/strict';
import {sections,lengths,dimensionValue,hasCompleteDimensions,measurementFilename} from '../web/fabrication.mjs';
test('fabrication grouping uses every existing dimension once and preserves width/thickness pairs',()=>{
  assert.deepEqual(sections.map(x=>[x.width,x.thickness]),[[1,2],[3,4],[8,10],[9,11]]);
  assert.deepEqual([...sections.flatMap(x=>[x.width,x.thickness]),...lengths.map(x=>x.id)].sort((a,b)=>a-b),[1,2,3,4,5,6,7,8,9,10,11]);
});
test('incomplete or invalid measurements cannot be treated as a complete printable set',()=>{
  const record={status:'complete',result:{dims_mm:Object.fromEntries(Array.from({length:11},(_,i)=>[String(i+1),i+12]))}};
  assert.equal(hasCompleteDimensions(record),true);
  for(const value of [undefined,NaN,Infinity,0,-1,'13']){
    const changed=structuredClone(record);changed.result.dims_mm['9']=value;
    assert.equal(dimensionValue(changed.result,9),null);assert.equal(hasCompleteDimensions(changed),false);
  }
  assert.equal(hasCompleteDimensions({...record,status:'processing'}),false);
});
test('download filenames identify the person, hand and full record without unsafe path characters',()=>{
  const name=measurementFilename({display_name:'김/샘플:*',hand:'left',id:'a'.repeat(32)},'csv','csv');
  assert.match(name,/김_샘플___왼손_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa_치수\.csv$/);
  assert.doesNotMatch(name,/[<>:"/\\|?*\u0000-\u001f]/);
});
