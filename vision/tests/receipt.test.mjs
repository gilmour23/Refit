import test from 'node:test';
import assert from 'node:assert/strict';
import {saveSession,restoreSession,forgetSession,requestedViews} from '../web/receipt.mjs';
function store(){const data=new Map();return {setItem:(k,v)=>data.set(k,v),getItem:k=>data.get(k)||null,removeItem:k=>data.delete(k)};}
test('reload restores a valid tab receipt and forgetting removes it',()=>{
 const storage=store(),receipt={id:'a'.repeat(32),receipt:'S'.repeat(43)};
 saveSession(storage,'K'.repeat(48),receipt,1000);assert.deepEqual(restoreSession(storage,2000).receipt,receipt);
 forgetSession(storage);assert.equal(restoreSession(storage,2000),null);
});
test('invalid or expired receipt cannot restore private status',()=>{
 const storage=store();saveSession(storage,'K'.repeat(48),{id:'../bad',receipt:'secret'},1000);assert.equal(restoreSession(storage,2000),null);
 saveSession(storage,'K'.repeat(48),{id:'a'.repeat(32),receipt:'S'.repeat(43)},1000);assert.equal(restoreSession(storage,1000+13*60*60*1000),null);
});
test('partial retake requires only the requested views',()=>{
 assert.deepEqual(requestedViews({review_status:'retake_requested',retake_views:['side'],revision:2}),['side']);
 assert.deepEqual(requestedViews({review_status:'reviewed',retake_views:['side']}),[]);
});
