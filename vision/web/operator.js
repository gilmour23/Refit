import {readBoundedJson} from './core.mjs';
const $=id=>document.getElementById(id);
for(const view of ['top','side'])$('result-'+view+'-tab').onclick=()=>{for(const which of ['top','side']){$('result-'+which+'-panel').hidden=which!==view;$('result-'+which+'-tab').setAttribute('aria-selected',String(which===view));}};
const labels={queued:'분석 대기',processing:'분석 중',complete:'측정 완료',failed:'확인 필요'};
const definitions={
 1:['0번에서 팔 쪽 4cm 폭','손목 0점에서 팔 쪽으로 40mm'],2:['① 위치 두께','①과 같은 높이 · 측면'],
 3:['0번 위치 손목 폭','손목점 높이의 가로 폭'],4:['③ 위치 두께','손목점 높이 · 측면'],
 5:['18번 ↔ 0번 세로 거리','두 점을 지나는 가로선 사이 간격'],6:['2번 ↔ 0번 세로 거리','두 점을 지나는 가로선 사이 간격'],
 7:['5번 ↔ 2번 세로 거리','두 점을 지나는 가로선 사이 간격'],8:['2번 → 가로 → 손 테두리','엄지 관절에서 새끼 쪽 손 끝까지'],
 9:['5번 → 가로 → 손 테두리','검지 뿌리에서 새끼 쪽 손 끝까지'],10:['⑧ 위치 두께','2번 높이 · 측면'],11:['⑨ 위치 두께','5번 높이 · 측면']};
