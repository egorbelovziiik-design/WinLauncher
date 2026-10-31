const mc = require('minecraft-protocol');
const c = mc.createClient({host:'127.0.0.1',port:Number(process.argv[2]),username:'ExpTester',version:'1.21.4',auth:'offline'});
let windowId=0,stateId=0;
c.on('position',p=>c.write('teleport_confirm',{teleportId:p.teleportId}));
c.on('open_window',p=>{windowId=p.windowId;stateId=0;});
c.on('window_items',p=>{if(p.windowId===windowId)stateId=p.stateId;});
c.on('set_slot',p=>{if(p.windowId===windowId)stateId=p.stateId;});
c.on('error',e=>console.log('CLIENT_ERROR',String(e)));
c.on('end',why=>console.log('CLIENT_END',String(why)));
let buffer='';process.stdin.on('data',bytes=>{buffer+=bytes;const lines=buffer.split('\n');buffer=lines.pop();for(const line of lines){
 const [cmd,slot]=line.trim().split(/\s+/);
 if(cmd==='CLICK')c.write('window_click',{windowId,stateId,slot:Number(slot),mouseButton:0,mode:0,changedSlots:[],cursorItem:{itemCount:0}});
 else if(cmd==='done'){c.end('test complete');setTimeout(()=>process.exit(0),300);}
}});
setTimeout(()=>{c.end('timeout');process.exit(2)},420000);
