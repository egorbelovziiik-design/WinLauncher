// Two real protocol clients for the disposable replay-privacy restart probe.
const mc = require('minecraft-protocol')
const port = Number(process.argv[2])
const clients = ['ParkourRunner','GhostObserver'].map(username => mc.createClient({
  host:'127.0.0.1', port, username, version:'1.21.4', auth:'offline'
}))
for (const client of clients) {
  client.on('error', error => console.error('GHOST_CLIENT_ERROR', String(error)))
  client.on('position', packet => client.write('teleport_confirm', {teleportId:packet.teleportId}))
}
process.stdin.on('data', data => {
  if (String(data).includes('done')) {
    for (const client of clients) client.end('test complete')
    setTimeout(() => process.exit(0), 300)
  }
})
setTimeout(() => {for (const client of clients) client.end('timeout');process.exit(2)},180000)
