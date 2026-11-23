// Детерминированный движок: всё состояние кадра считается из времени t (секунды).
const clamp=(x,a=0,b=1)=>Math.max(a,Math.min(b,x));
const E={
  out:p=>1-Math.pow(1-p,3),
  inout:p=>p<.5?4*p*p*p:1-Math.pow(-2*p+2,3)/2,
  back:p=>{const c1=2.2,c3=c1+1;return 1+c3*Math.pow(p-1,3)+c1*Math.pow(p-1,2)},
  elastic:p=>p===0?0:p===1?1:Math.pow(2,-10*p)*Math.sin((p*10-.75)*(2*Math.PI)/3)+1,
};
function rnd(seed){let s=seed>>>0||1;return()=>(s=(s*1664525+1013904223)>>>0)/4294967296}
const fmt=n=>Math.round(n).toString().replace(/\B(?=(\d{3})+(?!\d))/g,' ');

const PALETTES={
  violet:{a1:'#9b5cff',a2:'#ff5cc8',a3:'#5ce1ff',bg:['#1a0b3d','#3d0b4a','#0b1e4a']},
  neon:{a1:'#00f0ff',a2:'#ff2bd6',a3:'#b4ff39',bg:['#00243a','#3a0036','#102a00']},
  gold:{a1:'#ffb627',a2:'#ff6b3d',a3:'#ffe08a',bg:['#3a2200','#3a0f00','#2a1a05']},
  emerald:{a1:'#19e3a1',a2:'#3dd6ff',a3:'#c6ff6b',bg:['#00301f','#002a3a','#123300']},
  sunset:{a1:'#ff4d6d',a2:'#ff9e40',a3:'#ffd166',bg:['#3d0718','#3d1d00','#2d0a3a']},
  danger:{a1:'#ff3b5c',a2:'#9b5cff',a3:'#ff8fa3',bg:['#3a0010','#1f0636','#2a0008']},
};

