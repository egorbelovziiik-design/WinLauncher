const mc=require('minecraft-protocol');
const port=Number(process.argv[2]);
const clients=['TrainTester','TrainOther'].map(username=>mc.createClient({host:'127.0.0.1',port,username,version:'1.21.4',auth:'offline'}));
let trainingBars=[0,0];
clients.forEach((client,i)=>{
 client.on('position',p=>client.write('teleport_confirm',{teleportId:p.teleportId}));
 client.on('action_bar',p=>{if(JSON.stringify(p.text).includes('DPS 5'))trainingBars[i]++;});
 client.on('error',e=>console.error('CLIENT_ERROR',String(e)));
 client.on('end',reason=>console.log('CLIENT_END',String(reason)));
});
let input='';process.stdin.on('data',data=>{
 input+=data;const lines=input.split('\n');input=lines.pop();
 for(const line of lines){const [cmd,who,id,count]=line.trim().split(/\s+/);
  if(cmd==='ATTACK'){
   const client=clients[who==='B'?1:0];for(let i=0;i<(Number(count)||1);i++)client.write('use_entity',{target:Number(id),mouse:1,sneaking:false});
   console.log('TRAIN_ATTACK',who,id,count||1);
  }else if(cmd==='AIR'){
   clients[0].write('flying',{flags:{onGround:false,hasHorizontalCollision:false}});console.log('TRAIN_AIR');
  }else if(cmd==='REPORT')console.log('TRAIN_NETWORK',JSON.stringify({trainingBars}));
  else if(cmd==='done'){for(const c of clients)c.end('test complete');setTimeout(()=>process.exit(0),300);}
 }
});
setTimeout(()=>{for(const c of clients)c.end('timeout');process.exit(2)},420000);
