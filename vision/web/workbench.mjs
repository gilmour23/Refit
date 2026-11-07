export function fitPhotoSize(imageWidth,imageHeight,availableWidth,availableHeight,zoom=1){
 if(![imageWidth,imageHeight,availableWidth,availableHeight,zoom].every(v=>Number.isFinite(v)&&v>0))return null;
 const scale=Math.min(availableWidth/imageWidth,availableHeight/imageHeight)*zoom;
 return {width:imageWidth*scale,height:imageHeight*scale};
}
const stages={
 photo:{index:0,title:'사진과 기준점 확인',next:'dimensions',action:'치수 대조로 이동'},
 dimensions:{index:1,title:'11개 치수 대조',next:'review',action:'최종 검토로 이동'},
 review:{index:2,title:'검토 결과 정리',next:null,action:'이 버전 검토 완료'},
};
export const reviewStage=step=>stages[step]||stages.photo;