let V,scenes=[],HITS=[],DUR=0,ctx,stars=[];
function setPalette(name){
  const P=PALETTES[name];V._pal=P;const r=document.documentElement.style;
  r.setProperty('--a1',P.a1);r.setProperty('--a2',P.a2);r.setProperty('--a3',P.a3);
  r.setProperty('--grad',`linear-gradient(120deg,${P.a1},${P.a2})`);
  r.setProperty('--glow',P.a1+'66');r.setProperty('--chatbg',`linear-gradient(180deg,#0f0b1c,#150f26)`);
}
function build(v){
  V=v;setPalette(v.palette||'violet');
  const st=document.getElementById('stage');
  let t=0;
  for(const s of v.scenes){
    const el=document.createElement('div');el.className='scene '+(s.cls||'');el.innerHTML=s.html;
    st.appendChild(el);
    const sc={el,start:t,d:s.d,tr:s.tr||'punch',pal:s.pal,anims:[...el.querySelectorAll('[data-a]')]};
    if(s.hit!==false)HITS.push({t,k:s.k||'impact'});
    for(const a of sc.anims){
      if(a.dataset.a==='type')a.dataset.text=a.textContent;
      if(a.dataset.hit!==undefined)HITS.push({t:t+(+a.dataset.t||0)+(+(a.dataset.hit)||0),k:a.dataset.hk||'hit'});
    }
    scenes.push(sc);t+=s.d;
  }
  DUR=t;
  const R=rnd(v.seed||7);
  for(let i=0;i<110;i++)stars.push({x:R()*540,y:R()*960,r:R()*1.4+.3,s:R()*3+1,ph:R()*6.28,v:R()*14+4});
  ctx=document.getElementById('bg').getContext('2d');
  window.META={dur:DUR,hits:HITS.sort((a,b)=>a.t-b.t),music:v.music,fps:30};
}
function bg(t){
  const P=V._pal,c=ctx;c.fillStyle='#06030d';c.fillRect(0,0,540,960);
  const beat=60/(V.music.bpm||120);const bp=Math.pow(1-((t%beat)/beat),3)*(t>=(V.music.dropAt||0)?1:.25);
  const blobs=[[P.bg[0],270+170*Math.sin(t*.37),300+140*Math.cos(t*.29),460],[P.bg[1],270+190*Math.cos(t*.31+1),700+120*Math.sin(t*.41),480],[P.bg[2],270+150*Math.sin(t*.23+2),520+200*Math.cos(t*.19+1),420]];
  c.globalCompositeOperation='lighter';
  for(const [col,x,y,r] of blobs){const g=c.createRadialGradient(x,y,0,x,y,r*(1+.08*bp));g.addColorStop(0,col);g.addColorStop(1,'rgba(0,0,0,0)');c.fillStyle=g;c.fillRect(0,0,540,960)}
  const g2=c.createRadialGradient(270,480,0,270,480,520);g2.addColorStop(0,P.a1+Math.round(18+40*bp).toString(16).padStart(2,'0'));g2.addColorStop(1,'rgba(0,0,0,0)');c.fillStyle=g2;c.fillRect(0,0,540,960);
  for(const s of stars){const y=(s.y-t*s.v+960*10)%960;const a=.25+.6*Math.abs(Math.sin(t*s.s+s.ph));c.fillStyle=`rgba(255,255,255,${a})`;c.beginPath();c.arc(s.x,y,s.r,0,6.283);c.fill()}
  c.globalCompositeOperation='source-over';
  const v=c.createRadialGradient(270,480,300,270,480,700);v.addColorStop(0,'rgba(0,0,0,0)');v.addColorStop(1,'rgba(0,0,0,.65)');c.fillStyle=v;c.fillRect(0,0,540,960);
}
function anim(el,lt){
  const a=el.dataset.a,t0=+el.dataset.t||0,d=+el.dataset.d||.45,o=el.dataset.o!==undefined?+el.dataset.o:null;
  const raw=(lt-t0)/d,p=clamp(raw);let tr='',op=1,f='';
  el.style.setProperty('--p',E.out(p).toFixed(4));
  switch(a){
    case 'pop':{const s=p<=0?0:E.back(p);tr=`scale(${s})`;op=p>0?1:0;break}
    case 'slam':{const q=E.out(p);tr=`scale(${2.6-1.6*q}) rotate(${(1-q)*-6}deg)`;op=clamp(p*3);break}
    case 'up':{const q=E.out(p);tr=`translateY(${(1-q)*50}px)`;op=q;break}
    case 'down':{const q=E.out(p);tr=`translateY(${(q-1)*50}px)`;op=q;break}
    case 'left':{const q=E.out(p);tr=`translateX(${(q-1)*120}px)`;op=q;break}
    case 'right':{const q=E.out(p);tr=`translateX(${(1-q)*120}px)`;op=q;break}
    case 'rise':{const q=E.back(p);tr=`translateY(${(1-q)*700}px) rotate(${(1-q)*8}deg)`;op=p>0?1:0;break}
    case 'blur':{const q=E.out(p);f=`blur(${(1-q)*24}px)`;tr=`scale(${1.25-.25*q})`;op=q;break}
    case 'fade':{op=E.out(p);break}
    case 'msg':{const q=E.back(p);const side=el.classList.contains('out')?1:-1;tr=`translate(${side*(1-q)*40}px,${(1-q)*24}px) scale(${.6+.4*q})`;op=clamp(p*2.5);el.style.transformOrigin=side>0?'100% 100%':'0 100%';if(p<=0){el.style.display='none';return}el.style.display='';break}
    case 'type':{const txt=el.dataset.text,cps=+el.dataset.cps||22;const n=clamp(Math.floor((lt-t0)*cps),0,txt.length);el.textContent=txt.slice(0,n)+(n<txt.length&&lt>=t0&&(Math.floor(lt*3)%2===0)?'|':'');op=lt>=t0-.001||el.dataset.keep?1:0;if(el.dataset.keep)op=1;break}
    case 'count':{const from=+el.dataset.from||0,to=+el.dataset.to;el.textContent=(el.dataset.pre||'')+fmt(from+(to-from)*E.inout(p))+(el.dataset.suf||'');op=lt>=t0?1:0;break}
    case 'cls':{el.classList.toggle(el.dataset.c||'on',lt>=t0);return}
    case 'show':{op=lt>=t0?1:0;break}
    case 'var':{return}
    case 'float':{tr=`translateY(${Math.sin(lt*2.2+t0)*8}px) rotate(${Math.sin(lt*1.6+t0)*3}deg)`;break}
    case 'pulse':{const b=60/V.music.bpm;const q=Math.pow(1-((lt%b)/b),4);tr=`scale(${1+.06*q})`;break}
    case 'spinin':{const q=E.back(p);tr=`scale(${q}) rotate(${(1-q)*-180}deg)`;op=p>0?1:0;break}
    case 'shakeon':{if(lt>=t0&&lt<t0+d){const k=1-(lt-t0)/d;tr=`translate(${Math.sin(lt*90)*10*k}px,${Math.cos(lt*77)*6*k}px)`}break}
    case 'fly':{const q=E.inout(p);tr=`translate(${(+el.dataset.x||0)*q}px,${(+el.dataset.y||0)*q}px) scale(${1-.7*q})`;op=1-q*q;break}
  }
  if(o!==null&&lt>=o){const q=clamp((lt-o)/(+el.dataset.od||.3));op*=1-q;tr+=` scale(${1-.4*q})`;
    if(el.dataset.collapse!==undefined){el.style.maxHeight=(1-q)*120+'px';el.style.marginTop=(-7*q)+'px';el.style.padding=q>=1?'0':''} }
  el.style.transform=tr;el.style.opacity=op;el.style.filter=f;
}
function seek(t){
  bg(t);
  let shake=0,flash=0;
  for(const h of HITS){const dt=t-h.t;if(dt>=0&&dt<.35){const k=1-dt/.35;shake=Math.max(shake,k*(h.k==='impact'?1:.6));flash=Math.max(flash,Math.pow(k,3)*(h.k==='impact'?.55:.35))}}
  for(const s of scenes){
    const lt=t-s.start,on=lt>=0&&lt<s.d||(s===scenes[scenes.length-1]&&lt>=0);
    s.el.style.display=on?'flex':'none';if(!on)continue;
    if(s.pal)setPalette(s.pal);
    const q=clamp(lt/.32);let tr='',f='';
    if(s.tr==='punch'){const e=E.out(q);tr=`scale(${1.18-.18*e})`;f=`blur(${(1-e)*10}px)`}
    else if(s.tr==='whip'){const e=E.out(q);tr=`translateY(${(1-e)*320}px)`;f=`blur(${(1-e)*14}px)`}
    else if(s.tr==='zoomout'){const e=E.out(q);tr=`scale(${.6+.4*e})`;f=`blur(${(1-e)*10}px)`}
    else if(s.tr==='glitch'){if(lt<.22){tr=`translateX(${Math.sin(lt*200)*14}px) skewX(${Math.sin(lt*130)*8}deg)`;f=`hue-rotate(${lt*2000}deg)`}}
    const drift=1+lt*.012;tr+=` scale(${drift})`;
    s.el.style.transform=tr;s.el.style.filter=f;
    for(const a of s.anims)anim(a,lt);
  }
  const st=document.getElementById('stage');
  st.style.transform=shake?`translate(${Math.sin(t*97)*9*shake}px,${Math.cos(t*83)*9*shake}px) rotate(${Math.sin(t*61)*.6*shake}deg)`:'';
  document.getElementById('flash').style.opacity=flash;
}
window.seek=seek;