const colors={1:'#252525',2:'#252525',3:'#6a6a6a',4:'#6a6a6a',5:'#df3b3b',6:'#9745cc',7:'#dc8100',8:'#226be0',9:'#13995b',10:'#226be0',11:'#13995b'};
const circles='①②③④⑤⑥⑦⑧⑨⑩⑪';
let deleteTarget=null;
let key='',selected=null,current=null,epoch=0,controller=null,urls=[],timer=null,searchTimer=null,offset=0,total=0,listBusy=false,sessionEpoch=0;
function say(text,error=false){$('operator-message').textContent=text;$('operator-message').classList.toggle('error',error);}
async function api(path,options={}){
 const r=await fetch(path,{...options,headers:{Authorization:`Bearer ${key}`,...options.headers},cache:'no-store',redirect:'error'});
 if(!r.ok){let j;try{j=await readBoundedJson(r,8192);}catch{}if(r.status===401)throw Error('관리자 키를 확인해 주세요.');throw Error(typeof j?.detail==='string'?j.detail:`서버 응답 오류 (${r.status})`);}return r;
}
function stamp(value){return new Date(value).toLocaleString('ko-KR',{timeZone:'Asia/Seoul',month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit'});}
function clearImages(){for(const url of urls)URL.revokeObjectURL(url);urls=[];for(const view of ['top','side']){$(view+'-photo').removeAttribute('src');$(view+'-photo').hidden=true;$(view+'-photo-link').removeAttribute('href');$(view+'-photo-empty').hidden=false;}}
function clearDetail(){epoch++;controller?.abort();controller=null;current=null;clearImages();$('record-content').hidden=true;$('empty-selection').hidden=false;}
function dimensionRows(ids,result){return ids.map(id=>{
 const row=document.createElement('div');row.style.setProperty('--dimension-color',colors[id]);const badge=document.createElement('span');badge.className='dimension-number';badge.textContent=circles[id-1];const dt=document.createElement('dt'),small=document.createElement('small'),dd=document.createElement('dd');dt.textContent=definitions[id][0];small.textContent=definitions[id][1];dt.append(small);const value=result?.dims_mm?.[String(id)];dd.textContent=typeof value==='number'&&Number.isFinite(value)&&value>0?value.toFixed(1):'—';const unit=document.createElement('small');unit.textContent=' mm';dd.append(unit);row.append(badge,dt,dd);return row;
});}
async function selectRecord(id){
 clearDetail();selected=id;const version=epoch;controller=new AbortController();const signal=controller.signal;
 for(const b of $('participant-list').querySelectorAll('button'))b.classList.toggle('active',b.dataset.id===id);
 $('empty-selection').querySelector('h2').textContent='측정 기록을 불러오고 있어요';
 try{const r=await api('/admin/intakes/'+id,{signal});const record=await readBoundedJson(r,256*1024);if(version!==epoch)return;current=record;
 $('record-number').textContent='접수번호 '+record.id;$('record-name').textContent=record.display_name;$('record-meta').textContent=`${record.hand==='left'?'왼손':'오른손'} · 접수 ${stamp(record.created_at)}`;$('record-status').textContent=labels[record.status]||record.status;$('record-message').textContent=record.message;$('record-message').classList.toggle('error',record.status==='failed');
 const warnings=Array.isArray(record.result?.warnings)?record.result.warnings.filter(v=>typeof v==='string'):[];$('record-warnings').replaceChildren(...warnings.map(text=>{const li=document.createElement('li');li.textContent=text;return li;}));
 $('top-dimensions').replaceChildren(...dimensionRows([5,6,7,8,9,3,1],record.result));$('side-dimensions').replaceChildren(...dimensionRows([2,4,10,11],record.result));
 for(const b of document.querySelectorAll('[data-download]'))b.disabled=!record.assets.includes(b.dataset.download);$('delete-record').disabled=['queued','processing'].includes(record.status);$('record-content').hidden=false;$('empty-selection').hidden=true;
 await Promise.all(['top','side'].map(async view=>{const asset=record.assets.includes(view+'_overlay')?view+'_overlay':record.assets.includes(view)?view:null;if(!asset)return;try{const response=await api(`/admin/intakes/${id}/assets/${asset}`,{signal});const blob=await response.blob();if(version!==epoch)return;const url=URL.createObjectURL(blob);urls.push(url);$(view+'-photo').src=url;$(view+'-photo').hidden=false;$(view+'-photo-link').href=url;$(view+'-photo-empty').hidden=true;}catch(e){if(version===epoch&&e.name!=='AbortError')$(view+'-photo-empty').textContent='사진을 읽지 못했어요. 다시 선택해 주세요.';}}));
 }catch(e){if(version!==epoch||e.name==='AbortError')return;clearDetail();say(e.message||'기록을 불러오지 못했어요.',true);}
}
async function refresh(){
 if(!key||listBusy)return;listBusy=true;const version=sessionEpoch;
 try{const q=$('search').value.trim();const r=await api(`/admin/intakes?q=${encodeURIComponent(q)}&limit=30&offset=${offset}`,{signal:AbortSignal.timeout(15000)});const j=await readBoundedJson(r,256*1024);if(version!==sessionEpoch)return;total=j.count;
 $('total-count').textContent=`${total}건 · 5초마다 갱신`;$('page-label').textContent=`${Math.floor(offset/30)+1} / ${Math.max(1,Math.ceil(total/30))}`;$('previous').disabled=offset===0;$('next').disabled=offset+30>=total;
 const buttons=j.items.map(record=>{const b=document.createElement('button');b.type='button';b.dataset.id=record.id;b.classList.toggle('active',selected===record.id);const name=document.createElement('strong'),state=document.createElement('span'),meta=document.createElement('span'),number=document.createElement('span');name.textContent=record.display_name;state.textContent=labels[record.status]||record.status;state.className='status-'+record.status;meta.textContent=`${record.hand==='left'?'왼손':'오른손'} · ${stamp(record.created_at)}`;number.textContent=record.id.slice(0,8);b.append(name,state,meta,number);b.onclick=()=>selectRecord(record.id);return b;});
 if(!buttons.length){const empty=document.createElement('p');empty.className='fine';empty.textContent=q?'검색 결과가 없어요.':'아직 접수된 사진이 없어요. 참가자 QR을 안내해 주세요.';buttons.push(empty);}$('participant-list').replaceChildren(...buttons);
 if(selected){const row=j.items.find(x=>x.id===selected);if(row&&current&&(row.updated_at!==current.updated_at||row.status!==current.status))selectRecord(selected);}
 say('');
 }catch(e){if(version===sessionEpoch)say(e.message||'목록을 불러오지 못했어요.',true);throw e;}finally{listBusy=false;}
}
$('login').onclick=async()=>{const candidate=$('admin-key').value.trim();if(!/^[A-Za-z0-9_-]{32,256}$/.test(candidate)){say('노트북의 운영자 시작 화면에 표시된 관리자 키를 입력해 주세요.',true);return;}key=candidate;$('login').disabled=true;try{await refresh();$('admin-key').value='';$('login-panel').hidden=true;$('dashboard').hidden=false;$('logout').hidden=false;clearInterval(timer);timer=setInterval(()=>refresh().catch(()=>{}),5000);}catch{key='';}finally{$('login').disabled=false;}};
$('admin-key').onkeydown=e=>{if(e.key==='Enter')$('login').click();};
$('refresh').onclick=()=>refresh().catch(()=>{});$('search').oninput=()=>{clearTimeout(searchTimer);searchTimer=setTimeout(()=>{offset=0;refresh().catch(()=>{});},250);};
$('previous').onclick=()=>{offset=Math.max(0,offset-30);refresh().catch(()=>{});};$('next').onclick=()=>{offset+=30;refresh().catch(()=>{});};
$('logout').onclick=()=>{sessionEpoch++;key='';selected=null;offset=0;clearInterval(timer);clearDetail();$('participant-list').replaceChildren();$('dashboard').hidden=true;$('login-panel').hidden=false;$('logout').hidden=true;say('관리 화면을 잠갔어요.');};
for(const button of document.querySelectorAll('[data-download]'))button.onclick=async()=>{if(!current)return;const id=current.id,asset=button.dataset.download,version=sessionEpoch;button.disabled=true;try{const r=await api(`/admin/intakes/${id}/assets/${asset}`,{signal:AbortSignal.timeout(30000)}),blob=await r.blob();if(version!==sessionEpoch)return;const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;const ext=asset==='csv'?'csv':blob.type.includes('heic')?'heic':blob.type.includes('png')?'png':'jpg';a.download=`${id}_${asset}.${ext}`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}catch(e){if(version===sessionEpoch)say(e.message||'파일을 받지 못했어요.',true);}finally{if(current?.id===id)button.disabled=false;}};
$('delete-record').onclick=()=>{if(!current)return;deleteTarget={id:current.id,version:sessionEpoch};$('delete-description').textContent=`${current.display_name} · ${current.id}`;$('delete-dialog').showModal();};$('cancel-delete').onclick=()=>$('delete-dialog').close();
$('confirm-delete').onclick=async()=>{if(!deleteTarget||deleteTarget.version!==sessionEpoch)return;const id=deleteTarget.id;$('confirm-delete').disabled=true;try{await api('/admin/intakes/'+id,{method:'DELETE',signal:AbortSignal.timeout(15000)});$('delete-dialog').close();deleteTarget=null;selected=null;clearDetail();offset=0;await refresh();say('접수 정보와 사진·결과를 삭제했어요.');}catch(e){say(e.message||'삭제하지 못했어요.',true);}finally{$('confirm-delete').disabled=false;}};
if(location.hash)history.replaceState(null,'',location.pathname);
window.addEventListener('pagehide',()=>{clearInterval(timer);clearTimeout(searchTimer);sessionEpoch++;clearDetail();key='';});
