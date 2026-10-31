const mc = require('minecraft-protocol');
const c = mc.createClient({host:'127.0.0.1', port:Number(process.argv[2]), username:'NpcTester', version:'1.21.4', auth:'offline'});
c.on('position', p => c.write('teleport_confirm', {teleportId:p.teleportId}));
c.on('error', e => console.log('CLIENT_ERROR', String(e)));
c.on('end', why => console.log('CLIENT_END', String(why)));
let input='';
process.stdin.on('data', chunk => {
  input += chunk; const lines=input.split('\n'); input=lines.pop();
  for(const line of lines) {
    const [cmd,id] = line.trim().split(/\s+/); const target=Number(id);
    if (cmd === 'RIGHT' || cmd === 'OFF') c.write('use_entity',{target,mouse:0,hand:cmd === 'OFF' ? 1 : 0,sneaking:false});
    else if (cmd === 'AT') c.write('use_entity',{target,mouse:2,x:0,y:1,z:0,hand:0,sneaking:false});
    else if (cmd === 'LEFT') c.write('use_entity',{target,mouse:1,sneaking:false});
    else if (cmd === 'done') { c.end('test complete'); setTimeout(() => process.exit(0),300); }
  }
});
setTimeout(() => {c.end('timeout');process.exit(2)},420000);
