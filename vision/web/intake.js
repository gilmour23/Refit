import {readBoundedJson} from './core.mjs';
import {saveSession,restoreSession,forgetSession,requestedViews} from './receipt.mjs';
const $=id=>document.getElementById(id);
let key='',ready=false,busy=false,receipt=null,pollTimer=null,request=null,retakeState=null,statusEpoch=0;
const tabStorage=typeof sessionStorage==='undefined'?null:sessionStorage;
const retakePhotos={top:null,side:null},retakePreviews={top:null,side:null};
const photos={top:null,side:null},previews={top:null,side:null};
function say(id,text,error=false){$(id).textContent=text;$(id).classList.toggle('error',error);}
function showView(view){for(const which of ['top','side']){$(which+'-photo-panel').hidden=which!==view;$(which+'-tab').setAttribute('aria-selected',String(which===view));}}
for(const view of ['top','side'])$(view+'-tab').onclick=()=>showView(view);
$('next-photo').onclick=()=>{showView('side');$('side-tab').focus();};
function sync(){for(const which of ['top','side'])$(which+'-ready').textContent=photos[which]?'선택됨':which==='top'?'1 / 2':'2 / 2';$('next-photo').hidden=!photos.top||!!photos.side;$('photo-count').textContent=photos.top&&photos.side?'사진 2장이 준비됐어요. 이름·측정할 손·보관 동의를 확인한 뒤 제출해 주세요.':photos.top?'측면 사진 1장을 더 선택해 주세요.':photos.side?'손등 사진 1장을 더 선택해 주세요.':'손등·측면 사진을 한 장씩 선택해 주세요.';const accepted=!!receipt;for(const field of $('intake-form').elements)field.disabled=busy||accepted;$('submit-intake').disabled=busy||accepted||!ready||!photos.top||!photos.side;$('submission-preview').hidden=!photos.top||!photos.side;}
async function checkKey(candidate){
 ready=false;key='';sync();say('connection-status','사진 접수 가능 여부를 확인하고 있어요.');
 try{if(!/^[A-Za-z0-9_-]{32,256}$/.test(candidate))throw Error('운영자가 제공한 QR로 접속하거나 연결키를 입력해 주세요.');
 const r=await fetch('/demo/check',{method:'POST',headers:{Authorization:`Bearer ${candidate}`},cache:'no-store',redirect:'error',signal:AbortSignal.timeout(15000)});const j=await readBoundedJson(r,8192);if(!r.ok)throw Error(j.detail||'서버 연결키를 확인해 주세요.');if(!j.model_ready)throw Error('측정 서버 준비가 필요해요. 운영자에게 알려 주세요.');key=candidate;ready=true;saveSession(tabStorage,key,receipt);$('upload-key').value='';$('key-settings').open=false;say('connection-status','손등·측면 사진을 차례로 제출해 주세요.');
 }catch(e){$('key-settings').open=true;say('connection-status',e.message||'연결하지 못했어요.',true);}finally{sync();}
}
$('check-key').onclick=()=>checkKey($('upload-key').value.trim());
for(const which of ['top','side'])for(const kind of ['camera','file'])$(which+'-'+kind).onchange=event=>{
 const file=event.target.files[0];if(!file)return;
 if(file.size===0||file.size>20*1024*1024){say('upload-message','사진 한 장은 20MB 이하여야 해요.',true);event.target.value='';return;}
 photos[which]=file;if(previews[which])URL.revokeObjectURL(previews[which]);previews[which]=URL.createObjectURL(file);
 $("confirm-"+which).src=previews[which];
 const img=$(which+'-preview');img.onload=()=>{$(which+'-placeholder').hidden=true;img.hidden=false;};img.onerror=()=>{img.hidden=true;$(which+'-placeholder').hidden=false;$(which+'-name').textContent=file.name+' · 미리보기 없이 원본 전송';};img.src=previews[which];$(which+'-name').textContent='내 사진 · '+file.name;say('upload-message','');sync();
};
async function checkStatus(){
 clearTimeout(pollTimer);if(!receipt)return;const target=receipt,generation=++statusEpoch;$('check-status').disabled=true;
 try{const r=await fetch('/intake/status',{method:'POST',headers:{Authorization:`Bearer ${key}`,'Content-Type':'application/json'},body:JSON.stringify(receipt),cache:'no-store',redirect:'error',signal:AbortSignal.timeout(15000)});const j=await readBoundedJson(r,8192);if(target!==receipt||generation!==statusEpoch)return;if(!r.ok)throw Error(j.detail||'접수 상태를 확인하지 못했어요.');
 $('receipt-title').textContent=({queued:'사진이 접수됐어요',processing:'사진을 분석하고 있어요',complete:'치수 계산이 끝났어요',failed:'사진을 다시 확인해 주세요'})[j.status]||'접수 확인';say('receipt-message',j.review_status==='retake_requested'?'재촬영 요청: '+j.retake_reason:j.status==='complete'?'사진과 치수가 운영자 화면에 정리됐어요.':j.message,j.status==='failed');retakeState=j;showRetake();
 pollTimer=setTimeout(checkStatus,['queued','processing'].includes(j.status)?2500:10000);
 }catch(e){if(target!==receipt||generation!==statusEpoch)return;say('receipt-message','접수는 유지돼요. '+(e.message||'잠시 뒤 상태 확인을 눌러 주세요.'),true);}finally{if(target===receipt&&generation===statusEpoch)$('check-status').disabled=false;}
}
$('check-status').onclick=checkStatus;
$('intake-form').onsubmit=async event=>{
 event.preventDefault();if(busy||receipt||!ready||!$('intake-form').reportValidity()||!photos.top||!photos.side)return;
 const selectedHand=new FormData($('intake-form')).get('hand');if(!window.confirm(`이름: ${$('display-name').value.trim()} · ${selectedHand==='left'?'왼손':'오른손'}\n손등과 측면 사진 두 장을 제출할까요?`))return;
 busy=true;request=new AbortController();const timeout=setTimeout(()=>request.abort(),120000);const form=new FormData();form.append('display_name',$('display-name').value.trim());form.append('hand',new FormData($('intake-form')).get('hand'));form.append('consent','true');for(const which of ['top','side'])form.append(which+'_image',photos[which],which+'-original');sync();say('upload-message','사진 두 장을 전송하고 있어요. 접수 완료가 나올 때까지 창을 닫지 마세요.');
 try{const r=await fetch('/intake',{method:'POST',headers:{Authorization:`Bearer ${key}`},body:form,signal:request.signal,cache:'no-store',redirect:'error'});const j=await readBoundedJson(r,8192);if(!r.ok)throw Error(typeof j.detail==='string'?j.detail:'입력 정보와 사진을 확인해 주세요.');if(!/^[a-f0-9]{32}$/.test(j.id)||typeof j.receipt!=='string')throw Error('접수 응답을 확인하지 못했어요. 운영자에게 접수 여부를 먼저 확인해 주세요.');receipt={id:j.id,receipt:j.receipt};saveSession(tabStorage,key,receipt);$('receipt-number').textContent=j.id;$('receipt').hidden=false;say('upload-message','접수됐어요. 분석하는 동안 다른 참가자도 사진을 제출할 수 있어요.');$('receipt').scrollIntoView({behavior:'smooth',block:'start'});checkStatus();
 }catch(e){say('upload-message',e.name==='AbortError'?'전송 확인 시간이 지났어요. 운영자에게 접수 여부를 확인한 뒤 다시 제출해 주세요.':e.message||'전송 확인이 끊겼어요. 운영자에게 접수 여부를 확인한 뒤 다시 제출해 주세요.',true);}finally{clearTimeout(timeout);busy=false;request=null;sync();}
};
$('new-submission').onclick=()=>{clearTimeout(pollTimer);receipt=null;retakeState=null;forgetSession(tabStorage);saveSession(tabStorage,key,null);clearRetakes();$('retake-form').hidden=true;$('check-status').disabled=false;$('receipt').hidden=true;$('intake-form').reset();for(const which of ['top','side']){photos[which]=null;if(previews[which])URL.revokeObjectURL(previews[which]);previews[which]=null;$(which+'-preview').hidden=true;$(which+'-placeholder').hidden=false;$(which+'-name').textContent='선택한 사진 없음';}say('upload-message','새 접수번호로 저장돼요. 이전 기록 삭제는 운영자에게 요청해 주세요.');sync();$('intake-form').scrollIntoView({behavior:'smooth'});};
const joined=new URLSearchParams(location.hash.slice(1)).get('key');if(location.hash)history.replaceState(null,'',location.pathname);restoreReceipt(joined||'');
window.addEventListener('pagehide',()=>{clearTimeout(pollTimer);receipt=null;request?.abort();for(const url of [...Object.values(previews),...Object.values(retakePreviews)])if(url)URL.revokeObjectURL(url);});

