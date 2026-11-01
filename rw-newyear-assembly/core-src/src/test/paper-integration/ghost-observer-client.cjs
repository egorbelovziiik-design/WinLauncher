// Two real protocol clients for the disposable replay-privacy restart probe.
const mc = require('minecraft-protocol')
const port = Number(process.argv[2])
const seen = [new Set(), new Set()]
const signals = [null, null]
const clients = ['ParkourRunner','GhostObserver'].map(username => mc.createClient({
  host:'127.0.0.1', port, username, version:'1.21.4', auth:'offline'
}))
for (let i=0;i<clients.length;i++) {
  const client=clients[i]
  client.on('error', error => console.error('GHOST_CLIENT_ERROR', String(error)))
  client.on('position', packet => client.write('teleport_confirm', {teleportId:packet.teleportId}))
  client.on('packet', (data, metadata) => {
    if (metadata.name==='spawn_entity') seen[i].add(data.entityId)
    if (metadata.name==='system_chat') {
      const match=JSON.stringify(data.content).match(/KG_GHOST_IDS=([0-9,]+) MARKER=([0-9]+)/)
      if (match) signals[i]={ghost:match[1].split(',').map(Number), marker:Number(match[2])}
    }
  })
}
process.stdin.on('data', data => {
  if (!String(data).includes('done')) return
  const a=signals[0], b=signals[1]
  const ok=!!a&&!!b&&a.ghost.length===6&&a.ghost.join(',')===b.ghost.join(',')&&a.marker===b.marker
    &&seen[0].has(a.marker)&&seen[1].has(a.marker)
    &&a.ghost.every(id=>seen[0].has(id))&&a.ghost.every(id=>!seen[1].has(id))
  console.log('GHOST_NETWORK_'+(ok?'PASS':'FAIL'), JSON.stringify({signals,seen:seen.map(s=>[...s])}))
  for (const client of clients) client.end('test complete')
  setTimeout(() => process.exit(ok?0:1), 300)
})
setTimeout(() => {for (const client of clients) client.end('timeout');process.exit(2)},180000)
