export function fitPhotoSize(imageWidth,imageHeight,availableWidth,availableHeight,zoom=1){
 if(![imageWidth,imageHeight,availableWidth,availableHeight,zoom].every(v=>Number.isFinite(v)&&v>0))return null;
 const scale=Math.min(availableWidth/imageWidth,availableHeight/imageHeight)*zoom;
 return {width:imageWidth*scale,height:imageHeight*scale};
}
