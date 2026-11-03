const mc = require('minecraft-protocol')
const port = Number(process.argv[2]); let clients = []
function login(name) {
  const client = mc.createClient({host:'127.0.0.1',port,username:name,version:'1.21.4',auth:'offline'})
  clients.push(client)
  client.on('position', p => client.write('teleport_confirm',{teleportId:p.teleportId}))
  client.on('error',e=>console.log('CLIENT',name,'ERROR',String(e)))
  client.on('end',why=>console.log('CLIENT',name,'END',String(why)))
}
let input=''
process.stdin.on('data',chunk=>{
 input+=chunk;const lines=input.split('\n');input=lines.pop()
 for(const line of lines){
  const [cmd,name]=line.trim().split(/\s+/)
  if(cmd==='LOGIN') login(name)
  if(cmd==='done') { for(const c of clients)try{c.end('done')}catch(_){};setTimeout(()=>process.exit(0),300) }
 }
})
setTimeout(()=>process.exit(2),420000)
