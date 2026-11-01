// Three real offline-mode protocol players; the probe issues server-side commands.
const mc = require('minecraft-protocol');
const port = Number(process.argv[2]);
const clients = ['PlayerA', 'PlayerB', 'PlayerC'].map(username => mc.createClient({
  host:'127.0.0.1', port, username, version:'1.21.4', auth:'offline'
}));
for (const client of clients) {
  client.on('position', p => client.write('teleport_confirm', {teleportId:p.teleportId}));
  client.on('error', e => console.error('CLIENT_ERROR', String(e)));
  client.on('end', reason => console.log('CLIENT_END', String(reason)));
}
let buffer='';
process.stdin.on('data', data => {
  buffer+=data;const lines=buffer.split('\n');buffer=lines.pop();
  for (const line of lines) if (line==='done') {
    for(const client of clients)client.end('test complete');
    setTimeout(()=>process.exit(0),300);
  }
});
setTimeout(()=>{for(const client of clients)client.end('timeout');process.exit(2)},420000);
