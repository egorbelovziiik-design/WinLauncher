// Real offline-mode 1.21.4 clients. The server probe prepares a disposable pond.
const mc = require('minecraft-protocol');
const port = Number(process.argv[2]);
const clients = ['FishTester', 'FishFriend'].map(username => mc.createClient({
  host: '127.0.0.1', port, username, version: '1.21.4', auth: 'offline'
}));
for (const c of clients) {
  c.on('position', p => c.write('teleport_confirm', {teleportId:p.teleportId}));
  c.on('error', e => console.error('CLIENT_ERROR', String(e)));
  c.on('end', reason => console.log('CLIENT_END', String(reason)));
}
let input = '', sequence = 1;
process.stdin.on('data', data => {
  input += data; const lines = input.split('\n'); input = lines.pop();
  for (const line of lines) {
    if (line === 'fish') {
      clients[0].write('use_item', {hand:0,sequence:sequence++,rotation:{x:-90,y:0}});
      console.log('FISH_USE', sequence-1);
    } else if (line === 'done') {
      for (const c of clients) c.end('test complete');
      setTimeout(()=>process.exit(0),300);
    }
  }
});
setTimeout(()=>{for(const c of clients)c.end('timeout');process.exit(2)},420000);
