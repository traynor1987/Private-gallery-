(()=>{
 const token=__NOVA_TOKEN__;
 if(location.origin!=='https://web.whatsapp.com')return JSON.stringify({state:'COMPATIBILITY_ERROR',reason:'UNEXPECTED_NAVIGATION'});
 if(window.__novaFav10?.token===token)return window.__novaFav10.peek();
 let state='CHECKING',reason='WAITING_INTERFACE',refs=null,attempted=null,pendingSince=0,timer=null,stopped=false,dirty=true;
 const style=document.createElement('style');style.id='nova-favourites-style';
 style.textContent='html[data-nova-favourites-checking] body{visibility:hidden!important}#nova-favourites-cover{visibility:visible!important;position:fixed;inset:0;z-index:2147483647;background:#111d32;color:#e5f4f1;display:flex;align-items:center;justify-content:center;font:16px system-ui}#nova-favourites-cover[hidden]{display:none!important}';
 document.documentElement.appendChild(style);
 const cover=document.createElement('div');cover.id='nova-favourites-cover';cover.setAttribute('role','status');cover.textContent='Nova Chat · checking Favourites…';
 (document.body||document.documentElement).appendChild(cover);
 function conceal(){if(!document.documentElement.hasAttribute('data-nova-favourites-checking'))document.documentElement.setAttribute('data-nova-favourites-checking','');cover.hidden=false;}
 function reveal(){document.documentElement.removeAttribute('data-nova-favourites-checking');cover.hidden=true;}
 function selected(node){const values=['aria-selected','aria-pressed'].filter(k=>node.hasAttribute(k)).map(k=>node.getAttribute(k));if(!values.length||values.some(v=>v!=='true'&&v!=='false')||new Set(values).size!==1)return null;return values[0]==='true';}
 function visible(node){return node.isConnected&&node.getClientRects().length>0&&!node.closest('[hidden],[aria-hidden="true"]');}
 const labels={fav:['Favourites','Favorites','Favourites filter','Favorites filter','Filter by favourites','Filter by favorites'],all:['All','All chats','All filter'],unread:['Unread','Unread filter','Filter by unread'],groups:['Groups','Groups filter']};
 function controls(root,kind){
  const names=labels[kind];const selector=names.map(x=>'[aria-label="'+x+'"],[title="'+x+'"]');
  const found=Array.from(root.querySelectorAll(selector.join(',')));
  // Text equality is permitted only inside a non-conversation filter group, never the chat list.
  const tests=names.map(x=>"normalize-space(.)='"+x+"' or .//span[normalize-space(text())='"+x+"']").join(' or ');
  const result=document.evaluate(".//*[self::button or @role='button' or @role='tab']["+tests+"]",root,null,XPathResult.ORDERED_NODE_SNAPSHOT_TYPE,null);
  for(let i=0;i<Math.min(24,result.snapshotLength);i++)found.push(result.snapshotItem(i));
  return [...new Set(found.map(n=>n.closest('button,[role=button],[role=tab]')).filter(Boolean))].filter(visible);
 }
 function locate(){
  const pane=document.querySelector('#pane-side');if(!pane)return null;
  const side=document.querySelector('#side');if(!side||!side.contains(pane))return null;
  const groups=[...side.querySelectorAll('[role=tablist],[data-testid="chat-list-filters"]'),...Array.from(side.children)].filter(g=>g!==pane&&!g.contains(pane)&&!pane.contains(g)&&!g.matches('[role=grid],[role=row],[role=listitem]')&&!g.querySelector('[role=grid],[role=row],[role=listitem],[contenteditable=true],[data-testid=msg-container]')&&g.querySelectorAll('button,[role=button],[role=tab]').length<=24);
  const valid=[];
  for(const group of groups.slice(0,24)){
   const fav=controls(group,'fav'),all=controls(group,'all'),unread=controls(group,'unread');
   if(fav.length===1&&all.length===1&&unread.length===1&&fav[0]!==all[0]&&fav[0]!==unread[0])valid.push({fav:fav[0],all:all[0],unread:unread[0],pane,group,side,peers:Array.from(group.querySelectorAll('button,[role=button],[role=tab]'))});
  }
  const distinct=valid.filter((r,i,a)=>a.findIndex(x=>x.fav===r.fav)===i);
  return distinct.length===1?distinct[0]:null;
 }
 function run(){
  if(stopped)return;
  if(location.origin!=='https://web.whatsapp.com'){state='COMPATIBILITY_ERROR';reason='UNEXPECTED_NAVIGATION';conceal();return;}
  if(dirty||!refs||!refs.fav.isConnected||!refs.all.isConnected||!refs.pane.isConnected||!refs.group.contains(refs.fav)||!refs.group.contains(refs.all)||refs.pane.contains(refs.fav)||document.getElementById('pane-side')!==refs.pane){refs=locate();dirty=false;}
  if(!refs){conceal();const pane=!!document.querySelector('#pane-side');const login=!pane&&!!document.querySelector('[data-testid="qrcode"],[data-testid="qr-code"],[data-ref] canvas');state=login?'LOGIN_REQUIRED':pane?'FILTER_UNAVAILABLE':'CHECKING';reason=login?'LOGIN_INTERFACE':pane?'FILTER_STRUCTURE_UNRECOGNISED':'WAITING_INTERFACE';return;}
  if(!visible(refs.fav)||selected(refs.fav)===null||selected(refs.all)===null){conceal();state='FILTER_UNAVAILABLE';reason='SELECTED_STATE_UNAVAILABLE';return;}
  if(selected(refs.fav)===true&&selected(refs.all)===false&&refs.peers.every(p=>p===refs.fav||selected(p)!==true)){state='FAVOURITES_ACTIVE';reason='SELECTED';pendingSince=0;attempted=null;reveal();return;}
  conceal();state='CHECKING';reason='ACTIVATION_PENDING';
  if(selected(refs.fav)===true&&refs.peers.some(p=>p!==refs.fav&&selected(p)===true)){state='COMPATIBILITY_ERROR';reason='CONFLICTING_SELECTION';return;}
  if(attempted!==refs.fav){attempted=refs.fav;pendingSince=Date.now();refs.fav.click();}
  if(selected(refs.fav)===true&&selected(refs.all)===false&&refs.peers.every(p=>p===refs.fav||selected(p)!==true)){state='FAVOURITES_ACTIVE';reason='SELECTED';pendingSince=0;attempted=null;reveal();}
  else if(pendingSince&&Date.now()-pendingSince>8000){state='COMPATIBILITY_ERROR';reason='ACTIVATION_NOT_CONFIRMED';}
 }
 function queue(){if(!timer&&!stopped)timer=setTimeout(()=>{timer=null;run();},120);}
 conceal();
 const options={subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['aria-selected','aria-pressed','aria-label','title','role']};
 const observer=new MutationObserver(records=>{
  if(stopped)return;
  const structural=!refs||!refs.fav.isConnected||!refs.all.isConnected||!refs.pane.isConnected||records.some(r=>refs.side.contains(r.target)&&!refs.pane.contains(r.target));
  if(structural)dirty=true;
  if(structural||selected(refs.fav)!==true||selected(refs.all)!==false){conceal();queue();}
 });
 observer.observe(document.documentElement,options);
 const unloading=()=>{state='CHECKING';reason='DOCUMENT_RELOADING';conceal();};
 window.addEventListener('beforeunload',unloading);
 const controller={token,peek(){if(stopped){stopped=false;dirty=true;refs=null;attempted=null;pendingSince=0;observer.observe(document.documentElement,options);}run();return JSON.stringify({state,reason});},freeze(){stopped=true;observer.disconnect();if(timer){clearTimeout(timer);timer=null;}conceal();},stop(){stopped=true;observer.disconnect();if(timer)clearTimeout(timer);window.removeEventListener('beforeunload',unloading);document.documentElement.removeAttribute('data-nova-favourites-checking');cover.remove();style.remove();delete window.__novaFav10;}};
 window.__novaFav10=controller;run();return controller.peek();
})()
