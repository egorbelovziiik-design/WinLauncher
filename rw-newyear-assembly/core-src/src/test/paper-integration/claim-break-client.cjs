const mc=require('minecraft-protocol');
let placements=0;
const clients=['EcoSeller','EcoBuyer'].map(username=>{
 const c=mc.createClient({host:'127.0.0.1',port:Number(process.argv[2]),username,version:'1.21.4',auth:'offline'});
 c.on('position',p=>c.write('teleport_confirm',{teleportId:p.teleportId}));
 c.on('error',e=>console.log('CLIENT_ERROR',username,String(e)));c.on('end',why=>console.log('CLIENT_END',username,String(why)));
 return c;
});
let input='';process.stdin.on('data',data=>{input+=data;const lines=input.split('\n');input=lines.pop();for(const line of lines){
 const [cmd,x,y,z]=line.trim().split(/\s+/);
 if(cmd==='PLACE'){
  placements++;clients[1].write('block_place',{hand:0,location:{x:Number(x),y:Number(y),z:Number(z)},direction:1,
    cursorX:0.5,cursorY:1,cursorZ:0.5,insideBlock:false,worldBorderHit:false,sequence:placements});
 }
 if(cmd==='done'){
  console.log('CLAIM_NETWORK '+JSON.stringify({placements}));
  for(const c of clients)c.end('test complete');setTimeout(()=>process.exit(0),300);
 }
}});
setTimeout(()=>{for(const c of clients)c.end('timeout');process.exit(2)},420000);
