(()=>{
 const empty={origin:false,container:false,rows:0,shape:0,consistent:false,virtual:false,unread:false,shadow:false};
 if(location.origin!=='https://web.whatsapp.com')return JSON.stringify(empty);
 const root=document.querySelector('#pane-side');
 if(!root)return JSON.stringify({...empty,origin:true});
 const rows=Array.from(root.querySelectorAll('[role="row"]')).slice(0,64);
 const shape=row=>[row.tagName,row.childElementCount,!!row.querySelector('[role="gridcell"]')].join(':');
 const consistent=rows.length>0&&rows.every(row=>shape(row)===shape(rows[0]));
 const first=rows[0];
 const category=!first?0:first.tagName==='DIV'?0:first.tagName==='LI'?1:2;
 const signature=first?category*16+Math.min(4,first.childElementCount)*2+(first.querySelector('[role="gridcell"]')?1:0):0;
 const rects=rows.map(row=>row.getBoundingClientRect());
 const span=rects.length?Math.max(...rects.map(r=>r.bottom))-Math.min(...rects.map(r=>r.top)):0;
 const virtual=root.clientHeight>0&&root.scrollHeight>root.clientHeight*2&&span>0&&span<root.scrollHeight/2;
 const unread=!!root.querySelector('[data-icon="unread-count"],[data-testid="icon-unread-count"]');
 const walker=document.createTreeWalker(root,NodeFilter.SHOW_ELEMENT);let shadow=!!root.shadowRoot;
 for(let n=0,node;n<256&&(node=walker.nextNode());n++)if(node.shadowRoot){shadow=true;break;}
 return JSON.stringify({origin:true,container:true,rows:rows.length,shape:signature,consistent,virtual,unread,shadow});
})()
