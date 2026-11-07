// All coordinates are decoded original image pixels, never CSS pixels.
export function imagePoint(event,rect,geometry){
 if(!geometry||rect.width<=0||rect.height<=0)return null;
 const x=(event.clientX-rect.left)/rect.width*geometry.width,y=(event.clientY-rect.top)/rect.height*geometry.height;
 return Number.isFinite(x)&&Number.isFinite(y)&&x>=0&&y>=0&&x<geometry.width&&y<geometry.height?[x,y]:null;
}
export const recordIdentity=record=>record?`${record.id}:v${record.revision||1}`:'';
const copy=value=>JSON.parse(JSON.stringify(value));
export class CorrectionDraft{
 constructor(base={}){this.base=copy(base);this.value=copy(base);this.points=[];this.steps=[];this.view=null;this.kind=null;}
 start(view,kind){this.view=view;this.kind=kind;this.points=[];this.steps=[];}
 add(point){if(!this.kind)return;this.steps.push({value:copy(this.value),points:copy(this.points)});if(this.points.length===(this.kind==='card'?4:1))this.points=[];this.points.push([...point]);if(this.points.length===(this.kind==='card'?4:1))this.value[this.kind+'_'+this.view]=copy(this.kind==='card'?this.points:this.points[0]);}
 undo(){const previous=this.steps.pop();if(previous){this.value=previous.value;this.points=previous.points;}}
 automatic(){if(this.kind){delete this.value[this.kind+'_'+this.view];this.points=[];this.kind=null;}}
 cancel(){this.value=copy(this.base);this.points=[];this.kind=null;this.steps=[];}
 get dirty(){return this.points.length>0||JSON.stringify(this.value)!==JSON.stringify(this.base);}
 get canApply(){return this.dirty&&(!this.kind||this.points.length===(this.kind==='card'?4:1));}
}
