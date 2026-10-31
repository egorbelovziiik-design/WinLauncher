const mc=require('minecraft-protocol');
let windowId=0,stateId=0;
const clients=['EcoSeller','EcoBuyer'].map(username=>{
 const c=mc.createClient({host:'127.0.0.1',port:Number(process.argv[2]),username,version:'1.21.4',auth:'offline'});
 c.on('position',p=>c.write('teleport_confirm',{teleportId:p.teleportId}));
 if(username==='EcoBuyer'){
  c.on('open_window',p=>{windowId=p.windowId;stateId=0});
  c.on('window_items',p=>{if(p.windowId===windowId)stateId=p.stateId});
  c.on('set_slot',p=>{if(p.windowId===windowId)stateId=p.stateId});
 }
 c.on('error',e=>console.log('CLIENT_ERROR',username,String(e)));c.on('end',why=>console.log('CLIENT_END',username,String(why)));return c;
});
let input='';process.stdin.on('data',data=>{input+=data;const lines=input.split('\n');input=lines.pop();for(const line of lines){
 const [cmd,slot]=line.trim().split(/\s+/);
 if(cmd==='CLICK')clients[1].write('window_click',{windowId,stateId,slot:Number(slot),mouseButton:0,mode:0,changedSlots:[],cursorItem:{itemCount:0}});
 if(cmd==='done'){for(const c of clients)c.end('test complete');setTimeout(()=>process.exit(0),300);}
}});
setTimeout(()=>{for(const c of clients)c.end('timeout');process.exit(2)},420000);
