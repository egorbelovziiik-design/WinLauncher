// Three real offline-mode players; PlayerA follows an actual history component.
const mc = require('minecraft-protocol');
const port = Number(process.argv[2]);
const names = ['TreeHelper', 'TreeFriend', 'TreeObserver'];
const clients = names.map(username => mc.createClient({host:'127.0.0.1', port, username, version:'1.21.4', auth:'offline'}));
let clicked = 0, observerCopies = 0, disconnected = false;
clients.forEach((client,index) => {
  client.on('position', packet => client.write('teleport_confirm',{teleportId:packet.teleportId}));
  client.on('error', error => console.log('CLIENT_ERROR', names[index], String(error)));
  client.on('end', reason => console.log('CLIENT_END', names[index], String(reason)));
  client.on('packet', (data,meta) => {
    if (process.env.KG_TREE_PHASE !== '1' || meta.name !== 'system_chat') return;
    const text = JSON.stringify(data.content);
    if (!text.includes('/tree claim legacy')) return;
    if (index === 0 && !clicked) {
      clicked++;
      client.write('chat_command',{command:'tree claim legacy'});
      console.log('TREE_HISTORY_CLICKED');
    } else if (index !== 0) observerCopies++;
  });
});
let buffer='';
process.stdin.on('data', data => {
  buffer += data;
  const lines=buffer.split('\n'); buffer=lines.pop();
  for (const line of lines) {
    if (line==='DISCONNECT_C' && !disconnected) {
      disconnected=true; clients[2].end('offline for archived season'); console.log('TREE_OBSERVER_DISCONNECTED');
    }
    if (line==='done') {
      console.log('TREE_NETWORK '+JSON.stringify({clicked,observerCopies,disconnected}));
      for (const client of clients) client.end('test complete');
      setTimeout(()=>process.exit(0),300);
    }
  }
});
setTimeout(()=>{for(const c of clients)c.end('timeout');process.exit(2)},420000);
