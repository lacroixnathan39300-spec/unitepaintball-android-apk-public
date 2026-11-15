'use strict';
if(new URLSearchParams(location.search).get('readonly')==='1'){
 document.documentElement.classList.add('avatar-readonly');
 const status=document.getElementById('status');
 if(status)new MutationObserver(()=>{if(/Rendu 3D indisponible|Rendu 3D interrompu/.test(status.textContent||''))status.classList.add('preview-error')}).observe(status,{childList:true,subtree:true,characterData:true});
}
