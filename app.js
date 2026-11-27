const $=id=>document.getElementById(id);
const samples=[
{name:"HOME-NETWORK",security:"WPA2/WPA3",signal:"-42 dBm",channel:"CH 6",strength:92},
{name:"PHANTOM_GUEST",security:"WPA2",signal:"-61 dBm",channel:"CH 11",strength:67},
{name:"Cafe_Free_WiFi",security:"OPEN",signal:"-74 dBm",channel:"CH 1",strength:39},
{name:"MESH-NODE-02",security:"WPA3",signal:"-80 dBm",channel:"CH 6",strength:24}
];
let toastTimer;
function toast(s){$("toast").textContent=s;$("toast").classList.add("show");clearTimeout(toastTimer);toastTimer=setTimeout(()=>$("toast").classList.remove("show"),3200)}
function log(s){const d=document.createElement("div");d.textContent=s;$("terminal").append(d);$("terminal").scrollTop=$("terminal").scrollHeight}
function isNative(){return !!(window.AndroidWifi&&typeof window.AndroidWifi.scanWifi==="function")}
function wifi(){
 if(isNative()){window.AndroidWifi.openWifiSettings();toast("Opening Android Wi-Fi settings.");return}
 toast("Opening Android Wi-Fi settings. Select a network and enter a password you are allowed to use.");
 location.href="intent:#Intent;action=android.settings.WIFI_SETTINGS;end";
}
function renderNetworks(networks,source){
 const list=$("networkList");list.replaceChildren();
 if(!networks.length){const empty=document.createElement("div");empty.className="empty";empty.textContent="No visible network names returned.";list.append(empty)}
 networks.forEach(n=>{
  const row=document.createElement("div");row.className="network-row";
  const left=document.createElement("div");
  const name=document.createElement("div");name.className="network-name";name.textContent=n.name;
  const meta=document.createElement("div");meta.className="network-meta";
  meta.textContent=source==="live"?(n.frequency/1000).toFixed(1)+" GHz · "+n.signalDbm+" dBm":n.channel+" · "+n.signal;
  const tag=document.createElement("span");tag.className="network-tag";tag.textContent=n.security;
  left.append(name,meta,tag);
  const right=document.createElement("div");
  const signal=document.createElement("div");signal.className="signal";signal.textContent=source==="live"?"LIVE":"DEMO";
  const strength=document.createElement("div");strength.className="network-meta";strength.textContent=n.strength+"% signal";
  right.append(signal,strength);row.append(left,right);list.append(row);
 });
 $("networkCount").textContent=String(networks.length).padStart(2,"0");
 $("scanTime").textContent="UPDATED "+new Date().toLocaleTimeString();
}
function scan(){
 const b=$("scanBtn");b.disabled=true;b.textContent=isNative()?"SCANNING WI-FI…":"SCANNING DEMO…";
 $("systemStatus").textContent=isNative()?"REQUESTING ANDROID SCAN":"SIMULATION RUNNING";
 if(isNative()){
  try{window.AndroidWifi.scanWifi()}
  catch(e){b.disabled=false;b.textContent="⌕ SCAN WI-FI";$("systemStatus").textContent="SCAN ERROR";toast("Could not start Android Wi-Fi scan.")}
  return;
 }
 setTimeout(()=>{
  renderNetworks(samples.map(n=>({...n})).map(n=>({...n,strength:n.strength})).map(n=>n),"demo");
  b.disabled=false;b.textContent="⌕ RUN DEMO SCAN";$("systemStatus").textContent="SYSTEM READY";
  toast("Demo scan complete. These are sample networks, not live results.");
 },850);
}
window.onNativeWifiResults=function(raw){
 let result;
 try{result=typeof raw==="string"?JSON.parse(raw):raw}catch(e){result={status:"scan_error",message:"Could not read scan results."}}
 const b=$("scanBtn");b.disabled=false;b.textContent="⌕ SCAN WI-FI";
 $("systemStatus").textContent="SYSTEM READY";
 if(result.status!=="success"){toast(result.message||"Wi-Fi scan could not be completed.");return}
 const networks=(result.networks||[]).map(n=>({...n,strength:n.signalPercent,signal:n.signalDbm+" dBm"}));
 renderNetworks(networks,"live");
 if(result.note)toast(result.note);
 else toast("Live scan complete. "+networks.length+" network(s) found.");
};
async function phantom(){
 const b=$("phantomBtn");b.disabled=true;b.textContent="SIMULATION RUNNING…";$("phantomStatus").textContent="ACTIVE";$("phantomStatus").classList.add("green");
 log("root@phantom:~$ phantom --demo --safe");
 for(const line of ["[INIT] Boot sequence complete.","[RADAR] Loading simulated signal map…","[SCAN] 2.4 GHz observation layer: DEMO","[CHECK] Sample access point records loaded.","[AUDIT] Reviewing example security labels…","[SAFE] No packets sent. No devices contacted.","[DONE] Simulation finished. Your network remains untouched."]){await new Promise(r=>setTimeout(r,450));log(line)}
 log("root@phantom:~$ <simulation complete>");b.disabled=false;b.textContent="▶ RUN AGAIN";toast("Simulation complete. No real Wi-Fi action occurred.");
}
$("scanBtn").onclick=scan;
$("wifiBtn").onclick=wifi;
$("openSettings").onclick=wifi;
$("phantomBtn").onclick=phantom;
$("clearBtn").onclick=()=>$("terminal").innerHTML='<div class="muted">Console cleared. Simulation only.</div><div>root@phantom:~$ <i class="cursor"></i></div>';
$("routerBtn").onclick=()=>{
 const url="http://192.168.1.1";
 if(isNative()){window.AndroidWifi.openRouter(url);toast("Opening your local router page. Administrator login may be required.");return}
 toast("Opening common local router address 192.168.1.1. Your router may use another address.");
 window.open(url,"_blank","noopener");
};
$("securityBtn").onclick=()=>$("checklist").scrollIntoView({behavior:"smooth"});
let installEvent;
addEventListener("beforeinstallprompt",e=>{e.preventDefault();installEvent=e;$("installBtn").hidden=false});
$("installBtn").onclick=async()=>{
 if(!installEvent){toast("Open your browser menu and choose Add to Home screen.");return}
 installEvent.prompt();await installEvent.userChoice;installEvent=null;$("installBtn").hidden=true;
};
if("serviceWorker"in navigator)addEventListener("load",()=>navigator.serviceWorker.register("./sw.js").catch(()=>{}));
if(isNative()){
 $("scanBtn").textContent="⌕ SCAN WI-FI";
 const label=document.querySelector(".pill");
 if(label)label.textContent="LIVE ANDROID SCAN";
 const note=document.querySelector(".notice span");
 if(note)note.innerHTML="<b>Android scanner:</b> Live results need Wi-Fi, Location services, and the requested permissions. Joining secured networks still requires valid credentials.";
}
