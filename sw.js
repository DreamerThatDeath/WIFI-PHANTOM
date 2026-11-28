const CACHE="wifi-phantom-v9";
const FILES=["./","./index.html","./app.html","./style.css?v=9","./app.js?v=9","./manifest.webmanifest?v=9","./icon.svg?v=9"];
self.addEventListener("install",event=>event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(FILES)).then(()=>self.skipWaiting())));
self.addEventListener("activate",event=>event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(key=>key!==CACHE).map(key=>caches.delete(key)))).then(()=>self.clients.claim())));
self.addEventListener("fetch",event=>{
 if(event.request.method!=="GET")return;
 const url=new URL(event.request.url);
 const isPage=event.request.mode==="navigate"||url.pathname.endsWith("/");
 if(isPage){event.respondWith(fetch(event.request).then(response=>{const copy=response.clone();caches.open(CACHE).then(cache=>cache.put("./index.html",copy));return response}).catch(()=>caches.match("./index.html")));return}
 event.respondWith(caches.match(event.request).then(cached=>{if(cached)return cached;return fetch(event.request).then(response=>{if(response.ok){const copy=response.clone();caches.open(CACHE).then(cache=>cache.put(event.request,copy))}return response})}));
});