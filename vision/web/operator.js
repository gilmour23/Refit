import {readBoundedJson} from './core.mjs';
import {sections,lengths,dimensionValue,hasCompleteDimensions,measurementFilename} from './fabrication.mjs';
import {imagePoint,CorrectionDraft,recordIdentity} from './geometry.mjs';
import {fitPhotoSize,reviewStage} from './workbench.mjs';
const $=id=>document.getElementById(id);
function showResultView(view){activeView=view;for(const which of ['top','side']){$('result-'+which+'-panel').hidden=which!==view;$('result-'+which+'-tab').setAttribute('aria-selected',String(which===view));}$('correction-view').value=view;if(current)showCalibration();fitPhotos();}
for(const view of ['top','side'])$('result-'+view+'-tab').onclick=()=>showResultView(view);
const labels={queued:'분석 대기',processing:'분석 중',complete:'측정 완료',failed:'확인 필요'};
const definitions={
 1:['0번에서 팔 쪽 4cm 폭','손목 0점에서 팔 쪽으로 40mm'],2:['① 위치 두께','①과 같은 높이 · 측면'],
 3:['0번 위치 손목 폭','손목점 높이의 가로 폭'],4:['③ 위치 두께','손목점 높이 · 측면'],
 5:['18번 ↔ 0번 세로 거리','두 점을 지나는 가로선 사이 간격'],6:['2번 ↔ 0번 세로 거리','두 점을 지나는 가로선 사이 간격'],
 7:['5번 ↔ 2번 세로 거리','두 점을 지나는 가로선 사이 간격'],8:['2번 → 가로 → 손 테두리','엄지 관절에서 새끼 쪽 손 끝까지'],
 9:['5번 → 가로 → 손 테두리','검지 뿌리에서 새끼 쪽 손 끝까지'],10:['⑧ 위치 두께','2번 높이 · 측면'],11:['⑨ 위치 두께','5번 높이 · 측면']};
