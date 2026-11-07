import test from 'node:test';
import assert from 'node:assert/strict';
import {imagePoint,CorrectionDraft,recordIdentity} from '../web/geometry.mjs';
test('zoomed pointer coordinates use image extent rather than screen pixels',()=>{
 assert.deepEqual(imagePoint({clientX:250,clientY:170},{left:50,top:20,width:800,height:600},{width:4000,height:3000}),[1000,750]);
 assert.equal(imagePoint({clientX:10,clientY:30},{left:50,top:20,width:800,height:600},{width:4000,height:3000}),null);
});
test('card edits require four points; undo and cancel preserve earlier wrist override',()=>{
 const d=new CorrectionDraft({wrist_top:[10,20]});d.start('side','card');
 for(const p of [[1,1],[20,1],[20,15]])d.add(p);
 assert.equal(d.canApply,false);d.add([1,15]);assert.equal(d.canApply,true);
 assert.deepEqual(d.value.wrist_top,[10,20]);assert.equal(d.value.card_side.length,4);
 d.undo();assert.equal(d.canApply,false);d.cancel();assert.deepEqual(d.value,{wrist_top:[10,20]});assert.equal(d.dirty,false);
});
test('record identity distinguishes versions of the same person',()=>{
 assert.notEqual(recordIdentity({id:'same',revision:1}),recordIdentity({id:'same',revision:2}));
});
