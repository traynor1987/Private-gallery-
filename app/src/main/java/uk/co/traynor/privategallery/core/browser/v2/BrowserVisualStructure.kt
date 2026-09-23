package uk.co.traynor.privategallery.core.browser.v2

/** Read-only, bounded geometric evidence. No text, attributes, selectors or CSS strings leave JS. */
internal object BrowserVisualStructure {
    val SCRIPT = """
        function(){
          var all=document.getElementsByTagName('*'),limit=Math.min(all.length,5000),cache=new WeakMap(),rects=new WeakMap();
          var w=innerWidth,h=innerHeight,area=Math.max(1,w*h),candidates=[],covers=[];
          var o={ancestor_hidden:0,own_hidden:0,display_none:0,visibility_hidden:0,opacity_zero:0,opacity_reduced:0,
            content_visibility_hidden:0,viewport_intersecting:0,outside_viewport:0,clipped_elements:0,fully_clipped:0,
            content_candidates:0,sampled_candidates:0,sample_unobscured:0,sample_obscured:0,sample_unknown:0,
            covering_layers:0,fixed_layers:0,absolute_layers:0,stacking_contexts:0,complex_clip_elements:0,
            transformed_elements:0,shadow_hosts:0,top_layer_elements:0,ancestor_scan_truncated:false,
            candidate_scan_truncated:false,layer_scan_truncated:false,visual_scan_truncated:all.length>limit,layers:[]};
          function css(e){var c=cache.get(e);if(!c){c=getComputedStyle(e);cache.set(e,c);}return c;}
          function rect(e){var r=rects.get(e);if(!r){r=e.getBoundingClientRect();rects.set(e,r);}return r;}
          function context(c){return c.position==='fixed'||c.position==='sticky'||(c.position!=='static'&&c.zIndex!=='auto')||Number(c.opacity)<1||c.transform!=='none'||c.isolation==='isolate'||c.filter!=='none';}
          function complex(c){return c.clipPath!=='none'||(c.maskImage&&c.maskImage!=='none')||c.clip!=='auto';}
          function overlap(a,b){return {left:Math.max(a.left,b.left),top:Math.max(a.top,b.top),right:Math.min(a.right,b.right),bottom:Math.min(a.bottom,b.bottom)};}
          function size(r){return Math.max(0,r.right-r.left)*Math.max(0,r.bottom-r.top);}
          function inside(r,x,y){return x>=r.left&&x<r.right&&y>=r.top&&y<r.bottom;}
          function isTop(e){try{return e.matches(':modal,:popover-open');}catch(ignore){return false;}}
          var viewport={left:0,top:0,right:w,bottom:h};
          for(var i=0;i<limit;i++){
            var e=all[i],c=css(e),r=rect(e),a=e.parentElement,n=0,opacity=Number(c.opacity),hidden=false,clip=overlap(r,viewport),original=size(clip),depth=0,unknown=complex(c);
            if(c.display==='none')o.display_none++;
            if(c.visibility==='hidden'||c.visibility==='collapse')o.visibility_hidden++;
            if(Number(c.opacity)===0)o.opacity_zero++;
            if(context(c))o.stacking_contexts++;
            if(complex(c))o.complex_clip_elements++;
            if(c.transform!=='none')o.transformed_elements++;
            if(e.shadowRoot)o.shadow_hosts++;
            if(isTop(e))o.top_layer_elements++;
            var own=c.display==='none'||c.visibility==='hidden'||c.visibility==='collapse'||opacity===0||c.contentVisibility==='hidden';
            if(own)o.own_hidden++;
            while(a&&n++<64){
              var ac=css(a),ar=rect(a);opacity*=Number(ac.opacity);
              // Visibility can be explicitly restored by a descendant; current computed visibility is authoritative.
              if(ac.display==='none'||ac.contentVisibility==='hidden'||Number(ac.opacity)===0)hidden=true;
              if(ac.contentVisibility==='hidden')o.content_visibility_hidden++;
              if(context(ac))depth++;
              if(complex(ac))unknown=true;
              if(ac.overflowX!=='visible'||ac.overflowY!=='visible'){
                var scaleX=a.offsetWidth?ar.width/a.offsetWidth:1,scaleY=a.offsetHeight?ar.height/a.offsetHeight:1;
                var box={left:ar.left+a.clientLeft*scaleX,top:ar.top+a.clientTop*scaleY,
                  right:ar.left+(a.clientLeft+a.clientWidth)*scaleX,bottom:ar.top+(a.clientTop+a.clientHeight)*scaleY};
                if(ac.overflowX!=='visible'){clip.left=Math.max(clip.left,box.left);clip.right=Math.min(clip.right,box.right);}
                if(ac.overflowY!=='visible'){clip.top=Math.max(clip.top,box.top);clip.bottom=Math.min(clip.bottom,box.bottom);}
              }
              a=a.parentElement;
            }
            if(a)o.ancestor_scan_truncated=true;
            if(hidden)o.ancestor_hidden++;
            if(opacity>0&&opacity<1)o.opacity_reduced++;
            if(original>0)o.viewport_intersecting++;else if(r.width>0&&r.height>0)o.outside_viewport++;
            if(size(clip)<original){o.clipped_elements++;if(size(clip)===0)o.fully_clipped++;}
            if(own||hidden||opacity===0||size(clip)===0)continue;
            var fixed=c.position==='fixed',absolute=c.position==='absolute';
            if(fixed)o.fixed_layers++;if(absolute)o.absolute_layers++;
            if((fixed||absolute||isTop(e))&&size(clip)/area>=0.2){
              o.covering_layers++;
              covers.push({e:e,r:clip,c:c,depth:depth,opacity:opacity,top:isTop(e)});
            }
            // Check node TYPE only; never read nodeValue/textContent/innerText or form values.
            var textNode=false;for(var j=0;j<e.childNodes.length&&j<64;j++){if(e.childNodes[j].nodeType===3){textNode=true;break;}}
            var standard=/^(BUTTON|INPUT|SELECT|TEXTAREA|IMG|VIDEO|CANVAS|IFRAME|SVG)$/.test(e.tagName);
            if((standard||textNode)&&!/^(SCRIPT|STYLE|NOSCRIPT|OPTION|TITLE)$/.test(e.tagName)){
              o.content_candidates++;candidates.push({e:e,r:clip,unknown:unknown||!!a});
            }
          }
          var count=Math.min(candidates.length,64);o.sampled_candidates=count;o.candidate_scan_truncated=candidates.length>count;
          for(var k=0;k<count;k++){
            var candidate=candidates[Math.floor(k*candidates.length/count)],cr=candidate.r;
            var points=[[0.5,0.5],[0.2,0.2],[0.8,0.2],[0.2,0.8],[0.8,0.8]];
            points.forEach(function(p){
              var x=cr.left+(cr.right-cr.left)*p[0],y=cr.top+(cr.bottom-cr.top)*p[1],stack=document.elementsFromPoint(x,y),index=stack.indexOf(candidate.e),blocked=false,uncertain=candidate.unknown||index<0;
              if(index>=0){for(var t=0;t<index;t++){var front=stack[t];if(!candidate.e.contains(front)&&!front.contains(candidate.e)){blocked=true;break;}}}
              // Hit testing omits pointer-events:none. Do not call these points unobscured.
              covers.forEach(function(layer){if(layer.c.pointerEvents==='none'&&!layer.e.contains(candidate.e)&&!candidate.e.contains(layer.e)&&inside(layer.r,x,y))uncertain=true;});
              if(blocked)o.sample_obscured++;else if(uncertain)o.sample_unknown++;else o.sample_unobscured++;
            });
          }
          o.layer_scan_truncated=covers.length>8;
          covers.slice(0,8).forEach(function(layer){
            var z=Number(layer.c.zIndex),hits=0,front=0,behind=0;
            for(var gx=0;gx<5;gx++)for(var gy=0;gy<5;gy++){
              var stack=document.elementsFromPoint((gx+0.5)*w/5,(gy+0.5)*h/5),li=stack.indexOf(layer.e);
              if(li>=0&&stack.slice(0,li).every(function(e){return layer.e.contains(e);}))hits++;
              covers.slice(0,8).forEach(function(other){if(other===layer)return;var oi=stack.indexOf(other.e);if(li>=0&&oi>=0&&!layer.e.contains(other.e)&&!other.e.contains(layer.e)){if(li<oi)front++;else behind++;}});
            }
            o.layers.push({position:layer.c.position==='fixed'?'fixed':layer.c.position==='absolute'?'absolute':'other',
              z_band:layer.c.zIndex==='auto'?'auto':z<0?'negative':z>0?'positive':'zero',
              coverage_percent:Math.round(size(layer.r)*100/area),opacity_percent:Math.round(layer.opacity*100),
              context_depth:layer.depth,hit_top_points:hits,in_front_samples:front,behind_samples:behind,
              pointer_none:layer.c.pointerEvents==='none',top_layer:layer.top});
          });
          return o;
        }
    """.trimIndent()
}
