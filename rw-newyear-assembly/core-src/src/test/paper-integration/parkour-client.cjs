// A real offline protocol client with prismarine physics, not a renderer.
// Walk off the first platform to test rescue, then physically run all 32 pads.
const mineflayer = require('mineflayer')
const { Vec3 } = require('vec3')
const fs = require('fs')
const path = require('path')
const { once } = require('events')
const layoutPath = path.resolve(__dirname, '../../main/resources/parkour_v74.csv')
const pads = fs.readFileSync(layoutPath, 'ascii').split('\n').filter(s => s && !s.startsWith('#')).map(line => {
  const [i,x,y,z,half,kind] = line.split(',')
  return {i:+i,x:+x,y:+y,z:+z,half:+half,kind}
})
const bot = mineflayer.createBot({host:'127.0.0.1',port:Number(process.argv[2]),username:'ParkourRunner',version:'1.21.4',auth:'offline'})
let ready=false, finished=false, ended=false
bot.on('messagestr', msg => {
  if (msg.includes('KG_PARKOUR_PROBE_READY')) ready=true
  if (msg.includes('KG_PARKOUR_REAL_FINISH_OK')) finished=true
  if (/ПАРКУР|Чекпоинт|KG_PARKOUR/.test(msg)) console.log('PARKOUR_CHAT',msg)
})
bot.on('kicked', reason => {console.error('PARKOUR_KICKED',JSON.stringify(reason));ended=true})
bot.on('error', error => {console.error('PARKOUR_ERROR',String(error));ended=true})
bot.on('end', reason => {console.log('PARKOUR_END',String(reason));ended=true})
function clear () { bot.clearControlStates() }
function pos () { return bot.entity.position }
function atPad (p, margin=0.25) {
  let v=pos()
  return Math.abs(v.x-(p.x+.5))<=p.half+.5+margin && Math.abs(v.z-(p.z+.5))<=p.half+.5+margin &&
    Math.abs(v.y-(p.y+1))<0.3 && bot.entity.onGround
}
async function tick () { if(ended) throw new Error('client disconnected'); await once(bot,'physicsTick') }
async function wait (predicate, maxTicks, label) {
  for(let t=0;t<maxTicks;t++) { if(predicate()) return; await tick() }
  throw new Error('timeout '+label+' at '+pos().toString())
}
async function lookToward (x,z) {
  await bot.lookAt(new Vec3(x,pos().y+1.62,z),true)
}
async function earlyFall () {
  const start=pads[0]
  await wait(() => atPad(start),180,'landing on start')
  // Walk down the staircase onto the plaza, without jumping. The shallow fall
  // used to strand players because it was below the old Y>=105 threshold.
  await lookToward(start.x+.5,-18)
  bot.setControlState('forward',true)
  let touchedPlaza=false
  for(let t=0;t<160;t++) {
    await tick()
    if(pos().y<100.8) touchedPlaza=true
    if(touchedPlaza && atPad(start)) {
      clear(); console.log('PARKOUR_EARLY_FALL_RESCUED',pos().toString());
      await bot.waitForTicks(8); return
    }
  }
  clear(); throw new Error('shallow early fall was not returned; plaza='+touchedPlaza+' at '+pos().toString())
}
async function checkpointFall (checkpoint) {
  await wait(() => atPad(checkpoint),80,'standing on first checkpoint')
  // Step off towards the plaza and wait for a real mid-air safety return.
  await lookToward(checkpoint.x+.5,checkpoint.z+14)
  bot.setControlState('forward',true)
  let fell=false
  for(let t=0;t<180;t++) {
    await tick()
    if(pos().y < checkpoint.y-.5) fell=true
    if(fell && atPad(checkpoint)) {
      clear(); console.log('PARKOUR_CHECKPOINT_FALL_RESCUED',pos().toString());
      await bot.waitForTicks(8); return
    }
  }
  clear();throw new Error('checkpoint fall was not returned; fell='+fell+' at '+pos().toString())
}
async function jump (from,to) {
  await wait(() => atPad(from),80,'standing on step '+from.i)
  const targetX=to.x+.5,targetZ=to.z+.5
  const dx=targetX-pos().x,dz=targetZ-pos().z, length=Math.hypot(dx,dz), ux=dx/length,uz=dz/length
  await lookToward(targetX,targetZ)
  bot.setControlState('sprint',true);bot.setControlState('forward',true)
  let jumped=false, airborne=false, released=false
  for(let t=0;t<75;t++) {
    const v=pos()
    // Distance of the player's centre to the safe edge in its direction of travel.
    const edgeX=ux>0 ? (from.x+from.half+1-.32-v.x)/ux : ux<0 ? (from.x-from.half+.32-v.x)/ux : Infinity
    const edgeZ=uz>0 ? (from.z+from.half+1-.32-v.z)/uz : uz<0 ? (from.z-from.half+.32-v.z)/uz : Infinity
    const edge=Math.min(edgeX,edgeZ)
    // If the destination is higher, jump BEFORE meeting its vertical side.
    // Some wide checkpoint edges overlap the next pad in projection.
    const entryAxis=(v,min,max,u) => v<min ? (u>0 ? (min-v)/u : Infinity)
      : v>max ? (u<0 ? (max-v)/u : Infinity) : 0
    const collisionEntry=Math.max(entryAxis(v.x,to.x-to.half-.30,to.x+to.half+1+.30,ux),
                                  entryAxis(v.z,to.z-to.half-.30,to.z+to.half+1+.30,uz))
    if(process.env.KG_PARKOUR_DEBUG && t<45) console.log('PARKOUR_DEBUG',from.i,t,JSON.stringify({p:[v.x,v.y,v.z],on:bot.entity.onGround,edge,collisionEntry,jumped,airborne,released}))
    if(!jumped && bot.entity.onGround && (edge<=.55 || (to.y>from.y && collisionEntry<=1.05))) {
      bot.setControlState('jump',true); jumped=true
    } else if(jumped) bot.setControlState('jump',false)
    if(jumped && !bot.entity.onGround) airborne=true
    const distance=Math.hypot(targetX-v.x,targetZ-v.z)
    if(jumped && distance<.8 && !released) {
      bot.setControlState('forward',false);bot.setControlState('sprint',false);released=true
    }
    if(jumped && airborne && atPad(to)) {
      clear();await bot.waitForTicks(4)
      if(atPad(to)) { console.log('PARKOUR_JUMP_PASS',from.i+'->'+to.i,pos().toString());return }
    }
    if(finished && to.i===31) { clear();console.log('PARKOUR_JUMP_PASS',from.i+'->'+to.i,'finish');return }
    await tick()
  }
  clear(); throw new Error('jump '+from.i+'->'+to.i+' failed, jumped='+jumped+' air='+airborne+' at '+pos().toString())
}
async function main () {
  await once(bot,'spawn')
  await wait(() => ready,600,'probe setup')
  await earlyFall()
  for(let i=0;i<pads.length-1;i++) {
    await jump(pads[i],pads[i+1])
    if(i+1===8) await checkpointFall(pads[8])
  }
  await wait(() => finished,100,'actual prize confirmation')
  console.log('PARKOUR_CLIENT_RESULT PASS (start and checkpoint falls, 31 real jumps)')
  bot.quit();process.exit(0)
}
main().catch(error => {console.error('PARKOUR_CLIENT_RESULT FAIL',error.stack||String(error));try{clear();bot.quit()}catch{}process.exit(1)})
setTimeout(() => {console.error('PARKOUR_CLIENT_TIMEOUT');process.exit(2)},240000)
