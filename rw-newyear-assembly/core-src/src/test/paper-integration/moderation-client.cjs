const mc = require('minecraft-protocol');
let client;
function connect() {
  client = mc.createClient({host:'127.0.0.1',port:Number(process.argv[2]),username:'CaseTester',version:'1.21.4',auth:'offline'});
  client.on('position', p => client.write('teleport_confirm',{teleportId:p.teleportId}));
  client.on('error', e => console.log('CLIENT_ERROR',String(e)));
  client.on('end', reason => console.log('CLIENT_END',String(reason)));
}
connect();
let buffer='';
process.stdin.on('data',bytes=>{
  buffer+=bytes;const lines=buffer.split('\n');buffer=lines.pop();
  for(const line of lines){
    if(line.trim()==='RECONNECT') connect();
    if(line.trim()==='DISCONNECT' && client) client.end('deliberate test quit');
    if(line.trim()==='done'){if(client)client.end('test complete');setTimeout(()=>process.exit(0),300);}
  }
});
setTimeout(()=>{if(client)client.end('timeout');process.exit(2)},420000);
