// Real 1.21.4 protocol clients: block interaction and inventory window clicks.
const mc = require('minecraft-protocol');
const port = Number(process.argv[2]);
const clients = {};
for (const [key, username] of [['A','OrbTester'], ['B','OrbEnemy']]) {
  const c = clients[key] = mc.createClient({host:'127.0.0.1', port, username, version:'1.21.4', auth:'offline'});
  c.tradeWindow = 0; c.clickState = 0;
  c.on('position', p => c.write('teleport_confirm', {teleportId:p.teleportId}));
  c.on('open_window', p => { c.tradeWindow=p.windowId; c.clickState=0; console.log('OPEN',key,c.tradeWindow); });
  c.on('window_items', p => { if (p.windowId===c.tradeWindow) c.clickState=p.stateId; });
  c.on('set_slot', p => { if (p.windowId===c.tradeWindow) c.clickState=p.stateId; });
  c.on('error', e => console.log('CLIENT_ERROR',key,String(e)));
  c.on('end', why => console.log('CLIENT_END',key,String(why)));
}
let buffer='';
process.stdin.on('data', bytes => {
  buffer+=bytes;const lines=buffer.split('\n');buffer=lines.pop();
  for(const line of lines){
    const [cmd,...args]=line.trim().split(/\s+/);
    if(cmd==='BLOCK'){
      const [x,y,z]=args.map(Number);
      clients.A.write('block_place',{hand:0,location:{x,y,z},direction:1,cursorX:0.5,cursorY:1,cursorZ:0.5,insideBlock:false,worldBorderHit:false,sequence:1});
      console.log('BLOCK',x,y,z);
    }else if(cmd==='CLICK'){
      const [key,slot]=args;const c=clients[key];
      c.write('window_click',{windowId:c.tradeWindow,stateId:c.clickState,slot:Number(slot),mouseButton:0,mode:0,changedSlots:[],cursorItem:{itemCount:0}});
      console.log('CLICK',key,c.tradeWindow,c.clickState,slot);
    }else if(cmd==='done'){
      for(const c of Object.values(clients))c.end('test complete');setTimeout(()=>process.exit(0),300);
    }
  }
});
setTimeout(()=>{for(const c of Object.values(clients))c.end('timeout');process.exit(2);},420000);
