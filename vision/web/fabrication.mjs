// Existing measurement definitions grouped for fabrication review; no geometric conversion.
export const sections = [
  {label:'전완',location:'손목에서 팔 쪽 40mm',width:1,thickness:2},
  {label:'손목',location:'손목 기준점 · 0번 높이',width:3,thickness:4},
  {label:'엄지 기준 위치',location:'엄지 관절 · 2번 높이',width:8,thickness:10},
  {label:'검지 기준 위치',location:'검지 뿌리 · 5번 높이',width:9,thickness:11},
];
export const lengths = [
  {id:5,label:'새끼손가락 기준점 ↔ 손목',detail:'18번과 0번을 지나는 가로선 사이'},
  {id:6,label:'엄지 기준점 ↔ 손목',detail:'2번과 0번을 지나는 가로선 사이'},
  {id:7,label:'검지 기준점 ↔ 엄지 기준점',detail:'5번과 2번을 지나는 가로선 사이'},
];
export function dimensionValue(result,id){const v=result?.dims_mm?.[String(id)];return typeof v==='number'&&Number.isFinite(v)&&v>0?v:null;}
export function hasCompleteDimensions(record){return record?.status==='complete'&&Array.from({length:11},(_,i)=>i+1).every(id=>dimensionValue(record.result,id)!==null);}
export function measurementFilename(record,asset,extension){
  const safe=String(record.display_name||'사용자').normalize('NFC').replace(/[<>:"/\\|?*\u0000-\u001f]/g,'_').replace(/[. ]+$/g,'').slice(0,40)||'사용자';
  const titles={report:'측정표',csv:'치수',top_original:'손등원본',side_original:'측면원본'};
  return `ReFit_${safe}_${record.hand==='left'?'왼손':'오른손'}_${record.id}_${titles[asset]||'자료'}.${extension}`;
}
