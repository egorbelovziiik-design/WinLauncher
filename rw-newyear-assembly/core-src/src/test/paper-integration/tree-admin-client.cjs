// Three real offline-mode 1.21.4 clients; inspect each player's own status text.
const mc = require('minecraft-protocol');
const names = ['TreeHelper', 'TreeFriend', 'TreeObserver'];
const port = Number(process.argv[2]);
const clients = names.map(username => mc.createClient({host:'127.0.0.1', port, username, version:'1.21.4', auth:'offline'}));
const unlimited = [false, false, false];
const quota = [false, false, false];
clients.forEach((client, index) => {
  client.on('position', packet => client.write('teleport_confirm', {teleportId:packet.teleportId}));
  client.on('error', error => console.log('CLIENT_ERROR', names[index], String(error)));
  client.on('end', reason => console.log('CLIENT_END', names[index], String(reason)));
  client.on('packet', (data, meta) => {
    if (meta.name !== 'system_chat') return;
    const text = JSON.stringify(data.content);
    if (text.includes('без суточного лимита')) unlimited[index] = true;
    if (text.includes('10/10')) quota[index] = true;
  });
});
let buffer = '';
process.stdin.on('data', data => {
  buffer += data;
  const lines = buffer.split('\n'); buffer = lines.pop();
  for (const line of lines) if (line === 'done') {
    console.log('TREE_ADMIN_NETWORK ' + JSON.stringify({unlimited, quota}));
    for (const client of clients) client.end('test complete');
    setTimeout(() => process.exit(0), 300);
  }
});
setTimeout(() => { for (const client of clients) client.end('timeout'); process.exit(2); }, 420000);
