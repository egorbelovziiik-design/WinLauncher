// Three real Paper 1.21.4 clients; the probe controls blocks, entities and GUI clicks.
const mc = require('minecraft-protocol');
const names = ['ExpOne', 'ExpTwo', 'ExpThree'];
const clients = [null, null, null];
const connected = [false, false, false], errors = [];
const windows = names.map(() => ({id: 0, state: 0, ready: false, queue: []}));
let blocks = 0, entities = 0, clicks = 0, sequence = 0, input = '';
function flush(i) {
  const w=windows[i], c=clients[i];
  while (c && w.ready && w.queue.length) {
    const [slot,button,mode]=w.queue.shift();
    c.write('window_click',{windowId:w.id,stateId:w.state,slot,mouseButton:button,mode,changedSlots:[],cursorItem:{itemCount:0}});
    clicks++;
  }
}
function connect(i) {
  const c = clients[i] = mc.createClient({host:'127.0.0.1',port:Number(process.argv[2]),username:names[i],version:'1.21.4',auth:'offline'});
  windows[i]={id:0,state:0,ready:false,queue:[]};
  c.on('position',p=>{connected[i]=true;c.write('teleport_confirm',{teleportId:p.teleportId});});
  c.on('open_window',p=>{windows[i].id=p.windowId;windows[i].state=0;windows[i].ready=false;});
  c.on('window_items',p=>{const w=windows[i];if(p.windowId===w.id){w.state=p.stateId;w.ready=true;flush(i);}});
  c.on('set_slot',p=>{const w=windows[i];if(p.windowId===w.id)w.state=p.stateId;});
  c.on('error',e=>{errors.push(names[i]+': '+String(e));console.error('CLIENT_ERROR',String(e));});
}
for(let i=0;i<3;i++) connect(i);
process.stdin.on('data',bytes=>{
 input+=bytes;const lines=input.split('\n');input=lines.pop();
 for(const line of lines){
  const [cmd,...args]=line.trim().split(/\s+/);
  if(cmd==='BLOCK') {
   const [i,x,y,z,face,hand]=args.map(Number);blocks++;
   clients[i].write('block_place',{hand:hand||0,location:{x,y,z},direction:face,cursorX:.5,cursorY:.5,cursorZ:.5,insideBlock:false,worldBorderHit:false,sequence:++sequence});
  } else if(cmd==='ENTITY') {
   const [i,id,action]=args.map(Number);entities++;
   if(action===2) clients[i].write('use_entity',{target:id,mouse:2,x:0,y:1,z:0,hand:0,sneaking:false});
   else if(action===1) clients[i].write('use_entity',{target:id,mouse:1,sneaking:false});
   else clients[i].write('use_entity',{target:id,mouse:0,hand:0,sneaking:false});
  } else if(cmd==='CLICK') {
   const [i,slot,button,mode]=args.map(Number);windows[i].queue.push([slot,button,mode]);flush(i);
  } else if(cmd==='DISCONNECT') {
   const i=Number(args[0]);clients[i].end('requested test disconnect');
  } else if(cmd==='RECONNECT') connect(Number(args[0]));
  else if(cmd==='done') {
   const ok=connected.every(Boolean)&&errors.length===0;
   console.log(ok?'V713_CLIENT PASS':'V713_CLIENT FAIL',JSON.stringify({connected,errors,blocks,entities,clicks}));
   for(const c of clients) if(c) c.end('done');setTimeout(()=>process.exit(ok?0:3),300);
  }
 }
});
setTimeout(()=>{clients.forEach(c=>c&&c.end('timeout'));process.exit(2)},420000);
