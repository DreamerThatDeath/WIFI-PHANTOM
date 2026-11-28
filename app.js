const $=id=>document.getElementById(id);
const samples=[
{name:"HOME-NETWORK",security:"WPA2",signal:"-42 dBm",channel:"CH 6",strength:92},
{name:"PHANTOM_GUEST",security:"WPA2",signal:"-61 dBm",channel:"CH 11",strength:67},
{name:"Cafe_Free_WiFi",security:"OPEN",signal:"-74 dBm",channel:"CH 1",strength:39},
{name:"MESH-NODE-02",security:"WPA3",signal:"-80 dBm",channel:"CH 6",strength:24}
];
let toastTimer, selectedNetwork=null, activeNativeRequest=false, nativeConnected=false;
function toast(s){$("toast").textContent=s;$("toast").classList.add("show");clearTimeout(toastTimer);toastTimer=setTimeout(()=>$("toast").classList.remove("show"),3500)}
function log(s){const d=document.createElement("div");d.textContent=s;$("terminal").append(d);$("terminal").scrollTop=$("terminal").scrollHeight}
function isNative(){return !!(window.AndroidWifi&&typeof window.AndroidWifi.scanWifi==="function")}
function wifi(){
 if(isNative()){window.AndroidWifi.openWifiSettings();toast("Opening Android Wi-Fi settings.");return}
 toast("Open your device Wi-Fi settings to connect.");
 location.href="intent:#Intent;action=android.settings.WIFI_SETTINGS;end";
}
function openPasswordDialog(network){
 selectedNetwork=network;
 const secure=!/^(OPEN|NONE)$/i.test(network.security||"");
 $("passwordTitle").textContent="Connect to "+network.name;
 $("passwordHint").textContent=secure?"Enter the network password. You can optionally save it encrypted on this device for next time.":"This is an open network. Android will still ask you to approve the connection.";
 $("wifiPassword").hidden=!secure;
 $("wifiPassword").value="";
 $("saveWifiPassword").checked=false;
 $("saveWifiPassword").parentElement.hidden=!secure;
 $("useSavedPassword").hidden=true;
 if(secure&&isNative()&&window.AndroidWifi.hasSavedWifiPassword){
  $("useSavedPassword").hidden=!window.AndroidWifi.hasSavedWifiPassword(network.name);
 }
 $("passwordModal").hidden=false;
 if(secure)$("wifiPassword").focus();
}
function closePasswordDialog(){$("passwordModal").hidden=true;$("wifiPassword").value="";selectedNetwork=null}
function connectSelected(){
 if(!selectedNetwork)return;
 const network=selectedNetwork;
 const secure=!/^(OPEN|NONE)$/i.test(network.security||"");
 const password=$("wifiPassword").value;
 if(secure&& (password.length<8 || password.length>63)){toast("Enter a Wi-Fi password between 8 and 63 characters.");return}
 if(!isNative()){toast("Real Wi-Fi connection is available in the Android app. Use Wi-Fi settings on the website.");closePasswordDialog();return}
 if(!window.AndroidWifi.connectWifi){toast("Update the Android app to enable Wi-Fi connection.");return}
 if(secure&&$("saveWifiPassword").checked){
  if(!window.AndroidWifi.saveWifiPassword||!window.AndroidWifi.saveWifiPassword(network.name,password)){
   toast("Could not securely save this password. Connection was not started.");return;
  }
 }
 closePasswordDialog();
 $("systemStatus").textContent="REQUESTING CONNECTION";
 log("[CONNECT] Requesting Android-approved connection to "+network.name+".");
 window.AndroidWifi.connectWifi(network.name,password,network.security);
 $("connectState")?.setAttribute("data-state","requesting");
}
function disconnectWifi(){
 if(isNative()&&window.AndroidWifi.disconnectWifi){
  window.AndroidWifi.disconnectWifi();
  $("systemStatus").textContent="DISCONNECT REQUESTED";
  toast("Releasing WIFI PHANTOM's Wi-Fi request. If this was a normal saved connection, Android settings will open.");
  return;
 }
 wifi();
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
  const right=document.createElement("div");right.className="network-row-right";
  const signal=document.createElement("div");signal.className="signal";signal.textContent=source==="live"?"LIVE":"DEMO";
  const strength=document.createElement("div");strength.className="network-meta";strength.textContent=n.strength+"% signal";
  const connect=document.createElement("button");connect.className="network-connect";connect.type="button";connect.textContent="CONNECT";
  connect.disabled=source!=="live"||!isNative();
  connect.title=connect.disabled?"Live connection requires the Android app":"Connect to "+n.name;
  connect.onclick=()=>openPasswordDialog(n);
  right.append(signal,strength,connect);row.append(left,right);list.append(row);
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
  renderNetworks(samples.map(n=>({...n})),"demo");
  b.disabled=false;b.textContent="⌕ RUN DEMO SCAN";$("systemStatus").textContent="SYSTEM READY";
  toast("Website preview uses sample networks. Install the Android app for live scanning.");
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
 if(result.note)toast(result.note);else toast("Live scan complete. "+networks.length+" network(s) found.");
};
window.onNativeWifiConnectionStatus=function(raw){
 let result;try{result=typeof raw==="string"?JSON.parse(raw):raw}catch(e){result={status:"error",message:"Could not read connection status."}}
 if(result.status==="available"){activeNativeRequest=true;nativeConnected=true;$("systemStatus").textContent="APP WI-FI READY";log("[CONNECTED] Android approved the network request.");toast("Wi-Fi request approved. This connection is scoped to WIFI PHANTOM.");}
 else if(result.status==="unavailable"){activeNativeRequest=false;nativeConnected=false;$("systemStatus").textContent="CONNECTION UNAVAILABLE";log("[FAILED] "+(result.message||"Android could not connect."));toast(result.message||"Connection unavailable. Check the password and try again.");}
 else if(result.status==="lost"){nativeConnected=false;$("systemStatus").textContent="WI-FI CONNECTION LOST";log("[NOTICE] The requested Wi-Fi connection was lost.");toast("Wi-Fi connection lost.");}
 else if(result.status==="disconnected"){activeNativeRequest=false;nativeConnected=false;$("systemStatus").textContent="SYSTEM READY";log("[DISCONNECT] WIFI PHANTOM released its Wi-Fi request.");toast("WIFI PHANTOM connection released.");}
 else if(result.status==="unsupported"){toast(result.message||"Use Android Wi-Fi settings for this network.");}
 else if(result.status==="error"){toast(result.message||"Wi-Fi connection failed.");}
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
$("disconnectBtn").onclick=disconnectWifi;
$("passwordForm").onsubmit=e=>{e.preventDefault();connectSelected()};
$("cancelConnect").onclick=closePasswordDialog;
$("useSavedPassword").onclick=()=>{
 if(!selectedNetwork||!isNative()||!window.AndroidWifi.connectSavedWifi)return;
 const n=selectedNetwork;closePasswordDialog();
 $("systemStatus").textContent="REQUESTING CONNECTION";
 log("[CONNECT] Using the saved credential for "+n.name+".");
 window.AndroidWifi.connectSavedWifi(n.name,n.security);
};
$("passwordModal").onclick=e=>{if(e.target===$("passwordModal"))closePasswordDialog()};
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
addEventListener("beforeinstallprompt",e=>{e.preventDefault();installEvent=e;$("installBtn").hidden=false;$("installPwaBtn").hidden=false});
async function promptWebInstall(){
 if(!installEvent){toast("Use your browser menu and choose Install app or Add to Home screen.");return}
 installEvent.prompt();
 await installEvent.userChoice;
 installEvent=null;
 $("installBtn").hidden=true;
 $("installPwaBtn").hidden=true;
}
$("installBtn").onclick=promptWebInstall;
$("installPwaBtn").onclick=promptWebInstall;
if("serviceWorker"in navigator)addEventListener("load",()=>navigator.serviceWorker.register("./sw.js").catch(()=>{}));
if(isNative()){
 $("scanBtn").textContent="⌕ SCAN WI-FI";
 const label=document.querySelector(".pill");if(label)label.textContent="LIVE ANDROID SCAN";
 const note=document.querySelector(".notice span");
 if(note)note.innerHTML="<b>Android connection controls:</b> Select a live network, enter its password if required, then approve Android's system prompt. Connection requests are app-scoped.";
} else {
 $("scanBtn").textContent="⌕ RUN DEMO SCAN";
}

if(isNative()&&$("installCard"))$("installCard").hidden=true;