const colors={1:'#252525',2:'#252525',3:'#6a6a6a',4:'#6a6a6a',5:'#df3b3b',6:'#9745cc',7:'#dc8100',8:'#226be0',9:'#13995b',10:'#226be0',11:'#13995b'};
const circles='①②③④⑤⑥⑦⑧⑨⑩⑪';
let activeStep='photo';
let deleteTarget=null,draft=null,activeDimension=null,activeView='top',actionBusy=false,printReady=false;
const reviewLabels={unreviewed:'검토 대기',reviewed:'검토 완료',retake_requested:'재촬영 요청'};
let key='',selected=null,current=null,epoch=0,controller=null,urls=[],timer=null,searchTimer=null,offset=0,total=0,listBusy=false,sessionEpoch=0;
function say(text,error=false){$('operator-message').textContent=text;$('operator-message').classList.toggle('error',error);}
async function api(path,options={}){
 const r=await fetch(path,{...options,headers:{Authorization:`Bearer ${key}`,...options.headers},cache:'no-store',redirect:'error'});
 if(!r.ok){let j;try{j=await readBoundedJson(r,8192);}catch{}if(r.status===401)throw Error('관리자 키를 확인해 주세요.');throw Error(typeof j?.detail==='string'?j.detail:`서버 응답 오류 (${r.status})`);}return r;
}
function stamp(value){return new Date(value).toLocaleString('ko-KR',{timeZone:'Asia/Seoul',month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit'});}
function clearImages(){for(const url of urls)URL.revokeObjectURL(url);urls=[];for(const view of ['top','side']){$(view+'-photo').removeAttribute('src');$(view+'-photo').hidden=true;$(view+'-photo-link').removeAttribute('href');$(view+'-photo-link').hidden=true;$(view+'-photo-empty').hidden=false;}}
function clearDetail(){epoch++;controller?.abort();controller=null;current=null;draft=null;printReady=false;clearImages();for(const view of ['top','side'])$(view+'-geometry').replaceChildren();$('print-sheet').disabled=true;$('record-content').hidden=true;$('empty-selection').hidden=false;}
function dimensionRows(ids,result){return ids.map(id=>{
 const row=document.createElement('div');row.style.setProperty('--dimension-color',colors[id]);const badge=document.createElement('span');badge.className='dimension-number';badge.textContent=circles[id-1];const dt=document.createElement('dt'),small=document.createElement('small'),dd=document.createElement('dd');const label=lengths.find(x=>x.id===id);dt.textContent=label?.label||definitions[id][0];small.textContent=label?.detail||definitions[id][1];dt.append(small);const value=result?.dims_mm?.[String(id)];dd.textContent=typeof value==='number'&&Number.isFinite(value)&&value>0?value.toFixed(1):'—';const unit=document.createElement('small');unit.textContent=' mm';dd.append(unit);row.append(badge,dt,dd);row.tabIndex=0;row.setAttribute('role','button');row.dataset.dimension=String(id);row.onclick=()=>selectDimension(id);row.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();selectDimension(id);}};return row;
});}
function sectionRows(result){return sections.map(section=>{
 const row=document.createElement('tr'),place=document.createElement('th'),detail=document.createElement('small');place.scope='row';place.textContent=section.label;detail.textContent=section.location;place.append(detail);row.append(place);
 for(const id of [section.width,section.thickness]){const cell=document.createElement('td'),badge=document.createElement('span'),value=document.createElement('span');badge.className='dimension-number';badge.style.setProperty('--dimension-color',colors[id]);badge.textContent=circles[id-1];value.className='measurement-value';const n=dimensionValue(result,id);value.textContent=n===null?'—':n.toFixed(1);const pick=document.createElement('button');pick.type='button';pick.className='dimension-pick';pick.dataset.dimension=String(id);pick.setAttribute('aria-label',`${circles[id-1]} ${definitions[id][0]} ${n===null?'미측정':n.toFixed(1)+' mm'}`);pick.append(badge,value);pick.onclick=()=>selectDimension(id);cell.append(pick);row.append(cell);}return row;
});}
async function selectRecord(id,revision=null,discard=false){
 if(!discard&&!canLeave())return;
 if(selected!==id){activeStep='photo';activeDimension=null;}
 clearDetail();selected=id;const version=epoch;controller=new AbortController();const signal=controller.signal;
 for(const b of $('participant-list').querySelectorAll('button'))b.classList.toggle('active',b.dataset.id===id);
 $('empty-selection').querySelector('h2').textContent='측정 기록을 불러오고 있어요';
 try{const r=await api('/admin/intakes/'+id+(revision?`?revision=${revision}`:''),{signal});const record=await readBoundedJson(r,256*1024);if(version!==epoch)return;current=record;draft=new CorrectionDraft(record.overrides||{});$('review-ack').checked=false;$('retake-top').checked=false;$('retake-side').checked=false;$('retake-reason').value='';
 const choices=(record.history||[{revision:record.revision||1,source:'upload'}]).map(item=>{const option=document.createElement('option');option.value=String(item.revision);option.textContent=`v${item.revision} · ${item.source==='correction'?'위치 보정':item.source==='retake'?'재촬영':'최초 사진'}${item.status==='failed'?' · 계산 실패':''}`;return option;});$('revision-select').replaceChildren(...choices);$('revision-select').value=String(record.revision||1);$('revision-note').textContent=isLatest()?'현재 결과 · 이전 버전은 목록에서 확인할 수 있어요.':`이전 결과입니다. 현재 버전은 v${record.current_revision}입니다.`;
 setReviewStep(activeStep);
 $('review-state').textContent=(reviewLabels[record.review_status]||'검토 대기')+(record.reviewed_at?' · '+stamp(record.reviewed_at):'');updateControls();
 $('record-number').textContent='접수번호 '+record.id;$('record-name').textContent=record.display_name;$('record-hand').textContent=record.hand==='left'?'왼손':'오른손';$('record-meta').textContent=`접수 ${stamp(record.created_at)}`;$('record-status').textContent=(labels[record.status]||record.status)+' · '+(reviewLabels[record.review_status]||'검토 대기');$('record-message').textContent=record.message;$('record-message').classList.toggle('error',record.status==='failed');
 const complete=hasCompleteDimensions(record);$('fabrication-summary').textContent=complete?'11개 치수 계산 완료 · 사진 대조 후 검토 완료를 저장하세요':record.status==='failed'?'보정 또는 재촬영이 필요합니다':'분석이 끝나면 치수가 표시됩니다';
 const warnings=Array.isArray(record.result?.warnings)?record.result.warnings.filter(v=>typeof v==='string'):[];$('record-warnings').replaceChildren(...warnings.map(text=>{const li=document.createElement('li');li.textContent=text;return li;}));$('measurement-alerts').hidden=!warnings.length;$('measurement-alerts').open=false;$('warning-summary').textContent=warnings.length?`확인할 내용 ${warnings.length}건 · ${warnings[0]}`:'촬영·측정 안내';
 $('section-dimensions').replaceChildren(...sectionRows(record.result));$('length-dimensions').replaceChildren(...dimensionRows(lengths.map(x=>x.id),record.result));
 for(const b of document.querySelectorAll('[data-download]'))b.disabled=!record.assets.includes(b.dataset.download);$('delete-record').disabled=['queued','processing'].includes(record.status);$('record-content').hidden=false;$('empty-selection').hidden=true;
 const loaded=await Promise.all(['top','side'].map(async view=>{const asset=$('show-original').checked?(record.assets.includes(view)?view:null):record.assets.includes(view+'_overlay')?view+'_overlay':record.assets.includes(view)?view:null;$(view+'-photo-empty').textContent='아직 '+(view==='top'?'손등':'측면')+' 측정 사진이 없어요.';if(!asset)return false;try{const response=await api(`/admin/intakes/${id}/assets/${asset}?revision=${record.revision||1}`,{signal});const blob=await response.blob();if(version!==epoch)return false;const url=URL.createObjectURL(blob);urls.push(url);const image=$(view+'-photo');image.src=url;await image.decode();if(version!==epoch)return false;image.hidden=false;fitPhotos();$(view+'-photo-link').href=url;$(view+'-photo-link').hidden=false;drawGeometry(view);$(view+'-photo-empty').hidden=true;return asset===view+'_overlay';}catch(e){if(version===epoch&&e.name!=='AbortError')$(view+'-photo-empty').textContent='사진을 읽지 못했어요. 다시 선택해 주세요.';return false;}}));
 if(version===epoch){printReady=complete&&loaded.every(Boolean);$('print-sheet').disabled=!printReady;showCalibration();fitPhotos();if(activeDimension)selectDimension(activeDimension);updateControls();}
 }catch(e){if(version!==epoch||e.name==='AbortError')return;clearDetail();say(e.message||'기록을 불러오지 못했어요.',true);}
}
async function refresh(){
 if(!key||listBusy)return;listBusy=true;const version=sessionEpoch;
 try{const q=$('search').value.trim();const r=await api(`/admin/intakes?q=${encodeURIComponent(q)}&review=${encodeURIComponent($('review-filter').value||'')}&limit=30&offset=${offset}`,{signal:AbortSignal.timeout(15000)});const j=await readBoundedJson(r,256*1024);if(version!==sessionEpoch)return;total=j.count;
 $('total-count').textContent=`${total}건 · 5초마다 갱신`;$('page-label').textContent=`${Math.floor(offset/30)+1} / ${Math.max(1,Math.ceil(total/30))}`;$('previous').disabled=offset===0;$('next').disabled=offset+30>=total;
 const buttons=j.items.map(record=>{const b=document.createElement('button');b.type='button';b.dataset.id=record.id;b.classList.toggle('active',selected===record.id);const name=document.createElement('strong'),state=document.createElement('span'),meta=document.createElement('span'),number=document.createElement('span');name.textContent=record.display_name;state.textContent=(labels[record.status]||record.status)+' · '+(reviewLabels[record.review_status]||'검토 대기');state.className='status-'+record.status;meta.textContent=`${record.hand==='left'?'왼손':'오른손'} · ${stamp(record.created_at)}`;number.textContent=record.id.slice(0,8);b.append(name,state,meta,number);b.onclick=()=>selectRecord(record.id);return b;});
 if(!buttons.length){const empty=document.createElement('p');empty.className='fine';empty.textContent=q?'검색 결과가 없어요.':'아직 접수된 사진이 없어요. 참가자 QR을 안내해 주세요.';buttons.push(empty);}$('participant-list').replaceChildren(...buttons);
 if(selected){const row=j.items.find(x=>x.id===selected);if(row&&current&&!draft?.dirty&&!actionBusy&&(row.updated_at!==current.updated_at||row.status!==current.status)){const pinned=isLatest()?null:current.revision;selectRecord(selected,pinned,true);}}
 say('');
 }catch(e){if(version===sessionEpoch)say(e.message||'목록을 불러오지 못했어요.',true);throw e;}finally{listBusy=false;}
}
$('login').onclick=async()=>{const candidate=$('admin-key').value.trim();if(!/^[A-Za-z0-9_-]{32,256}$/.test(candidate)){say('노트북의 운영자 시작 화면에 표시된 관리자 키를 입력해 주세요.',true);return;}key=candidate;$('login').disabled=true;try{await refresh();$('admin-key').value='';$('login-panel').hidden=true;$('dashboard').hidden=false;$('logout').hidden=false;clearInterval(timer);timer=setInterval(()=>refresh().catch(()=>{}),5000);}catch{key='';}finally{$('login').disabled=false;}};
$('admin-key').onkeydown=e=>{if(e.key==='Enter')$('login').click();};
$('refresh').onclick=()=>refresh().catch(()=>{});$('search').oninput=()=>{clearTimeout(searchTimer);searchTimer=setTimeout(()=>{offset=0;refresh().catch(()=>{});},250);};
$('previous').onclick=()=>{offset=Math.max(0,offset-30);refresh().catch(()=>{});};$('next').onclick=()=>{offset+=30;refresh().catch(()=>{});};
$('logout').onclick=()=>{sessionEpoch++;key='';selected=null;offset=0;clearInterval(timer);clearDetail();$('participant-list').replaceChildren();$('dashboard').hidden=true;$('login-panel').hidden=false;$('logout').hidden=true;say('관리 화면을 잠갔어요.');};
for(const button of document.querySelectorAll('[data-download]'))button.onclick=async()=>{if(!current)return;const record=current,id=record.id,identity=recordIdentity(record),asset=button.dataset.download,version=sessionEpoch;button.disabled=true;try{const r=await api(`/admin/intakes/${id}/assets/${asset}?revision=${record.revision||1}`,{signal:AbortSignal.timeout(30000)}),blob=await r.blob();if(version!==sessionEpoch||recordIdentity(current)!==identity)return;const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;const ext=asset==='csv'?'csv':blob.type.includes('heic')||blob.type.includes('heif')?'heic':blob.type.includes('png')?'png':'jpg';a.download=measurementFilename(record,asset,ext);a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}catch(e){if(version===sessionEpoch)say(e.message||'파일을 받지 못했어요.',true);}finally{if(recordIdentity(current)===identity)button.disabled=false;}};
$('print-sheet').onclick=()=>{if(hasCompleteDimensions(current)&&!draft?.dirty&&!$('print-sheet').disabled)window.print();};
$('delete-record').onclick=()=>{if(!current||!canLeave())return;deleteTarget={id:current.id,version:sessionEpoch};$('delete-description').textContent=`${current.display_name} · ${current.id}`;$('delete-dialog').showModal();};$('cancel-delete').onclick=()=>$('delete-dialog').close();
$('confirm-delete').onclick=async()=>{if(!deleteTarget||deleteTarget.version!==sessionEpoch)return;const id=deleteTarget.id;$('confirm-delete').disabled=true;try{await api('/admin/intakes/'+id,{method:'DELETE',signal:AbortSignal.timeout(15000)});$('delete-dialog').close();deleteTarget=null;selected=null;clearDetail();offset=0;await refresh();say('접수 정보와 사진·결과를 삭제했어요.');}catch(e){say(e.message||'삭제하지 못했어요.',true);}finally{$('confirm-delete').disabled=false;}};
if(location.hash)history.replaceState(null,'',location.pathname);
window.addEventListener('pagehide',()=>{clearInterval(timer);clearTimeout(searchTimer);sessionEpoch++;clearDetail();key='';});

function isLatest(){return !!current&&(!current.current_revision||current.current_revision===(current.revision||1));}
function canLeave(){return !draft?.dirty||window.confirm('저장하지 않은 위치 보정이 있어요. 버리고 이동할까요?');}
function selectDimension(id){
 activeDimension=id;const view=[2,4,10,11].includes(id)?'side':'top';showResultView(view);
 for(const el of document.querySelectorAll('[data-dimension]')){const selected=Number(el.dataset.dimension)===id;el.classList.toggle('selected-dimension',selected);el.setAttribute('aria-pressed',String(selected));}
 const line=current?.result?.geometry?.[view]?.lines?.[String(id)];
 $('selected-measurement').textContent=`${circles[id-1]} ${definitions[id][0]} · ${dimensionValue(current?.result,id)??'—'} mm`+(line?'':' · 이 결과에는 선택 가능한 선 좌표가 없어요. 원본을 다시 계산해 주세요.');
 for(const which of ['top','side'])drawGeometry(which);
}
function drawGeometry(view){
 const svg=$(view+'-geometry'),geometry=current?.result?.geometry?.[view];svg.replaceChildren();if(!geometry)return;
 svg.setAttribute('viewBox',`0 0 ${geometry.width} ${geometry.height}`);
 function shape(type,attributes){const el=document.createElementNS('http://www.w3.org/2000/svg',type);for(const [k,v] of Object.entries(attributes))el.setAttribute(k,String(v));svg.append(el);return el;}
 const line=geometry.lines?.[String(activeDimension)];
 if(line){const [a,b]=line;shape('line',{x1:a[0],y1:a[1],x2:b[0],y2:b[1],stroke:'#fff','stroke-width':9,'vector-effect':'non-scaling-stroke'});shape('line',{x1:a[0],y1:a[1],x2:b[0],y2:b[1],stroke:colors[activeDimension]||'#226be0','stroke-width':4,'vector-effect':'non-scaling-stroke'});}
 const editing=activeStep==='photo'&&draft?.view===view&&!!draft.kind;
 svg.style.pointerEvents=editing?'auto':'none';svg.classList.toggle('editing',editing);
 if(activeStep==='photo'||!!draft?.kind){
  const card=draft?.value['card_'+view]||geometry.card,wrist=draft?.value['wrist_'+view]||geometry.wrist;
  if(card?.length===4)shape('polygon',{points:card.map(p=>p.join(',')).join(' '),fill:'none',stroke:'#db8500','stroke-width':2,'vector-effect':'non-scaling-stroke'});
  const pts=editing&&draft.points.length?draft.points:(editing&&draft.kind==='card'?card||[]:wrist?[wrist]:[]);
  const radius=Math.max(geometry.width,geometry.height)/90;
  pts.forEach((p,i)=>{shape('circle',{cx:p[0],cy:p[1],r:radius,fill:'#fff',stroke:'#12684f','stroke-width':2,'vector-effect':'non-scaling-stroke'});const text=shape('text',{x:p[0],y:p[1],fill:'#12684f','font-size':radius*1.3,'text-anchor':'middle','dominant-baseline':'central'});text.textContent=editing&&draft.kind==='card'?String(i+1):'0';});
 }
}
function showCalibration(){
 const result=current?.result,view=activeView,refs={card:'카드 자동 검출',card_tap:'카드 모서리 직접 지정',marker:'마커 자동 검출'},wrists={manual:'직접 지정',mediapipe:'손 관절점 자동 검출',auto:'자동 보정'};
 const facts=[['사진',view==='top'?'손등 · 가로 치수와 길이':'측면 · 두께'],['크기 기준',refs[result?.reference?.[view]]||'기준을 검출하지 못함'],['손목 0점',wrists[result?.wrist_method?.[view]]||'위치를 검출하지 못함'],['높이 보정',result?.camera_height_mm?.[view]?'EXIF 정보로 보정 적용':'EXIF 높이 보정 미적용']];
 $('calibration-info').replaceChildren(...facts.map(([label,value])=>{const row=document.createElement('div'),dt=document.createElement('dt'),dd=document.createElement('dd');dt.textContent=label;dd.textContent=value;row.append(dt,dd);return row;}));
}
function updateControls(){
 const mutable=isLatest()&&!['queued','processing'].includes(current?.status)&&!actionBusy;
 for(const id of ['edit-card','edit-wrist','auto-points','request-retake'])$(id).disabled=!mutable;
 $('undo-point').disabled=!mutable||!draft?.steps.length;
 $('apply-correction').disabled=!mutable||(!draft?.canApply&&!!current?.result?.geometry);
 $('apply-correction').textContent=current?.result?.geometry?'새 버전으로 저장·재계산':'원본 다시 계산 · 좌표 생성';
 $('mark-reviewed').disabled=!mutable||!hasCompleteDimensions(current)||!$('review-ack').checked||!!draft?.dirty||current?.review_status==='reviewed';
 $('print-sheet').disabled=!printReady||!!draft?.dirty;
 updateTaskActions();
 $('edit-card').setAttribute('aria-pressed',String(activeStep==='photo'&&draft?.kind==='card'));$('edit-wrist').setAttribute('aria-pressed',String(activeStep==='photo'&&draft?.kind==='wrist'));
 $('correction-instruction').textContent=draft?.kind?`${draft.view==='top'?'손등':'측면'} 사진: ${draft.kind==='card'?`카드 모서리를 둘레를 따라 선택하세요 (${draft.points.length}/4).`:'손목 0점을 선택하세요.'} ${draft.dirty?'저장 전에는 현재 치수에 반영되지 않습니다.':''}`:'위치를 선택한 뒤 새 버전으로 저장하면 11개 치수를 다시 계산합니다.';
}
function startCorrection(kind){if(!current||!isLatest()||actionBusy)return;const view=$('correction-view').value;if(!current.result?.geometry?.[view]){say('먼저 원본을 다시 계산해 사진 좌표를 생성해 주세요.',true);return;}if(draft.kind&&draft.points.length&&!draft.canApply){say('현재 항목의 점을 모두 선택하거나 보정을 취소해 주세요.',true);return;}if(draft.view!==view||draft.kind!==kind)draft.start(view,kind);setReviewStep('photo');showResultView(view);for(const v of ['top','side'])drawGeometry(v);updateControls();}
for(const view of ['top','side'])$(view+'-geometry').onclick=event=>{if(activeStep!=='photo'||draft?.view!==view||!draft.kind)return;const point=imagePoint(event,$(view+'-geometry').getBoundingClientRect(),current.result.geometry[view]);if(point){draft.add(point);drawGeometry(view);updateControls();}};
$('edit-card').onclick=()=>startCorrection('card');$('edit-wrist').onclick=()=>startCorrection('wrist');
$('undo-point').onclick=()=>{draft?.undo();for(const view of ['top','side'])drawGeometry(view);updateControls();};
$('auto-points').onclick=()=>{draft?.automatic();for(const view of ['top','side'])drawGeometry(view);updateControls();};
$('cancel-correction').onclick=()=>{if(!current)return;draft=new CorrectionDraft(current.overrides||{});selectRecord(current.id,current.revision,true);};
$('photo-zoom').onchange=fitPhotos;
$('show-original').onchange=()=>{if(current){if(!canLeave()){$('show-original').checked=!$('show-original').checked;return;}selectRecord(current.id,current.revision,true);}};
$('revision-select').onchange=()=>{if(!current)return;const revision=Number($('revision-select').value);if(!canLeave()){$('revision-select').value=String(current.revision);return;}selectRecord(current.id,revision,true);};
$('review-filter').onchange=()=>{offset=0;refresh().catch(()=>{});};
$('review-ack').onchange=updateControls;
async function mutateRecord(action,body){
 if(!current||actionBusy||!isLatest())return;const record=current,identity=recordIdentity(record),session=sessionEpoch;
 actionBusy=true;updateControls();
 try{await api(`/admin/intakes/${record.id}/${action}`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({revision:record.revision||1,...body}),signal:AbortSignal.timeout(30000)});if(session!==sessionEpoch||recordIdentity(current)!==identity)return;draft?.cancel();if(action==='retake-request'&&$('retake-dialog').open)$('retake-dialog').close();await selectRecord(record.id,null,true);await refresh();}
 catch(error){if(session===sessionEpoch&&recordIdentity(current)===identity){const message=error.message||'저장하지 못했어요.';say(message,true);if(action==='retake-request')$('retake-error').textContent=message;}}
 finally{actionBusy=false;updateControls();}
}
$('apply-correction').onclick=()=>{if(!current||$('apply-correction').disabled)return;if(!window.confirm('새 결과 버전으로 저장하고 재계산할까요? 이전 결과는 보존되고 검토 상태는 초기화됩니다.'))return;mutateRecord('recalculate',{overrides:draft?.value||{}});};
$('mark-reviewed').onclick=()=>mutateRecord('review',{acknowledged:$('review-ack').checked});
$('request-retake').onclick=()=>{const views=['top','side'].filter(v=>$('retake-'+v).checked),reason=$('retake-reason').value.trim();if(!views.length||!reason){$('retake-error').textContent='재촬영할 사진과 이유를 입력해 주세요.';return;}$('retake-error').textContent='';if(!canLeave())return;mutateRecord('retake-request',{views,reason});};
window.addEventListener('beforeunload',event=>{if(draft?.dirty){event.preventDefault();event.returnValue='';}});

