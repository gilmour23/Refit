import {parseMeasurement,freshSignal,appendSamples,readBoundedJson} from './core.mjs';
import {BleReader} from './bluetooth.mjs';
const $=id=>document.getElementById(id);
let key='',authorized=false,busy=false,requestController=null,measurement=null,brace=null,reportUrl=null;
const photos={top:null,side:null},previews={top:null,side:null};
const signal={connected:false,lastAt:null,values:[]};let sampleCount=0,bleKind='disconnected';
let session=null;const records=[];
function message(id,text,error=false){$(id).textContent=text;$(id).classList.toggle('error',error);}
function tab(name){for(const e of document.querySelectorAll('.tab'))e.hidden=e.id!==name;for(const e of document.querySelectorAll('[data-tab]'))e.classList.toggle('active',e.dataset.tab===name);}
document.querySelectorAll('[data-tab],[data-go]').forEach(e=>e.addEventListener('click',()=>tab(e.dataset.tab||e.dataset.go)));
function syncButtons(){$('measure').disabled=busy||!authorized||!photos.top||!photos.side;for(const id of ['top-file','side-file'])$(id).disabled=busy;$('cancel-measure').hidden=!busy;$('register').disabled=!measurement||(measurement.warnings.length>0&&!$('warning-confirm').checked);}
function clearResult(){measurement=null;if(reportUrl)URL.revokeObjectURL(reportUrl);reportUrl=null;$('report').removeAttribute('src');$('report-link').removeAttribute('href');$('result').hidden=true;$('warning-confirm').checked=false;syncButtons();}
for(const which of ['top','side']) $(which+'-file').addEventListener('change',async event=>{
 clearResult();const file=event.target.files[0];photos[which]=null;if(previews[which])URL.revokeObjectURL(previews[which]);$(which+'-preview').hidden=true;
 if(!file){$(which+'-name').textContent='원본 사진 선택';syncButtons();return;}
 if(!file.size||file.size>20*1024*1024){message('photo-message','사진 한 장은 20MB 이하여야 해요.',true);event.target.value='';syncButtons();return;}
 photos[which]=file;$(which+'-name').textContent=file.name;previews[which]=URL.createObjectURL(file);$(which+'-preview').src=previews[which];$(which+'-preview').onerror=()=>{$(which+'-preview').hidden=true;};$(which+'-preview').hidden=false;message('photo-message','');syncButtons();
});
function openSettings(){$('settings').showModal();$('api-key').value=key;}
$('settings-open').onclick=openSettings;
async function checkApi(){
 const candidate=$('api-key').value.trim();
 if(!/^[A-Za-z0-9_-]{32,256}$/.test(candidate)){message('settings-message','노트북 시작 화면의 연결키를 그대로 입력해 주세요.',true);return;}
 $('check-api').disabled=true;message('settings-message','서버 확인 중…');
 try{const response=await fetch('/demo/check',{method:'POST',headers:{Authorization:`Bearer ${candidate}`},redirect:'error',cache:'no-store',signal:AbortSignal.timeout(15000)});const j=await readBoundedJson(response,8192);if(!response.ok)throw Error(j.detail||'연결키와 서버 상태를 확인해 주세요.');if(!j.model_ready)throw Error('서버에 손 인식 모델이 없어요. 노트북 설치 상태를 확인해 주세요.');key=candidate;authorized=true;$('api-status').textContent='측정 서버 연결됨';$('api-led').classList.add('ready');message('settings-message','연결됐어요. 사진을 선택해 분석할 수 있어요.');$('settings').close();syncButtons();}
 catch(e){key='';authorized=false;$('api-status').textContent='측정 서버 연결 확인 필요';$('api-led').classList.remove('ready');message('settings-message',e.message||'서버에 연결하지 못했어요.',true);syncButtons();}finally{$('check-api').disabled=false;}
}
$('check-api').onclick=checkApi;
$('measure').onclick=async()=>{
 clearResult();busy=true;syncButtons();requestController=new AbortController();const timeout=setTimeout(()=>requestController?.abort(),90000);message('photo-message','카드와 손을 분석하는 중이에요. 잠시 기다려 주세요.');
 const form=new FormData();for(const which of ['top','side'])form.append(which+'_image',photos[which],which+'-original');form.append('session_id','W'+crypto.randomUUID().replaceAll('-',''));form.append('return_report','true');
 try{const response=await fetch('/measure_brace',{method:'POST',headers:{Authorization:`Bearer ${key}`},body:form,signal:requestController.signal,redirect:'error',cache:'no-store'});if(response.status===401||response.status===403){authorized=false;throw Error('연결키를 다시 확인해 주세요.');}const j=await readBoundedJson(response);if(!response.ok)throw Error(j.detail||`측정 서버 오류 (${response.status})`);const result=parseMeasurement(j);
 const bytes=Uint8Array.from(atob(result.report),c=>c.charCodeAt(0));reportUrl=URL.createObjectURL(new Blob([bytes],{type:'image/jpeg'}));
 await new Promise((resolve,reject)=>{$('report').onload=resolve;$('report').onerror=()=>reject(Error('측정 보고서를 표시하지 못했어요.'));$('report').src=reportUrl;});
 measurement=result;$('report-link').href=reportUrl;$('dimensions').replaceChildren(...result.dimensions.map(d=>{const row=document.createElement('div'),name=document.createElement('dt'),value=document.createElement('dd');name.textContent=`${d.id}. ${d.name}`;value.textContent=`${d.value.toFixed(1)} mm`;row.append(name,value);return row;}));
 $('warnings').replaceChildren(...result.warnings.map(t=>{const li=document.createElement('li');li.textContent=t;return li;}));$('warning-confirm-row').hidden=!result.warnings.length;$('result').hidden=false;message('photo-message','측정 위치와 치수를 보고 확인해 주세요.');
 }catch(e){clearResult();message('photo-message',e.name==='AbortError'?'분석을 취소했거나 대기 시간이 지났어요.':e.message||'분석하지 못했어요.',true);}finally{clearTimeout(timeout);requestController=null;busy=false;syncButtons();}
};
$('cancel-measure').onclick=()=>requestController?.abort();$('warning-confirm').onchange=syncButtons;
$('clear-photo').onclick=()=>{clearResult();message('photo-message','원본 사진을 다시 선택하거나 같은 사진으로 분석해 주세요.');};
$('register').onclick=()=>{if($('register').disabled)return;brace={session:measurement.session,dimensions:measurement.dimensions};$('brace-status').textContent=`사진에서 측정한 11개 치수 등록 · ${brace.session}`;tab('exercise');};
const ble=new BleReader({onState:state=>{bleKind=state.kind;signal.connected=state.kind==='subscribed';signal.values=[];signal.lastAt=null;message('ble-message',state.label,state.kind==='error');$('connect-ble').disabled=['selecting','connecting','subscribed'].includes(state.kind);},onSamples:values=>{if(!signal.connected)return;appendSamples(signal,values);sampleCount+=values.length;$('sample-count').textContent=String(sampleCount);}});
const supported=!!navigator.bluetooth&&window.isSecureContext;
$('browser-badge').textContent=supported?'BLE 지원 감지':'BLE 브라우저 필요';$('browser-help').textContent=supported?'이 브라우저에서 BLE 기기를 선택할 수 있어요. 펌웨어 팀의 UUID를 입력해 주세요.':'사진 측정은 이 브라우저에서 사용할 수 있어요. 센서 연결은 갤럭시 Chrome, 아이폰·아이패드 Bluefy에서 HTTPS 주소로 열어주세요.';
for(const id of ['service-uuid','notify-uuid']){$(id).value=localStorage.getItem('refit-'+id)||'';}
$('connect-ble').onclick=()=>{const service=$('service-uuid').value.trim(),notify=$('notify-uuid').value.trim();ble.connect(service,notify,$('format-confirm').checked).then(()=>{localStorage.setItem('refit-service-uuid',service);localStorage.setItem('refit-notify-uuid',notify);}).catch(e=>message('ble-message',e.message,true));};
$('disconnect-ble').onclick=()=>ble.disconnect();
function drawGraph(values){const canvas=$('sensor-graph'),context=canvas.getContext('2d'),w=canvas.width=canvas.clientWidth*devicePixelRatio,h=canvas.height=190*devicePixelRatio;context.clearRect(0,0,w,h);context.strokeStyle='#ccdcd5';context.lineWidth=devicePixelRatio;context.beginPath();context.moveTo(0,h/2);context.lineTo(w,h/2);context.stroke();if(!values.length)return;const bound=Math.max(1,...values.map(Math.abs));context.strokeStyle='#226d65';context.lineWidth=2*devicePixelRatio;context.beginPath();values.forEach((v,i)=>{const x=i*w/Math.max(1,values.length-1),y=h/2-v/bound*h*.42;i?context.lineTo(x,y):context.moveTo(x,y);});context.stroke();}
function exerciseButtons(running){$('start-exercise').disabled=running;$('pause-exercise').disabled=!running;$('stop-exercise').disabled=!running;}
function finishExercise(){if(!session)return;const record={elapsedSeconds:Math.round((performance.now()-session.started)/1000),sampledSeconds:Math.round(session.samples*.1),rmsMean:session.samples?session.sum/session.samples:null,source:'real_ble',measurementSession:brace?.session||null};records.push(record);$('exercise-result').hidden=false;$('exercise-result').textContent=record.rmsMean==null?'운동 안내를 마쳤어요. 실제 센서 수신이 없어 측정값은 기록하지 않았어요.':`운동 안내 완료 · 실제 수신 ${record.sampledSeconds}초 · 평균 원시 RMS ${record.rmsMean.toFixed(1)} (보정 전)`;session=null;exerciseButtons(false);$('phase').textContent='운동 안내 완료';$('countdown').textContent='—';}
$('start-exercise').onclick=()=>{session={started:performance.now(),active:0,paused:false,sum:0,samples:0,last:performance.now()};$('exercise-result').hidden=true;$('pause-exercise').textContent='일시정지';exerciseButtons(true);};
$('pause-exercise').onclick=()=>{if(!session)return;session.paused=!session.paused;session.last=performance.now();$('pause-exercise').textContent=session.paused?'계속하기':'일시정지';};$('stop-exercise').onclick=finishExercise;
setInterval(()=>{const now=performance.now(),live=freshSignal(signal,now);$('rms').textContent=live?live.rms.toFixed(1):'—';$('raw').textContent=live?String(live.latest):'—';$('exercise-rms').textContent=live?live.rms.toFixed(1):'—';$('exercise-data').textContent=live?'실제 센서 · 원시 RMS':'실제 센서 데이터 대기';$('signal-status').textContent=live?'실제 알림 수신 중 · 원시값 / 화면 자동 스케일':bleKind==='subscribed'?'연결됐지만 최근 2초간 수신한 데이터가 없어요.':'실제 센서 데이터 대기 중';drawGraph(live?signal.values:[]);
 if(session){const delta=Math.min(now-session.last,500);session.last=now;if(!session.paused){session.active+=delta;if(live){session.sum+=live.rms;session.samples++;}}const elapsed=session.active/1000,round=Math.floor(elapsed/10)+1,attempt=elapsed%10<4; if(elapsed>=30){finishExercise();return;}$('phase').textContent=session.paused?'잠시 쉬는 중':attempt?'손목을 펴 보세요':'편하게 쉬세요';$('countdown').textContent=String(Math.ceil((attempt?4:10)-elapsed%10));$('round').textContent=`${round} / 3회 · 시도 4초 / 휴식 6초`;}
},100);
document.addEventListener('visibilitychange',()=>{if(document.hidden&&session){session.paused=true;$('pause-exercise').textContent='계속하기';}});
$('reset-session').onclick=()=>{if(busy){message('photo-message','분석을 먼저 취소해 주세요.',true);return;}ble.disconnect();session=null;records.length=0;brace=null;sampleCount=0;$('sample-count').textContent='0';exerciseButtons(false);clearResult();for(const which of ['top','side']){photos[which]=null;$(which+'-file').value='';$(which+'-name').textContent='원본 사진 선택';$(which+'-preview').hidden=true;if(previews[which])URL.revokeObjectURL(previews[which]);previews[which]=null;}$('exercise-result').hidden=true;$('brace-status').textContent='사진 측정 치수를 등록하면 여기에 표시돼요.';$('phase').textContent='운동 준비';$('countdown').textContent='—';message('photo-message','이 페이지의 참가자 데이터와 사진을 비웠어요.');tab('photo');syncButtons();};
const fragment=new URLSearchParams(location.hash.slice(1));const joined=fragment.get('key');if(location.hash)history.replaceState(null,'',location.pathname);if(joined){$('api-key').value=joined;checkApi();}
window.addEventListener('pagehide',()=>{ble.disconnect();requestController?.abort();});
syncButtons();
