const mc = require('minecraft-protocol');
const client = mc.createClient({host:'127.0.0.1',port:Number(process.argv[2]),username:process.argv[3] || 'SpawnVisitor',version:'1.21.4',auth:'offline'});
client.on('position', p => { client.write('teleport_confirm',{teleportId:p.teleportId}); console.log('SPAWN_CLIENT_POSITION',p.x,p.y,p.z); });
client.on('add_resource_pack', p => client.write('resource_pack_receive',{uuid:p.uuid,result:1}));
client.on('cookie_request', p => client.write('cookie_response',{key:p.key,value:undefined}));
client.on('error', e => {console.error('SPAWN_CLIENT_ERROR',String(e));process.exitCode=1;});
client.on('end', reason => {console.log('SPAWN_CLIENT_END',String(reason));process.exit();});
setTimeout(() => {console.error('SPAWN_CLIENT_TIMEOUT');client.end('timeout');process.exit(2);},90000);
