import test from 'node:test';import assert from 'node:assert/strict';import {BleReader} from '../web/bluetooth.mjs';
const uuid='0000ffe0-0000-1000-8000-00805f9b34fb';
test('subscription is separate from samples and late packets after stop are ignored',async()=>{
 const characteristic=new EventTarget();characteristic.properties={notify:true};characteristic.startNotifications=async()=>characteristic;characteristic.stopNotifications=async()=>{};
 const device=new EventTarget();device.name='Test sensor';device.gatt={connected:false,connect:async()=>{device.gatt.connected=true;return {getPrimaryService:async()=>({getCharacteristic:async()=>characteristic})}},disconnect:()=>{device.gatt.connected=false;device.dispatchEvent(new Event('gattserverdisconnected'));}};
 const states=[],samples=[];let options;
 const r=new BleReader({bluetooth:{requestDevice:async o=>{options=o;return device}},onState:s=>states.push(s),onSamples:v=>samples.push(v)});
 await r.connect(uuid,uuid,true);
 assert.equal(states.at(-1).kind,'subscribed');assert.equal(samples.length,0);assert.deepEqual(options.optionalServices,[uuid]);
 characteristic.value=new DataView(Uint8Array.from([10,0]).buffer);characteristic.dispatchEvent(new Event('characteristicvaluechanged'));
 assert.deepEqual(samples,[[10]]);
 r.disconnect();characteristic.dispatchEvent(new Event('characteristicvaluechanged'));assert.equal(samples.length,1);assert.equal(states.at(-1).kind,'disconnected');
});
test('missing acknowledgement never opens a device chooser',async()=>{
 let calls=0;const r=new BleReader({bluetooth:{requestDevice:()=>{calls++}},onState:()=>{},onSamples:()=>{}});
 await assert.rejects(r.connect(uuid,uuid,false));assert.equal(calls,0);
});
