// Three real 1.21.4 protocol clients. Probe controls GUI clicks and respawn.
const mc=require('minecraft-protocol');
const names=['ExpOne','ExpTwo','ExpThree'];
const clients=names.map(username=>mc.createClient({host:'127.0.0.1',port:Number(process.argv[2]),username,version:'1.21.4',auth:'offline'}));
const connected=[false,false,false], windows=names.map(()=>({id:0,state:0,ready:false,queue:[]}));
const errors=[];let clicks=0,respawns=0;
function flush(i) {
 const w=windows[i];
 while(w.ready && w.queue.length) {
  const [slot,button,mode]=w.queue.shift();
  clients[i].write('window_click',{windowId:w.id,stateId:w.state,slot,mouseButton:button,mode,changedSlots:[],cursorItem:{itemCount:0}});
  clicks++; console.log('V712_CLICK',i,slot,button,mode,w.id,w.state);
 }
}
clients.forEach((client,i)=>{
 client.on('position',p=>{connected[i]=true;client.write('teleport_confirm',{teleportId:p.teleportId});});
 client.on('open_window',p=>{windows[i].id=p.windowId;windows[i].state=0;windows[i].ready=false;});
 client.on('window_items',p=>{const w=windows[i];if(p.windowId===w.id){w.state=p.stateId;w.ready=true;flush(i);}});
 client.on('set_slot',p=>{const w=windows[i];if(p.windowId===w.id)w.state=p.stateId;});
 client.on('error',e=>{errors.push(names[i]+': '+String(e));console.error('CLIENT_ERROR',String(e));});
});
let input='';process.stdin.on('data',bytes=>{
 input+=bytes;const lines=input.split('\n');input=lines.pop();
 for(const line of lines) {
  const [cmd,...args]=line.trim().split(/\s+/);
  if(cmd==='CLICK') { const [i,slot,button,mode]=args.map(Number);windows[i].queue.push([slot,button,mode]);flush(i); }
  else if(cmd==='RESPAWN') { const i=Number(args[0]);clients[i].write('client_command',{actionId:'perform_respawn'});respawns++; }
  else if(cmd==='done') {
   const ok=connected.every(Boolean)&&errors.length===0;
   console.log(ok?'V712_CLIENT PASS':'V712_CLIENT FAIL',JSON.stringify({connected,errors,clicks,respawns}));
   clients.forEach(c=>c.end('done'));setTimeout(()=>process.exit(ok?0:3),300);
  }
 }
});
setTimeout(()=>{clients.forEach(c=>c.end('timeout'));process.exit(2)},420000);
