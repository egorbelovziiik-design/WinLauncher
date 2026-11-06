// Two genuine offline-mode Paper clients observe the restricted error journal.
const mc = require('minecraft-protocol');
const names = ['V714One', 'V714Two'];
const clients = names.map(username => mc.createClient({host:'127.0.0.1',port:Number(process.argv[2]),username,version:'1.21.4',auth:'offline'}));
const connected = [false, false], errors = [], messages = [];
clients.forEach((c, i) => {
  c.on('position', p => { connected[i] = true; c.write('teleport_confirm', {teleportId:p.teleportId}); });
  c.on('system_chat', p => { if (i === 0) messages.push(JSON.stringify(p.content)); });
  c.on('error', e => { errors.push(names[i]+': '+String(e)); });
});
function section(name) {
  const start = messages.findIndex(x=>x.includes('KG714_BEGIN_'+name));
  const end = messages.findIndex((x,i)=>i>start && x.includes('KG714_END_'+name));
  return start>=0 && end>start ? messages.slice(start+1,end).join('\n') : '';
}
let buffer='';
process.stdin.on('data', chunk => {
  buffer += chunk; const lines=buffer.split('\n'); buffer=lines.pop();
  for (const line of lines) if (line.trim()==='done') {
    const player=section('PLAYER'), server=section('SERVER'), wrong=section('WRONG');
    const denied=section('DENIED'), unauth=section('UNAUTH'), frozen=section('FROZEN'), page=section('PAGE');
    const report = {
      connected, errors,
      playerExact: player.includes('ProbePlayerError') && player.includes('V714Two') && !player.includes('ProbeGlobalError'),
      serverErrors: server.includes('ProbeGlobalError') && server.includes('ProbePlayerError') && !server.includes('ProbeWarning'),
      noPrefixMatch: !wrong.includes('ProbePlayerError') && wrong.includes('нет'),
      denied: denied.includes('доступен') && !denied.includes('ProbeGlobalError'),
      unauth: unauth.includes('доступен') && !unauth.includes('ProbeGlobalError'),
      frozen: frozen.includes('доступен') && !frozen.includes('ProbeGlobalError'),
      pagination: page.includes('2/100'),
      noSecrets: !messages.join('\n').match(/SECRET_(?:GLOBAL|PLAYER)_DO_NOT_SEND|PASSWORD_(?:GLOBAL|PLAYER)|TOKEN_(?:GLOBAL|PLAYER)|COOKIE_(?:GLOBAL|PLAYER)|192\.0\.2\.(?:22|99)/),
      sectionCounts: [player,server,wrong,denied,unauth,frozen,page].map(x=>x.length),
    };
    const ok=connected.every(Boolean) && errors.length===0 && report.playerExact && report.serverErrors && report.noPrefixMatch
      && report.denied && report.unauth && report.frozen && report.pagination && report.noSecrets;
    console.log(ok?'V714_CLIENT PASS':'V714_CLIENT FAIL',JSON.stringify(report));
    if (!ok) console.log('V714_SECTIONS',JSON.stringify({player,server,wrong,denied,unauth,frozen,page}));
    for (const c of clients) c.end('done'); setTimeout(()=>process.exit(ok?0:3),300);
  }
});
setTimeout(()=>{clients.forEach(c=>c.end('timeout'));process.exit(2)},420000);
