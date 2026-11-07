import test from 'node:test';
import assert from 'node:assert/strict';
import {fitPhotoSize,reviewStage} from '../web/workbench.mjs';
test('fit shows a complete portrait photograph without chopping off wrist or card',()=>{
 assert.deepEqual(fitPhotoSize(720,960,640,520,1),{width:390,height:520});
 assert.deepEqual(fitPhotoSize(720,960,640,520,2),{width:780,height:1040});
});
test('landscape images fit by width and invalid extents do not create geometry',()=>{
 assert.deepEqual(fitPhotoSize(1600,900,640,520,1),{width:640,height:360});
 assert.equal(fitPhotoSize(0,900,640,520,1),null);
});
test('task stages state the next action without marking an unchecked result approved',()=>{
 assert.deepEqual(reviewStage('photo'),{index:0,title:'사진과 기준점 확인',next:'dimensions',action:'치수 대조로 이동'});
 assert.equal(reviewStage('dimensions').next,'review');
 assert.equal(reviewStage('review').next,null);
 assert.equal(reviewStage('unknown').index,0);
});
