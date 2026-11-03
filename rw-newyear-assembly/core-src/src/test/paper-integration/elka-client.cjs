// Three real protocol clients record vanilla BlockDisplay spawn packets.
const mc = require('minecraft-protocol')
const data = require('minecraft-data')('1.21.4')
const names = ['TreeHelper', 'TreeFriend', 'TreeObserver']
const clients = names.map(username => mc.createClient({host:'127.0.0.1', port:Number(process.argv[2]), username, version:'1.21.4', auth:'offline'}))
const seen = [0,0,0]
const display = data.entitiesByName.block_display.id
clients.forEach((client,index)=>{
  client.on('position',packet=>client.write('teleport_confirm',{teleportId:packet.teleportId}))
  client.on('spawn_entity',packet=>{if(packet.type===display)seen[index]++})
  client.on('error',error=>console.log('CLIENT_ERROR',names[index],String(error)))
})
let buffer=''
process.stdin.on('data',chunk=>{
  buffer+=chunk;const lines=buffer.split('\n');buffer=lines.pop()
  for(const line of lines)if(line==='done'){
    console.log('ELKA_NETWORK '+JSON.stringify(seen))
    for(const client of clients)client.end('done')
    setTimeout(()=>process.exit(0),300)
  }
})
setTimeout(()=>{for(const client of clients)client.end('timeout');process.exit(2)},420000)
