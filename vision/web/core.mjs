export const names = ['팔 쪽 40mm 위치 · 폭','팔 쪽 40mm 위치 · 두께','손목 폭','손목 두께','새끼손가락 기준점–손목 세로 거리','엄지 기준점–손목 세로 거리','검지–엄지 기준점 세로 거리','엄지 기준점에서 새끼 쪽 테두리','검지 기준점에서 새끼 쪽 테두리','엄지 기준점 높이 두께','검지 기준점 높이 두께'];
export function validUuid(value) { return /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(value); }
export function parseRaw(view) {
  if (!view || view.byteLength % 2) throw Error('INT16_LE 패킷은 짝수 바이트여야 해요. 펌웨어 형식을 확인해 주세요.');
  const result=[]; for(let i=0;i<view.byteLength;i+=2) result.push(view.getInt16(i,true)); return result;
}
export function freshSignal(state, now=performance.now()) {
  if(!state.connected || state.lastAt==null || now-state.lastAt>2000 || !state.values.length) return null;
  const samples=state.values.slice(-100);
  return {rms:Math.sqrt(samples.reduce((a,v)=>a+v*v,0)/samples.length),latest:samples.at(-1),values:samples};
}
export function appendSamples(state,values,now=performance.now()) {
  if(state.lastAt==null||now-state.lastAt>2000)state.values=[];
  state.lastAt=now;state.values=state.values.concat(values).slice(-500);
}
export function parseMeasurement(j) {
  if(j?.ok!==true) throw Error(j?.message || j?.detail || '사진을 측정하지 못했어요. 촬영 조건을 확인해 주세요.');
  if(typeof j.session_id!=='string'||!j.session_id.trim()) throw Error('측정 번호가 없어요.');
  const dimensions=names.map((name,i)=>{const value=j.dims_mm?.[String(i+1)];if(typeof value!=='number'||!Number.isFinite(value)||value<=0)throw Error(`${i+1}번 치수를 측정하지 못했어요.`);return {id:i+1,name,value};});
  if(typeof j.report_jpg_base64!=='string'||!j.report_jpg_base64.startsWith('/9j/')||j.report_jpg_base64.length>12*1024*1024) throw Error('확인할 수 있는 측정 보고서가 없어요.');
  const warnings=Array.isArray(j.warnings)?j.warnings.filter(x=>typeof x==='string'):[];
  return {session:j.session_id,dimensions,warnings,report:j.report_jpg_base64};
}
export async function readBoundedJson(response, max=12*1024*1024) {
  const declared=Number(response.headers.get('content-length')); if(declared>max)throw Error('서버 응답이 너무 커요.');
  let text='';
  if(response.body?.getReader){const reader=response.body.getReader();const decoder=new TextDecoder();let bytes=0;
    try {while(true){const {done,value}=await reader.read();if(done)break;bytes+=value.byteLength;if(bytes>max)throw Error('서버 응답이 너무 커요.');text+=decoder.decode(value,{stream:true});}text+=decoder.decode();}finally{await reader.cancel().catch(()=>{});}
  } else {text=await response.text();if(new TextEncoder().encode(text).byteLength>max)throw Error('서버 응답이 너무 커요.');}
  try{return JSON.parse(text);}catch{throw Error('서버 응답을 읽을 수 없어요.');}
}
