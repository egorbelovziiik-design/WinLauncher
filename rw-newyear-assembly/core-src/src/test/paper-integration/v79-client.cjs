// Two genuine protocol players. The first one clicks the pet menu and gift chest.
const mc = require('minecraft-protocol');
const clients=['WheelOne','WheelTwo'].map(username=>mc.createClient({host:'127.0.0.1',port:Number(process.argv[2]),username,version:'1.21.4',auth:'offline'}));
let connected=[false,false],errors=[],windowId=0,stateId=0,ready=false,opens=0,clicks=0,blocks=0,sequence=1;
const pending=[];
function flush(){
 while(pending.length&&ready&&opens>=1){
  const slot=pending.shift();clients[0].write('window_click',{windowId,stateId,slot,mouseButton:0,mode:0,changedSlots:[],cursorItem:{itemCount:0}});
  clicks++;console.log('V79_CLICK',slot,windowId,stateId);
 }
}
clients.forEach((c,i)=>{
 c.on('position',p=>{connected[i]=true;c.write('teleport_confirm',{teleportId:p.teleportId})});
 c.on('error',e=>{errors.push(String(e));console.error('CLIENT_ERROR',String(e))});
});
clients[0].on('open_window',p=>{windowId=p.windowId;stateId=0;ready=false;opens++});
clients[0].on('window_items',p=>{if(p.windowId===windowId){stateId=p.stateId;ready=true;flush()}});
clients[0].on('set_slot',p=>{if(p.windowId===windowId)stateId=p.stateId});
let input='';process.stdin.on('data',data=>{
 input+=data;const lines=input.split('\n');input=lines.pop();
 for(const line of lines){const [cmd,...args]=line.trim().split(/\s+/);
  if(cmd==='CLICK'){pending.push(Number(args[0]));flush()}
  else if(cmd==='BLOCK'){
   const [x,y,z]=args.map(Number);clients[0].write('block_place',{hand:0,location:{x,y,z},direction:1,cursorX:.5,cursorY:1,cursorZ:.5,insideBlock:false,worldBorderHit:false,sequence:sequence++});blocks++;
  } else if(cmd==='done'){
   const ok=connected.every(Boolean)&&errors.length===0;
   console.log(ok?'V79_CLIENT PASS':'V79_CLIENT FAIL',JSON.stringify({connected,errors,opens,clicks,blocks}));
   clients.forEach(c=>c.end('done'));setTimeout(()=>process.exit(ok?0:3),300);
  }
 }
});
setTimeout(()=>{clients.forEach(c=>c.end('timeout'));process.exit(2)},420000);
