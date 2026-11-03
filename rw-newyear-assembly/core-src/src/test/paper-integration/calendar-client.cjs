// Two real protocol clients. Only the owner receives the private inventory.
const mc=require('minecraft-protocol');
const port=Number(process.argv[2]);
const clients=['CalendarTester','CalendarOther'].map(username=>mc.createClient({host:'127.0.0.1',port,username,version:'1.21.4',auth:'offline'}));
let windowId=0,stateId=0,opens=0,observerOpens=0,reminders=[0,0],clicks=0,contentReady=false;
const pending=[];
// Events menu opens first, then calendar pages 1, 2 and 1. Never click a
// previous window just because the server-side test logged a request early.
const requiredOpens={53:2,45:3,10:4,49:4};
function flushClicks(){
 while(pending.length && contentReady && opens >= requiredOpens[pending[0]]){
  const slot=pending.shift();
  clients[0].write('window_click',{windowId,stateId,slot,mouseButton:0,mode:0,changedSlots:[],cursorItem:{itemCount:0}});
  clicks++;console.log('CALENDAR_CLICK',slot,windowId,stateId);
 }
}
clients[0].on('open_window',p=>{windowId=p.windowId;stateId=0;contentReady=false;opens++;});
clients[1].on('open_window',()=>observerOpens++);
clients[0].on('window_items',p=>{if(p.windowId===windowId){stateId=p.stateId;contentReady=true;flushClicks();}});
clients[0].on('set_slot',p=>{if(p.windowId===windowId)stateId=p.stateId;});
clients.forEach((client,i)=>{
 client.on('packet',(data,meta)=>{if(meta.name==='system_chat'&&JSON.stringify(data.content).includes('Календарь:'))reminders[i]++;});
 client.on('position',p=>client.write('teleport_confirm',{teleportId:p.teleportId}));
 client.on('error',e=>console.error('CLIENT_ERROR',String(e)));
 client.on('end',reason=>console.log('CLIENT_END',String(reason)));
});
let input='';process.stdin.on('data',data=>{
 input+=data;const lines=input.split('\n');input=lines.pop();
 for(const line of lines){const [cmd,arg]=line.trim().split(/\s+/);
  if(cmd==='CLICK'){
   const slot=Number(arg);if(!(slot in requiredOpens))throw new Error('unknown click '+arg);
   pending.push(slot);flushClicks();
  }else if(cmd==='REPORT')console.log('CALENDAR_NETWORK',JSON.stringify({opens,observerOpens,reminders,clicks}));
  else if(cmd==='done'){for(const c of clients)c.end('test complete');setTimeout(()=>process.exit(0),300);}
 }
});
setTimeout(()=>{for(const c of clients)c.end('timeout');process.exit(2)},420000);
