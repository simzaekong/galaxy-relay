// One shared GPU context; only wallpaper pixels enter the optical renderer.
// Text, icons, credentials and notification contents remain ordinary DOM.
const vertex=`attribute vec2 position;varying vec2 uv;
void main(){uv=position*.5+.5;gl_Position=vec4(position,0.,1.);}`;
const fragment=`precision highp float;
varying vec2 uv;
uniform sampler2D wallpaper;
uniform vec2 size, origin, viewport, imageSize, light;
uniform float radius, darkness;
float shape(vec2 p){vec2 q=abs(p-size*.5)-(size*.5-radius);return length(max(q,0.))+min(max(q.x,q.y),0.)-radius;}
vec3 sampleWall(vec2 p){float cover=max(viewport.x/imageSize.x,viewport.y/imageSize.y);vec2 t=(p-viewport*.5)/(imageSize*cover)+.5;return texture2D(wallpaper,clamp(t,.001,.999)).rgb;}
void main(){
 vec2 p=vec2(uv.x,1.-uv.y)*size;
 float sd=shape(p),depth=-sd;
 float coverage=1.-smoothstep(-.7,.7,sd);
 if(coverage<=0.)discard;
 vec2 gradient=vec2(shape(p+vec2(.5,0.))-shape(p-vec2(.5,0.)),shape(p+vec2(0.,.5))-shape(p-vec2(0.,.5)));
 vec2 outward=gradient/max(length(gradient),.001);
 float bevel=1.-smoothstep(0.,20.,depth);
 // Convex surface normal and Snell refraction through a glass-like IOR.
 vec3 normal=normalize(vec3(outward*bevel*1.35,1.));
 vec3 ray=refract(vec3(0.,0.,-1.),normal,1./1.47);
 vec2 bend=ray.xy/max(-ray.z,.1)*30.;
 vec2 lens=size*.5+(p-size*.5)/1.028;
 lens+=outward*sin(depth*.28)*bevel*1.2;
 vec2 point=origin+lens;
 // Separate wavelengths very slightly at the curved rim.
 vec3 color=vec3(sampleWall(point+bend*1.035).r,sampleWall(point+bend).g,sampleWall(point+bend*.965).b);
 vec3 soft=(sampleWall(point+bend+vec2(1.8,0.))+sampleWall(point+bend-vec2(1.8,0.))+sampleWall(point+bend+vec2(0.,1.8))+sampleWall(point+bend-vec2(0.,1.8)))*.25;
 color=mix(color,soft,.48)*(1.-darkness);
 color=mix(color,vec3(.30,.28,.32),.13);
 vec3 lamp=normalize(vec3(light,.85));
 float spec=pow(max(dot(normal,normalize(lamp+vec3(0.,0.,1.))),0.),38.);
 float rim=exp(-max(depth,0.)*.75);
 float caustic=exp(-pow((depth-5.)/2.7,2.));
 float facing=dot(outward,normalize(light));
 color+=vec3(.98,.97,.94)*(spec*bevel*.38+rim*(.16+.36*max(facing,0.))+caustic*max(-facing,0.)*.20);
 color-=vec3(.12,.10,.09)*caustic*max(facing,0.)*.6;
 // Broad, low-opacity reflected light, rather than a uniform white outline.
 float sheen=pow(max(0.,1.-abs((p.x/size.x*.65+p.y/size.y*.35)-.27)*2.),7.);
 color+=vec3(.94,.96,1.)*sheen*.035;
 gl_FragColor=vec4(clamp(color,0.,1.),coverage);
}`;

const cards=new Set(),visible=new Set(),entries=new WeakMap();
const dark=matchMedia('(prefers-color-scheme: dark)'),reduced=matchMedia('(prefers-reduced-transparency: reduce)');
let renderer,loading,frame=0,lastDraw=0,settleUntil=0,failed=false;
function release(entry){if(entry.canvas){entry.canvas.width=entry.canvas.height=1;entry.canvas.remove();entry.canvas=null;}entry.node.classList.remove('optical-glass');}
function schedule(settle=0){if(settle)settleUntil=Math.max(settleUntil,performance.now()+settle);if(!frame&&!failed&&!reduced.matches&&document.visibilityState==='visible')frame=requestAnimationFrame(draw);}
function compile(gl,type,source){const shader=gl.createShader(type);gl.shaderSource(shader,source);gl.compileShader(shader);if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS)){gl.deleteShader(shader);throw new Error('GLASS_UNAVAILABLE');}return shader;}
async function prepare(){if(loading)return loading;loading=(async()=>{
 const surface=document.createElement('canvas'),gl=surface.getContext('webgl',{alpha:true,antialias:false,depth:false,stencil:false,premultipliedAlpha:false,preserveDrawingBuffer:true});
 if(!gl)throw new Error('GLASS_UNAVAILABLE');
 const image=new Image();image.src='/images/wallpaper.png';await image.decode();
 const program=gl.createProgram(),vs=compile(gl,gl.VERTEX_SHADER,vertex),fs=compile(gl,gl.FRAGMENT_SHADER,fragment);
 gl.attachShader(program,vs);gl.attachShader(program,fs);gl.linkProgram(program);gl.deleteShader(vs);gl.deleteShader(fs);
 if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw new Error('GLASS_UNAVAILABLE');gl.useProgram(program);
 const buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,-1,1,1,-1,1,1]),gl.STATIC_DRAW);
 const location=gl.getAttribLocation(program,'position');gl.enableVertexAttribArray(location);gl.vertexAttribPointer(location,2,gl.FLOAT,false,0,0);
 const texture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,texture);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,image);
 const uniforms=Object.fromEntries(['size','origin','viewport','imageSize','light','radius','darkness'].map(name=>[name,gl.getUniformLocation(program,name)]));
 gl.uniform2f(uniforms.imageSize,image.naturalWidth,image.naturalHeight);
 surface.addEventListener('webglcontextlost',event=>{event.preventDefault();failed=true;for(const entry of cards)release(entry);});
 surface.addEventListener('webglcontextrestored',()=>{renderer=null;loading=null;failed=false;schedule();});
 return {surface,gl,uniforms};
 })().catch(()=>{failed=true;for(const entry of cards)release(entry);return null;});return loading;}
