// Two actual offline-protocol clients. Gameplay assertions are server-side.
const mc = require('minecraft-protocol');
const port = Number(process.argv[2]);
const clients = ['OrbTester', 'OrbEnemy'].map(username => mc.createClient({
  host: '127.0.0.1', port, username, version: '1.21.4', auth: 'offline'
}));
for (const c of clients) {
  c.on('error', e => console.error('CLIENT_ERROR', String(e)));
  c.on('end', reason => console.log('CLIENT_END', String(reason)));
  c.on('position', packet => c.write('teleport_confirm', {teleportId: packet.teleportId}));
}
let input = '';
process.stdin.on('data', data => {
  input += data;
  const lines = input.split('\n'); input = lines.pop();
  for (const line of lines) {
    if (line === 'throw' || line === 'throw-off') {
      clients[0].write('use_item', {hand: line === 'throw-off' ? 1 : 0, sequence: 3, rotation: {x: -90, y: 0}});
    } else if (line === 'shoot' || line === 'shoot-off' || line === 'shoot-down' || line === 'crossbow') {
      const shooter = clients[0];
      shooter.write('use_item', {hand: line === 'shoot-off' ? 1 : 0, sequence: 1, rotation: {x: -90, y: line === 'shoot-down' ? 12 : 0}});
      setTimeout(() => shooter.write('block_dig', {status: 5, location: {x: 0, y: 0, z: 0}, face: 0, sequence: 2}), 1400);
      if (line === 'crossbow') setTimeout(() => shooter.write('use_item', {hand: 0, sequence: 4, rotation: {x: -90, y: 0}}), 1900);
    } else if (line === 'done') {
      for (const c of clients) c.end('test complete');
      setTimeout(() => process.exit(0), 300);
    }
  }
});
setTimeout(() => { for (const c of clients) c.end('timeout'); process.exit(2); }, 420000);
