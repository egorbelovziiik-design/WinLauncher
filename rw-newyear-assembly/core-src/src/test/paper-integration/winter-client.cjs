const mc = require('minecraft-protocol');
const c = mc.createClient({host:'127.0.0.1', port:Number(process.argv[2]), username:'WinterTester', version:'1.21.4', auth:'offline'});
let sequence = 1;
c.on('position', packet => c.write('teleport_confirm', {teleportId: packet.teleportId}));
c.on('error', e => console.log('CLIENT_ERROR', String(e)));
c.on('end', why => console.log('CLIENT_END', String(why)));
let input='';
process.stdin.on('data', chunk => {
  input += chunk;
  const lines=input.split('\n'); input=lines.pop();
  for(const line of lines) {
    if (line === 'use' || line === 'off') c.write('use_item', {hand:line === 'off' ? 1 : 0, sequence:sequence++, rotation:{x:0,y:0}});
    if (line === 'done') { c.end('test complete'); setTimeout(() => process.exit(0),300); }
  }
});
setTimeout(() => {c.end('timeout'); process.exit(2)}, 420000);
