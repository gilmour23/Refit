const SLOT='refit-intake-session-v1',TTL=12*60*60*1000;
export function saveSession(storage,key,receipt,stamp=Date.now()){
 try{storage?.setItem(SLOT,JSON.stringify({key,receipt,stamp}));}catch{/* Private browsing may disable storage. */}
}
export function restoreSession(storage,stamp=Date.now()){
 try{const v=JSON.parse(storage?.getItem(SLOT)||'null');if(!v||!Number.isFinite(v.stamp)||stamp-v.stamp>TTL||stamp<v.stamp||!/^[A-Za-z0-9_-]{32,256}$/.test(v.key)||v.receipt&&(!/^[a-f0-9]{32}$/.test(v.receipt.id)||!/^[A-Za-z0-9_-]{32,256}$/.test(v.receipt.receipt)))return null;return v;}catch{return null;}
}
export function forgetSession(storage){try{storage?.removeItem(SLOT);}catch{}}
export function requestedViews(state){return state?.review_status==='retake_requested'&&Number.isInteger(state.revision)?['top','side'].filter(v=>state.retake_views?.includes(v)):[];}
