// Real protocol clients; the probe controls a disposable workbench and target.
const mc = require('minecraft-protocol');
const port=Number(process.argv[2]);
const clients=['ForgeSmith','ForgeTarget'].map(username=>mc.createClient({host:'127.0.0.1',port,username,version:'1.21.4',auth:'offline'}));
let windowId=0,stateId=0;
clients[0].on('open_window',p=>{windowId=p.windowId;stateId=0;});
clients[0].on('window_items',p=>{if(p.windowId===windowId)stateId=p.stateId;});
clients[0].on('set_slot',p=>{if(p.windowId===windowId)stateId=p.stateId;});
for(const client of clients){
  client.on('position',p=>client.write('teleport_confirm',{teleportId:p.teleportId}));
  client.on('error',e=>console.error('CLIENT_ERROR',String(e)));
  client.on('end',reason=>console.log('CLIENT_END',String(reason)));
}
let input='';process.stdin.on('data',data=>{
  input+=data;const lines=input.split('\n');input=lines.pop();
  for(const line of lines){
    const [cmd,arg]=line.trim().split(/\s+/);
    if(cmd==='CLICK'||cmd==='SHIFT'){
      clients[0].write('window_click',{windowId,stateId,slot:0,mouseButton:0,mode:cmd==='SHIFT'?1:0,changedSlots:[],cursorItem:{itemCount:0}});
      console.log('FORGE_CLICK',cmd,windowId,stateId);
    }else if(cmd==='ATTACK'){
      clients[0].write('use_entity',{target:Number(arg),mouse:1,sneaking:false});console.log('FORGE_ATTACK',arg);
    }else if(cmd==='done'){
      for(const client of clients)client.end('test complete');setTimeout(()=>process.exit(0),300);
    }
  }
});
setTimeout(()=>{for(const c of clients)c.end('timeout');process.exit(2)},420000);
