const mc = require('minecraft-protocol')
const names=['WheelOne','WheelTwo']
const clients=names.map(username=>mc.createClient({host:'127.0.0.1',port:Number(process.argv[2]),username,version:'1.21.4',auth:'offline'}))
let connected=[false,false],errors=[]
clients.forEach((c,i)=>{
  c.on('position',p=>{connected[i]=true;c.write('teleport_confirm',{teleportId:p.teleportId})})
  c.on('error',e=>{errors.push(String(e));console.log('CLIENT_ERROR',names[i],String(e))})
})
let input=''
process.stdin.on('data',chunk=>{
  input+=chunk;const lines=input.split('\n');input=lines.pop()
  for(const line of lines){
    const [cmd,id]=line.trim().split(/\s+/)
    if(cmd==='NPC') clients[0].write('use_entity',{target:Number(id),mouse:0,hand:0,sneaking:false})
    if(cmd==='done'){
      const ok=connected.every(Boolean)&&errors.length===0
      console.log(ok?'WHEEL_CLIENT PASS':'WHEEL_CLIENT FAIL',JSON.stringify({connected,errors}))
      for(const c of clients)c.end('done')
      setTimeout(()=>process.exit(ok?0:3),300)
    }
  }
})
setTimeout(()=>{for(const c of clients)c.end('timeout');process.exit(2)},420000)
