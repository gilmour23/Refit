import test from 'node:test';
import assert from 'node:assert/strict';
import {operatorHarness,exampleRecord,tick} from './operator-harness.mjs';

test('selecting a case immediately exposes all eleven dimensions next to the photographs',async()=>{
 const h=operatorHarness();await h.run("selectRecord('"+'a'.repeat(32)+"')");
 assert.equal(h.node('dimensions-panel').hidden,false);
 assert.equal(h.node('section-dimensions').children.length,4);
 assert.equal(h.node('length-dimensions').children.length,3);
 assert.equal(h.node('top-photo').hidden,false);
 assert.equal(h.node('correction-panel').hidden,true);
 assert.equal(h.node('record-status').textContent,'측정 완료');
});

test('correction tools open inline without hiding dimensions and only complete points can save',async()=>{
 const h=operatorHarness();await h.run("selectRecord('"+'a'.repeat(32)+"')");
 h.node('photo-tools').open=true;h.node('edit-card').onclick();
 assert.equal(h.node('photo-tools').open,false);
 assert.equal(h.node('correction-panel').hidden,false);
 assert.equal(h.node('dimensions-panel').hidden,false);
 for(const [x,y]of [[30,40],[180,40],[180,135]])h.node('top-geometry').onclick({clientX:x,clientY:y});
 assert.equal(h.node('apply-correction').disabled,true);
 assert.equal(h.files.find(b=>b.dataset.download==='csv').disabled,true);
 h.node('top-geometry').onclick({clientX:30,clientY:135});
 assert.equal(h.node('apply-correction').disabled,false);
 h.node('result-side-tab').onclick();
 assert.equal(h.node('dimensions-panel').hidden,false);
 assert.equal(h.node('edit-card').attributes['aria-pressed'],'false');
 assert.equal(h.run('draft.value.card_top.length'),4);
});

test('a previous revision stays read-only while photographs are still decoding',async()=>{
 const h=operatorHarness({deferImages:true});const loading=h.run("selectRecord('"+'a'.repeat(32)+"',1)");await tick();
 assert.equal(h.node('delete-record').disabled,true);
 assert.equal(h.node('reanalyze-record').disabled,true);
 h.decodes.forEach(resolve=>resolve());await loading;
});

test('saved-original reanalysis keeps overrides and protects previous or processing revisions',async()=>{
 const h=operatorHarness({record:(id,revision)=>({...exampleRecord(id,revision),status:'failed',overrides:{wrist_top:[12,24]}})});
 await h.run("selectRecord('"+'a'.repeat(32)+"')");
 assert.equal(h.node('reanalyze-record').disabled,false);
 h.node('reanalyze-record').onclick();await new Promise(resolve=>setImmediate(resolve));
 const posted=h.requests.find(x=>x.options.method==='POST');
 assert.ok(posted.path.endsWith('/recalculate'));
 assert.deepEqual(JSON.parse(posted.options.body),{revision:2,overrides:{wrist_top:[12,24]}});
 h.run('current.revision=1;updateControls();');assert.equal(h.node('reanalyze-record').disabled,true);
 h.run("current.revision=2;current.status='processing';updateControls();");assert.equal(h.node('reanalyze-record').disabled,true);
});