async function draw(now){frame=0;
 if(reduced.matches||failed||document.visibilityState!=='visible')return;
 if(now-lastDraw<32){schedule();return;}lastDraw=now;
 if(!renderer){renderer=await prepare();if(renderer)schedule();return;}
 const {surface,gl,uniforms}=renderer,wall=document.querySelector('.wallpaper').getBoundingClientRect();
 const dpr=Math.min(devicePixelRatio||1,1.5);let painted=0;
 for(const entry of cards){if(!entry.node.isConnected){observer.unobserve(entry.node);resize.unobserve(entry.node);visible.delete(entry);cards.delete(entry);release(entry);}}
 for(const entry of visible){
  const box=entry.node.getBoundingClientRect();if(!box.width||!box.height||box.bottom<0||box.top>innerHeight||painted++>=16){release(entry);continue;}
  if(!entry.canvas){entry.canvas=document.createElement('canvas');entry.canvas.className='glass-optics';entry.canvas.setAttribute('aria-hidden','true');entry.node.prepend(entry.canvas);}
  const width=Math.ceil(box.width*dpr),height=Math.ceil(Math.min(box.height,800)*dpr);
  if(surface.width!==width||surface.height!==height){surface.width=width;surface.height=height;}
  gl.viewport(0,0,width,height);gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT);
  gl.uniform2f(uniforms.size,box.width,box.height);gl.uniform2f(uniforms.origin,box.left-wall.left,box.top-wall.top);gl.uniform2f(uniforms.viewport,wall.width,wall.height);
  gl.uniform2f(uniforms.light,entry.light[0],entry.light[1]);gl.uniform1f(uniforms.radius,Math.min(parseFloat(getComputedStyle(entry.node).borderTopLeftRadius)||26,box.height*.5));gl.uniform1f(uniforms.darkness,dark.matches ? .27 : .16);
  gl.drawArrays(gl.TRIANGLES,0,6);gl.flush();
  const canvas=entry.canvas;if(canvas.width!==width||canvas.height!==height){canvas.width=width;canvas.height=height;}
  const context=canvas.getContext('2d');context.clearRect(0,0,width,height);context.drawImage(surface,0,0);entry.node.classList.add('optical-glass');
 }
 if(now<settleUntil)schedule();
}
const observer=new IntersectionObserver(changes=>{for(const change of changes){const entry=entries.get(change.target);if(change.isIntersecting){visible.add(entry);}else{visible.delete(entry);release(entry);}}schedule();},{rootMargin:'80px'});
const resize=new ResizeObserver(()=>schedule());
resize.observe(document.querySelector('.shell'));resize.observe(document.getElementById('detail'));
// Rendering a fresh list must also release detached entries when WebGL is disabled.
new MutationObserver(()=>{for(const entry of cards)if(!entry.node.isConnected){observer.unobserve(entry.node);resize.unobserve(entry.node);visible.delete(entry);cards.delete(entry);release(entry);}}).observe(document.getElementById('notifications'),{childList:true});
export function mountGlass(node){if(entries.has(node))return;const entry={node,canvas:null,light:[-.6,-.8]};entries.set(node,entry);cards.add(entry);observer.observe(node);resize.observe(node);
 node.addEventListener('pointermove',event=>{const box=node.getBoundingClientRect();entry.light=[(event.clientX-box.left)/box.width*1.2-.6,(event.clientY-box.top)/box.height*1.2-.8];schedule(node.classList.contains('swiping')?220:0);});
 node.addEventListener('pointerleave',()=>{entry.light=[-.6,-.8];schedule();});
 node.addEventListener('pointerup',()=>schedule(240));node.addEventListener('pointercancel',()=>schedule(240));
}
addEventListener('scroll',()=>schedule(),{passive:true});addEventListener('resize',()=>schedule(),{passive:true});
document.addEventListener('scroll',()=>schedule(),{passive:true,capture:true});
window.visualViewport?.addEventListener('resize',()=>schedule());window.visualViewport?.addEventListener('scroll',()=>schedule());
dark.addEventListener('change',()=>schedule());reduced.addEventListener('change',()=>{for(const entry of cards)release(entry);schedule();});
document.addEventListener('visibilitychange',()=>{if(document.visibilityState!=='visible'){cancelAnimationFrame(frame);frame=0;}else schedule();});
