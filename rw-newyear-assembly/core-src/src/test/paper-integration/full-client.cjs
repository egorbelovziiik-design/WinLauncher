const mc = require('minecraft-protocol');
const client = mc.createClient({host:'127.0.0.1',port:Number(process.argv[2]),username:'FullTester',version:'1.21.4',auth:'offline'});
let position = false, prepared = false, packCount = 0;
client.on('add_resource_pack', packet => {
  packCount++;
  console.log('FULL_CLIENT_DECLINED_PACK', packet.url);
  if (packet.url !== 'https://kinggrief.vercel.app/kinggrief-mega-rp-v72.zip' || packet.hash !== '1729e4f29ad7fded64ab2f23c8b27f783936cd3d' || packet.forced) {
    console.error('FULL_CLIENT_UNEXPECTED_PACK'); process.exit(1);
  }
  // A normal Minecraft client responds to the optional configuration-phase pack.
  client.write('resource_pack_receive', {uuid:packet.uuid,result:1});
});
client.on('cookie_request', packet => client.write('cookie_response', {key:packet.key,value:undefined}));
client.on('position', packet => {
  client.write('teleport_confirm', {teleportId:packet.teleportId});
  if (Math.abs(packet.x - 0.5) < 1 && Math.abs(packet.y - 100) < 1 && Math.abs(packet.z - 52.5) < 1) {
    prepared = true;
    console.log('FULL_CLIENT_PREPARED_SPAWN_POSITION', packet.x, packet.y, packet.z);
  }
  if (!position) {
    position = true;
    console.log('FULL_CLIENT_POSITION');
    setTimeout(() => client.write('chat_command', {command:'register smoke123 smoke123'}), 300);
    setTimeout(() => { client.end('smoke complete'); if (packCount !== 1) console.error('FULL_CLIENT_PACK_COUNT', packCount); process.exit(packCount === 1 && prepared ? 0 : 1); }, 8000);
  }
});
client.on('error', error => {console.error('FULL_CLIENT_ERROR',String(error));process.exit(1);});
client.on('end', why => {if (!position) {console.error('FULL_CLIENT_EARLY_END',String(why));process.exit(1);}});
setTimeout(() => {console.error('FULL_CLIENT_TIMEOUT');process.exit(1);},60000);
