// Real Minecraft 1.21.4 clients: GUI packets, commands, disconnects and an
// incoming signed KingConnect-compatible transfer cookie. No production keys.
const mc = require('minecraft-protocol');
const crypto = require('crypto');
const mode = process.env.KG_CAPTCHA_MODE || 'combined';
const names = mode === 'hub-only' ? ['HubOnly'] : ['CaptchaOne','CaptchaBad','CaptchaLate','CaptchaFree','CaptchaSso','CaptchaOp'];
const clients = names.map(() => null);
const windows = names.map(() => ({id:0, state:0, ready:false, queue:[]}));
const connected = names.map(() => 0), opens = names.map(() => 0), packs = names.map(() => 0);
const errors = [];
let clicks=0, commands=0, closes=0, entities=0, cookieRequests=0, packChecks=0;
function flush(i) {
  const c=clients[i], w=windows[i];
  while(c && w.ready && w.queue.length) {
    const [slot,button,action]=w.queue.shift();
    c.write('window_click',{windowId:w.id,stateId:w.state,slot,mouseButton:button,mode:action,changedSlots:[],cursorItem:{itemCount:0}});
    clicks++;
    console.log('CAPTCHA_CLIENT CLICK',i,slot,button,action,w.id,w.state);
  }
}
function connect(i) {
  const c=clients[i]=mc.createClient({host:'127.0.0.1',port:Number(process.argv[2]),username:names[i],version:'1.21.4',auth:'offline'});
  windows[i]={id:0,state:0,ready:false,queue:[]};
  // Handshake intent 3 is the real 1.20.5+ transferred connection.
  if(names[i]==='CaptchaSso') {
    const write=c.write;
    c.write=function(name, data) {
      if(name==='set_protocol') data={...data,nextState:3};
      return write.call(this,name,data);
    };
  }
  c.on('position',p=>{connected[i]++;c.write('teleport_confirm',{teleportId:p.teleportId});});
  c.on('open_window',p=>{const w=windows[i];w.id=p.windowId;w.state=0;w.ready=false;opens[i]++;});
  c.on('window_items',p=>{const w=windows[i];if(p.windowId===w.id){w.state=p.stateId;w.ready=true;flush(i);}});
  c.on('set_slot',p=>{const w=windows[i];if(p.windowId===w.id)w.state=p.stateId;});
  c.on('close_window',p=>{const w=windows[i];if(p.windowId===w.id)w.ready=false;});
  c.on('add_resource_pack',p=>{packs[i]++;console.log('CAPTCHA_CLIENT PACK',i,packs[i],p.url);});
  c.on('cookie_request',p=>{
    cookieRequests++;
    let value=null;
    if(names[i]==='CaptchaSso' && p.cookie==='kingconnect:sso_token') {
      const raw=`${names[i]}|test-uuid|test-ip|default|${Date.now()}|hub@test.invalid:25565`;
      const sig=crypto.createHmac('sha256','kingcaptcha-test-secret-2026').update(raw).digest('hex');
      value=Buffer.from(raw+'|'+sig,'utf8');
    }
    c.write('cookie_response',{key:p.cookie,value});
    console.log('CAPTCHA_CLIENT COOKIE',i,p.cookie,!!value);
  });
  c.on('error',e=>{errors.push(names[i]+': '+String(e));console.log('CAPTCHA_CLIENT ERROR',names[i],String(e));});
  c.on('end',why=>console.log('CAPTCHA_CLIENT END',i,String(why)));
}
names.forEach((_,i)=>connect(i));
let input='';
process.stdin.on('data',data=>{
  input+=data;const lines=input.split('\n');input=lines.pop();
  for(const line of lines){
    const [command,...args]=line.trim().split(/\s+/), i=Number(args[0]);
    if(command==='CLICK'){windows[i].queue.push(args.slice(1).map(Number));flush(i);}
    else if(command==='COMMAND'){commands++;clients[i].write('chat_command',{command:args.slice(1).join(' ')});}
    else if(command==='CLOSE'){
      const w=windows[i];if(w.id){clients[i].write('close_window',{windowId:w.id});w.ready=false;closes++;}
    }
    else if(command==='ENTITY'){
      const id=Number(args[1]), action=Number(args[2]);entities++;console.log('CAPTCHA_CLIENT ENTITY',i,id,action);
      if(action===2)clients[i].write('use_entity',{target:id,mouse:2,x:0,y:1,z:0,hand:0,sneaking:false});
      else if(action===1)clients[i].write('use_entity',{target:id,mouse:1,sneaking:false});
      else clients[i].write('use_entity',{target:id,mouse:0,hand:0,sneaking:false});
    }
    else if(command==='MOVE'){console.log('CAPTCHA_CLIENT MOVE',i,args.slice(1).join(' '));clients[i].write('position',{x:Number(args[1]),y:Number(args[2]),z:Number(args[3]),flags:{onGround:true,hasHorizontalCollision:false}});}
    else if(command==='CHAT')clients[i].chat(args.slice(1).join(' '));
    else if(command==='DISCONNECT'){clients[i].end('test disconnect');windows[i].ready=false;}
    else if(command==='RECONNECT')connect(i);
    else if(command==='EXPECTPACK'){
      const expected=Number(args[1]);packChecks++;
      if(packs[i]!==expected){errors.push(`pack ${i}: expected ${expected}, got ${packs[i]}`);console.log('CAPTCHA_CLIENT PACKCHECK FAIL',i,expected,packs[i]);}
      else console.log('CAPTCHA_CLIENT PACKCHECK PASS',i,expected);
    }
    else if(command==='done'){
      const base=connected.every(x=>x>0) && errors.length===0;
      const combined=mode!=='combined' || (clicks>=14 && commands>=4 && closes>=2 && entities>=1 && cookieRequests>=1 && packChecks>=3 && opens[0]>=5);
      const ok=base && combined;
      console.log(ok?'CAPTCHA_CLIENT PASS':'CAPTCHA_CLIENT FAIL',JSON.stringify({mode,connected,opens,packs,clicks,commands,closes,entities,cookieRequests,packChecks,errors}));
      for(const client of clients) if(client) client.end('test complete');
      setTimeout(()=>process.exit(ok?0:3),300);
    }
  }
});
setTimeout(()=>{for(const client of clients) if(client)client.end('timeout');process.exit(2)},240000);
