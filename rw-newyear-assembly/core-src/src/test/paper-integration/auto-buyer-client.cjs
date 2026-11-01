const mc=require('minecraft-protocol');
let digs=0;
const clients=['EcoSeller','EcoBuyer'].map(username=>{
 const c=mc.createClient({host:'127.0.0.1',port:Number(process.argv[2]),username,version:'1.21.4',auth:'offline'});
 c.on('position',p=>c.write('teleport_confirm',{teleportId:p.teleportId}));
 c.on('error',e=>console.log('CLIENT_ERROR',username,String(e)));
 c.on('end',why=>console.log('CLIENT_END',username,String(why)));
 return c;
});
let input='';process.stdin.on('data',data=>{
 input+=data;const lines=input.split('\n');input=lines.pop();
 for(const line of lines){
  const [cmd,x,y,z]=line.trim().split(/\s+/);
  if(cmd==='DIG'){
   const location={x:Number(x),y:Number(y),z:Number(z)};digs++;
   clients[1].write('block_dig',{status:0,location,face:1,sequence:digs*2});
   setTimeout(()=>{if(!clients[1].ended)clients[1].write('block_dig',{status:2,location,face:1,sequence:digs*2+1});},1600);
  }
  if(cmd==='done'){
   console.log('AUTO_NETWORK '+JSON.stringify({digs}));
   for(const c of clients)c.end('test complete');setTimeout(()=>process.exit(0),300);
  }
 }
});
setTimeout(()=>{for(const c of clients)c.end('timeout');process.exit(2)},420000);
