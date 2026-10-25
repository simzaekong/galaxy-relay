// Vertical scrolling and pinch zoom remain native. Only a deliberate left swipe deletes.
export function swipeCard(front,{tap,remove}){
  let start,dragging=false,suppressUntil=0,busy=false;
  const reset=()=>{front.classList.remove('swiping');front.style.removeProperty('--swipe');start=null;dragging=false;};
  async function erase(){if(busy)return;busy=true;front.setAttribute('aria-busy','true');try{await remove();}finally{busy=false;front.removeAttribute('aria-busy');reset();}}
  front.addEventListener('pointerdown',e=>{if(busy||!e.isPrimary||e.button!==0)return;start={x:e.clientX,y:e.clientY,id:e.pointerId,dx:0};});
  front.addEventListener('pointermove',e=>{if(!start||start.id!==e.pointerId)return;const dx=e.clientX-start.x,dy=e.clientY-start.y;if(!dragging){if(Math.abs(dy)>10&&Math.abs(dy)>Math.abs(dx)){suppressUntil=performance.now()+400;start=null;return;}if(dx<-10&&Math.abs(dx)>Math.abs(dy)*1.3){dragging=true;front.setPointerCapture(e.pointerId);front.classList.add('swiping');}}if(dragging){e.preventDefault();start.dx=Math.min(0,dx);front.style.setProperty('--swipe',`${Math.max(start.dx,-front.clientWidth)}px`);}});
  front.addEventListener('pointerup',()=>{if(!start)return;const eraseNow=dragging&&-start.dx>=Math.max(72,front.clientWidth*.25);if(dragging)suppressUntil=performance.now()+400;if(eraseNow)void erase();else reset();});
  front.addEventListener('pointercancel',reset);
  front.addEventListener('click',e=>{if(busy||performance.now()<suppressUntil){e.preventDefault();e.stopPropagation();return;}void tap();});
  front.addEventListener('keydown',e=>{if(e.key==='Delete'){e.preventDefault();void erase();}else if(front.tagName!=='BUTTON'&&['Enter',' '].includes(e.key)){e.preventDefault();if(!busy)void tap();}});
  front.setAttribute('aria-keyshortcuts','Delete');front.setAttribute('aria-description','왼쪽으로 밀거나 Delete 키로 Galaxy 알림과 기록을 지웁니다.');
}
