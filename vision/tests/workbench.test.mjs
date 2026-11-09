import test from 'node:test';
import assert from 'node:assert/strict';
import {fitPhotoSize} from '../web/workbench.mjs';
test('fit shows a complete portrait photograph without chopping off wrist or card',()=>{
 assert.deepEqual(fitPhotoSize(720,960,640,520,1),{width:390,height:520});
 assert.deepEqual(fitPhotoSize(720,960,640,520,2),{width:780,height:1040});
});
test('landscape images fit by width and invalid extents do not create geometry',()=>{
 assert.deepEqual(fitPhotoSize(1600,900,640,520,1),{width:640,height:360});
 assert.equal(fitPhotoSize(0,900,640,520,1),null);
});
