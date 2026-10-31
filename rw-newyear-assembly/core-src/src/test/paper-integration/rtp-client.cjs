const mc=require('minecraft-protocol');
const clients=['RtpTesterA','RtpTesterB','RtpTesterC'].map(username=>{
 const c=mc.createClient({host:'127.0.0.1',port:Number(process.argv[2]),username,version:'1.21.4',auth:'offline'});
 c.on('position',p=>c.write('teleport_confirm',{teleportId:p.teleportId}));
 c.on('error',e=>console.log('CLIENT_ERROR',username,String(e)));c.on('end',why=>console.log('CLIENT_END',username,String(why)));return c;
});
let input='';process.stdin.on('data',chunk=>{input+=chunk;const lines=input.split('\n');input=lines.pop();for(const line of lines)if(line.trim()==='done'){for(const c of clients)c.end('test complete');setTimeout(()=>process.exit(0),300);}});
setTimeout(()=>{for(const c of clients)c.end('timeout');process.exit(2)},420000);
