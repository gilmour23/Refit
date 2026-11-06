import {readBoundedJson} from './core.mjs';
const $=id=>document.getElementById(id);
let key='',ready=false,busy=false,receipt=null,pollTimer=null,request=null;
const photos={top:null,side:null},previews={top:null,side:null};
function say(id,text,error=false){$(id).textContent=text;$(id).classList.toggle('error',error);}
for(const view of ['top','side'])$(view+'-tab').onclick=()=>{for(const which of ['top','side']){$(which+'-photo-panel').hidden=which!==view;$(which+'-tab').setAttribute('aria-selected',String(which===view));}};
function sync(){for(const which of ['top','side'])$(which+'-ready').textContent=photos[which]?'선택됨':which==='top'?'1 / 2':'2 / 2';$('photo-count').textContent=photos.top&&photos.side?'사진 2장이 준비됐어요.':photos.top||photos.side?'나머지 사진 1장을 선택해 주세요.':'사진 2장을 선택해 주세요.';const accepted=!!receipt;for(const field of $('intake-form').elements)field.disabled=busy||accepted;$('submit-intake').disabled=busy||accepted||!ready||!photos.top||!photos.side;}
async function checkKey(candidate){
 ready=false;key='';sync();say('connection-status','측정 서버 연결 확인 중…');
 try{if(!/^[A-Za-z0-9_-]{32,256}$/.test(candidate))throw Error('운영자가 제공한 QR로 접속하거나 연결키를 입력해 주세요.');
 const r=await fetch('/demo/check',{method:'POST',headers:{Authorization:`Bearer ${candidate}`},cache:'no-store',redirect:'error',signal:AbortSignal.timeout(15000)});const j=await readBoundedJson(r,8192);if(!r.ok)throw Error(j.detail||'서버 연결키를 확인해 주세요.');if(!j.model_ready)throw Error('측정 서버 준비가 필요해요. 운영자에게 알려 주세요.');key=candidate;ready=true;$('upload-key').value='';$('key-settings').open=false;say('connection-status','측정 서버에 연결됐어요.');
 }catch(e){$('key-settings').open=true;say('connection-status',e.message||'연결하지 못했어요.',true);}finally{sync();}
}
$('check-key').onclick=()=>checkKey($('upload-key').value.trim());
for(const which of ['top','side'])for(const kind of ['camera','file'])$(which+'-'+kind).onchange=event=>{
 const file=event.target.files[0];if(!file)return;
 if(file.size===0||file.size>20*1024*1024){say('upload-message','사진 한 장은 20MB 이하여야 해요.',true);event.target.value='';return;}
 photos[which]=file;if(previews[which])URL.revokeObjectURL(previews[which]);previews[which]=URL.createObjectURL(file);
 const img=$(which+'-preview');img.onload=()=>{$(which+'-placeholder').hidden=true;img.hidden=false;};img.onerror=()=>{img.hidden=true;$(which+'-placeholder').hidden=false;$(which+'-name').textContent=file.name+' · 미리보기 없이 원본 전송';};img.src=previews[which];$(which+'-name').textContent=file.name;say('upload-message','');sync();
};
async function checkStatus(){
 clearTimeout(pollTimer);if(!receipt)return;const target=receipt;$('check-status').disabled=true;
 try{const r=await fetch('/intake/status',{method:'POST',headers:{Authorization:`Bearer ${key}`,'Content-Type':'application/json'},body:JSON.stringify(receipt),cache:'no-store',redirect:'error',signal:AbortSignal.timeout(15000)});const j=await readBoundedJson(r,8192);if(target!==receipt)return;if(!r.ok)throw Error(j.detail||'접수 상태를 확인하지 못했어요.');
 $('receipt-title').textContent=({queued:'사진이 접수됐어요',processing:'사진을 분석하고 있어요',complete:'측정이 완료됐어요',failed:'사진을 다시 확인해 주세요'})[j.status]||'접수 확인';say('receipt-message',j.message,j.status==='failed');
 if(['queued','processing'].includes(j.status))pollTimer=setTimeout(checkStatus,2500);
 }catch(e){if(target!==receipt)return;say('receipt-message','접수는 유지돼요. '+(e.message||'잠시 뒤 상태 확인을 눌러 주세요.'),true);}finally{if(target===receipt)$('check-status').disabled=false;}
}
$('check-status').onclick=checkStatus;
$('intake-form').onsubmit=async event=>{
 event.preventDefault();if(busy||receipt||!ready||!$('intake-form').reportValidity()||!photos.top||!photos.side)return;
 busy=true;request=new AbortController();const timeout=setTimeout(()=>request.abort(),120000);const form=new FormData();form.append('display_name',$('display-name').value.trim());form.append('hand',new FormData($('intake-form')).get('hand'));form.append('consent','true');for(const which of ['top','side'])form.append(which+'_image',photos[which],which+'-original');sync();say('upload-message','사진 두 장을 전송하고 있어요. 접수 완료가 나올 때까지 창을 닫지 마세요.');
 try{const r=await fetch('/intake',{method:'POST',headers:{Authorization:`Bearer ${key}`},body:form,signal:request.signal,cache:'no-store',redirect:'error'});const j=await readBoundedJson(r,8192);if(!r.ok)throw Error(typeof j.detail==='string'?j.detail:'입력 정보와 사진을 확인해 주세요.');if(!/^[a-f0-9]{32}$/.test(j.id)||typeof j.receipt!=='string')throw Error('접수 응답을 확인하지 못했어요. 운영자에게 접수 여부를 먼저 확인해 주세요.');receipt={id:j.id,receipt:j.receipt};$('receipt-number').textContent=j.id;$('receipt').hidden=false;say('upload-message','접수됐어요. 분석하는 동안 다른 참가자도 사진을 제출할 수 있어요.');$('receipt').scrollIntoView({behavior:'smooth',block:'start'});checkStatus();
 }catch(e){say('upload-message',e.name==='AbortError'?'전송 확인 시간이 지났어요. 운영자에게 접수 여부를 확인한 뒤 다시 제출해 주세요.':e.message||'전송 확인이 끊겼어요. 운영자에게 접수 여부를 확인한 뒤 다시 제출해 주세요.',true);}finally{clearTimeout(timeout);busy=false;request=null;sync();}
};
$('new-submission').onclick=()=>{clearTimeout(pollTimer);receipt=null;$('check-status').disabled=false;$('receipt').hidden=true;$('intake-form').reset();for(const which of ['top','side']){photos[which]=null;if(previews[which])URL.revokeObjectURL(previews[which]);previews[which]=null;$(which+'-preview').hidden=true;$(which+'-placeholder').hidden=false;$(which+'-name').textContent='선택한 사진 없음';}say('upload-message','새 접수번호로 저장돼요. 이전 기록 삭제는 운영자에게 요청해 주세요.');sync();$('intake-form').scrollIntoView({behavior:'smooth'});};
const joined=new URLSearchParams(location.hash.slice(1)).get('key');if(location.hash)history.replaceState(null,'',location.pathname);checkKey(joined||'');
window.addEventListener('pagehide',()=>{clearTimeout(pollTimer);receipt=null;request?.abort();for(const url of Object.values(previews))if(url)URL.revokeObjectURL(url);});
