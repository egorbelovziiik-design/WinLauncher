// Three protocol players for server-triggered combat scenarios.
const mc = require('minecraft-protocol');
const port = Number(process.argv[2]);
const clients = ['FightTester', 'FightTarget', 'FightOther'].map(username => mc.createClient({
  host: '127.0.0.1', port, username, version: '1.21.4', auth: 'offline'
}));
for (const client of clients) {
  client.on('position', p => client.write('teleport_confirm', {teleportId: p.teleportId}));
  client.on('error', e => console.error('CLIENT_ERROR', String(e)));
  client.on('end', reason => console.log('CLIENT_END', String(reason)));
}
let input = '';
process.stdin.on('data', data => {
  input += data; const lines = input.split('\n'); input = lines.pop();
  for (const line of lines) if (line.trim() === 'done') {
    for (const client of clients) client.end('test complete');
    setTimeout(() => process.exit(0), 300);
  }
});
setTimeout(() => {for (const client of clients) client.end('timeout');process.exit(2)}, 420000);
