import {parseRaw,validUuid} from './core.mjs';
export class BleReader {
 constructor({bluetooth=globalThis.navigator?.bluetooth,onState=()=>{},onSamples=()=>{}}={}){this.bluetooth=bluetooth;this.onState=onState;this.onSamples=onSamples;this.generation=0;}
 async connect(service,notify,confirmed){
  if(!validUuid(service)||!validUuid(notify))throw Error('펌웨어 팀의 서비스 UUID와 알림 UUID를 입력해 주세요.');
  if(!confirmed)throw Error('실제 전송 형식이 signed INT16_LE인지 확인해 주세요.');
  if(!this.bluetooth)throw Error('이 브라우저는 BLE를 지원하지 않아요. 갤럭시는 Chrome, 아이폰·아이패드는 Bluefy에서 열어주세요.');
  this.disconnect();const generation=this.generation;this.onState({kind:'selecting',label:'센서를 선택해 주세요'});
  let device;
  try{
   device=await this.bluetooth.requestDevice({acceptAllDevices:true,optionalServices:[service.toLowerCase()]});
   if(generation!==this.generation)return;
   this.device=device;this.lost=()=>{if(generation===this.generation)this.disconnect();};device.addEventListener('gattserverdisconnected',this.lost);
   this.onState({kind:'connecting',label:'센서 연결 중…'});
   const server=await device.gatt.connect();
   if(generation!==this.generation){device.gatt.disconnect();return;}
   const channel=await (await server.getPrimaryService(service.toLowerCase())).getCharacteristic(notify.toLowerCase());
   if(generation!==this.generation)return;
   if(!channel.properties.notify&&!channel.properties.indicate)throw Error('이 특성은 알림을 지원하지 않아요. UUID를 확인해 주세요.');
   this.channel=channel;
   this.packet=event=>{if(generation!==this.generation)return;try{const values=parseRaw(event.target.value);if(values.length)this.onSamples(values);}catch(e){this.disconnect();this.onState({kind:'error',label:e.message});}};
   channel.addEventListener('characteristicvaluechanged',this.packet);
   await channel.startNotifications();
   if(generation!==this.generation)return;
   this.onState({kind:'subscribed',label:`${device.name||'선택한 센서'} 연결됨 · 데이터 대기`});
  }catch(e){if(generation===this.generation){this.disconnect();this.onState({kind:'error',label:e.name==='NotFoundError'?'기기 선택을 취소했거나 센서를 찾지 못했어요.':e.message||'센서에 연결하지 못했어요.'});}throw e;}
 }
 disconnect(){
  this.generation++;
  const channel=this.channel;this.channel=null;
  if(channel&&this.packet)channel.removeEventListener('characteristicvaluechanged',this.packet);
  if(channel)Promise.resolve().then(()=>channel.stopNotifications()).catch(()=>{});
  const device=this.device;this.device=null;
  if(device&&this.lost)device.removeEventListener('gattserverdisconnected',this.lost);
  if(device?.gatt?.connected)device.gatt.disconnect();
  this.onState({kind:'disconnected',label:'센서 연결 안 됨'});
 }
}