function showRetake(){const views=requestedViews(retakeState);$('retake-form').hidden=!views.length;for(const view of ['top','side'])$('retake-'+view+'-panel').hidden=!views.includes(view);$('retake-instructions').textContent=retakeState?.retake_reason||'';}
function clearRetakes(){for(const view of ['top','side']){retakePhotos[view]=null;if(retakePreviews[view])URL.revokeObjectURL(retakePreviews[view]);retakePreviews[view]=null;$('retake-'+view+'-preview').hidden=true;$('retake-'+view+'-file').value='';}$('retake-consent').checked=false;}
for(const view of ['top','side'])$('retake-'+view+'-file').onchange=event=>{
 const file=event.target.files[0];if(!file)return;if(!file.size||file.size>20*1024*1024){say('retake-message','사진 한 장은 20MB 이하여야 해요.',true);event.target.value='';return;}
 retakePhotos[view]=file;if(retakePreviews[view])URL.revokeObjectURL(retakePreviews[view]);retakePreviews[view]=URL.createObjectURL(file);const img=$('retake-'+view+'-preview');img.src=retakePreviews[view];img.hidden=false;say('retake-message','');
};
$('retake-form').onsubmit=async event=>{
 event.preventDefault();const views=requestedViews(retakeState);if(!receipt||busy||!ready||!views.length||!$('retake-consent').checked)return;
 if(views.some(v=>!retakePhotos[v])){say('retake-message','요청한 사진을 모두 선택해 주세요.',true);return;}
 const target=receipt,form=new FormData();form.append('id',receipt.id);form.append('receipt',receipt.receipt);form.append('revision',String(retakeState.revision));form.append('consent','true');for(const view of views)form.append(view+'_image',retakePhotos[view],view+'-original');
 statusEpoch++;busy=true;$('submit-retake').disabled=true;$('new-submission').disabled=true;clearTimeout(pollTimer);say('retake-message','요청한 사진을 전송하고 있어요.');
 try{const r=await fetch('/intake/retake',{method:'POST',headers:{Authorization:`Bearer ${key}`},body:form,cache:'no-store',redirect:'error',signal:AbortSignal.timeout(120000)});const j=await readBoundedJson(r,8192);if(receipt!==target)return;if(!r.ok)throw Error(typeof j.detail==='string'?j.detail:'재촬영 사진을 제출하지 못했어요.');clearRetakes();$('retake-form').hidden=true;say('retake-message','새 사진을 같은 접수번호로 저장했어요.');checkStatus();}
 catch(error){if(receipt===target){say('retake-message',error.message||'전송 결과를 확인해 주세요.',true);checkStatus();}}
 finally{busy=false;$('submit-retake').disabled=false;$('new-submission').disabled=false;sync();}
};
async function restoreReceipt(joined){
 const stored=restoreSession(tabStorage);if(stored&&(!joined||joined===stored.key)){receipt=stored.receipt;if(receipt){$('receipt-number').textContent=receipt.id;$('receipt').hidden=false;}}
 await checkKey(joined||stored?.key||'');if(receipt&&ready)checkStatus();
}
$('intake-form').onchange=()=>{const hand=new FormData($('intake-form')).get('hand');$('confirmation-hand').textContent=`${$('display-name').value.trim()||'이름 미입력'} · ${hand==='left'?'왼손':hand==='right'?'오른손':'손 선택 필요'}`;};