function fitPhotos(){
 for(const view of ['top','side']){
  const viewport=$(view+'-scroll'),image=$(view+'-photo'),geometry=current?.result?.geometry?.[view];
  const size=fitPhotoSize(geometry?.width||image.naturalWidth,geometry?.height||image.naturalHeight,viewport.clientWidth-32,viewport.clientHeight-32,Number($('photo-zoom').value)||1);
  if(!size)continue;const stage=$(view+'-stage');stage.style.width=size.width+'px';stage.style.height=size.height+'px';
 }
}
function setReviewStep(step){
 activeStep=['photo','dimensions','review'].includes(step)?step:'photo';
 const info=reviewStage(activeStep);
 for(const which of ['photo','dimensions','review']){
  const button=$('review-step-'+which);button.classList.toggle('active',which===activeStep);if(which===activeStep)button.setAttribute('aria-current','step');else button.removeAttribute('aria-current');
 }
 $('photo-check-panel').hidden=activeStep!=='photo';$('dimensions-panel').hidden=activeStep!=='dimensions';$('final-review-panel').hidden=activeStep!=='review';
 $('stage-kicker').textContent=String(info.index+1).padStart(2,'0')+' / '+({photo:'사진 확인',dimensions:'치수 대조',review:'검토 완료'})[activeStep];
 $('stage-title').textContent=info.title;
 $('stage-description').textContent=({photo:'현재 사진의 크기 기준과 손목 위치를 대조하세요.',dimensions:'항목을 선택해 사진의 실제 측정 위치와 대조하세요.',review:'현재 버전의 검토 결과와 필요한 자료를 정리하세요.'})[activeStep];
 for(const view of ['top','side'])drawGeometry(view);updateControls();fitPhotos();
}
function updateTaskActions(){
 const info=reviewStage(activeStep),dirty=!!draft?.dirty,legacy=!!current&&!current.result?.geometry&&['complete','failed'].includes(current.status);
 const saving=dirty||legacy;
 $('open-retake-photo').hidden=activeStep!=='photo';$('open-retake-photo').disabled=!isLatest()||actionBusy||['queued','processing'].includes(current?.status);$('open-retake-review').disabled=$('open-retake-photo').disabled;
 $('workflow-back').hidden=info.index===0;$('workflow-back').disabled=actionBusy;
 $('workflow-next').hidden=info.next===null||saving;$('workflow-next').disabled=actionBusy;
 $('workflow-next').textContent=info.action+' →';
 $('apply-correction').hidden=!saving;$('mark-reviewed').hidden=activeStep!=='review'||saving;
 $('task-action-title').textContent=dirty?'보정한 위치가 아직 저장되지 않았습니다':legacy?'이전 결과에는 선 좌표가 없습니다':current?.review_status==='reviewed'?'이 버전 검토 완료':info.title;
 $('task-action-note').textContent=dirty?'새 버전으로 재계산하면 치수와 보고서가 갱신됩니다.':legacy?'원본을 다시 계산하면 사진 위 측정선을 선택할 수 있습니다.':current?.review_status==='reviewed'?'자료 받기에서 이 버전의 측정표와 치수를 저장하세요.':activeStep==='review'?'사진과 치수를 대조한 뒤 확인 항목에 체크하세요.':activeStep==='dimensions'?'누락된 값이나 잘못된 측정선은 사진 확인에서 보정하세요.':'사진이 잘렸거나 자세가 다르면 재촬영을 요청하세요.';
 $('guide-title').textContent=draft?.kind==='wrist'?'손목 기준점 · 0번':'카드 85.6 × 54mm';
 $('guide-description').textContent=draft?.kind==='wrist'?'손과 팔이 만나는 손목 기준점을 사진에서 한 번 선택하세요. 선택 후 11개 치수를 다시 계산합니다.':'카드 네 모서리를 둘레 순서로 선택하세요. 손과 겹치지 않은 규격 카드를 기준으로 배율을 계산합니다.';
 $('card-point-guide').hidden=draft?.kind==='wrist';
 $('cancel-correction').disabled=!dirty&&!draft?.kind;
 $('correction-view').disabled=actionBusy;
}
for(const step of ['photo','dimensions','review'])$('review-step-'+step).onclick=()=>setReviewStep(step);
$('workflow-next').onclick=()=>{const next=reviewStage(activeStep).next;if(next)setReviewStep(next);};
$('workflow-back').onclick=()=>{const stages=['photo','dimensions','review'];setReviewStep(stages[Math.max(0,reviewStage(activeStep).index-1)]);};
$('correction-view').onchange=()=>showResultView($('correction-view').value);
function openRetake(){if(!current||!isLatest()||actionBusy||!canLeave())return;$('retake-error').textContent='';$('retake-dialog').showModal();}
$('open-retake-photo').onclick=openRetake;$('open-retake-review').onclick=openRetake;$('cancel-retake').onclick=()=>$('retake-dialog').close();
window.addEventListener('resize',fitPhotos);
if(typeof ResizeObserver!=='undefined'){const observer=new ResizeObserver(fitPhotos);for(const view of ['top','side'])observer.observe($(view+'-scroll'));}
