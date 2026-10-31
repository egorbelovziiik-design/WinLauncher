const mc = require('minecraft-protocol');
const headId = require('minecraft-data')('1.21.4').itemsByName.player_head.id;
const allowed = new Set([
  '14e424b1676feec3a3f8ebade9e7d6a6f71f7756a869f36f7df0fc182d436e',
  'bca614e14e5035f11a3ad5ebe69c2d912e60839f43a8a2517ea057f18f7db093',
  '7e1b346249aef655f81a8bd58cc4b35f139d1e86b6217b11b8e981445cb5118d',
  'b4f32011d6b260442d99397d70356c1fcaeb064c144f138d13568e6ec648',
  '16c0de7dd65387482c3ab5d3cb4eaa921a6aeeddf38e3df20e04fd961faef43',
  '4f99dc32ad4edad52985efd7e3aeb42b436540713736742f6d9a3b91c35ef871',
  'bfd4308763ffd5e2631c74d0be2ff49a90bac49053618c28882e75c4dcce22ac',
  'e04568737bc3890b8624d97b1d2736419633b9116fc9c5273355a30a7797875f'
]);
const seen = new Set(), errors = [];
const c = mc.createClient({host:'127.0.0.1', port:Number(process.argv[2]), username:'NpcTester', version:'1.21.4', auth:'offline'});
c.on('position', p => c.write('teleport_confirm', {teleportId:p.teleportId}));
// Check what Paper actually sends to a client, not just the Bukkit object.
c.on('entity_metadata', p => {
  for (const m of p.metadata) {
    if (m.key !== 23 || m.type !== 'item_stack' || m.value.itemId !== headId) continue;
    const profile = (m.value.components || []).find(component => component.type === 'profile')?.data;
    if (!profile?.name?.startsWith('KGS_')) continue; // not one of Core's textured NPC heads
    try {
      const property = (profile.properties || []).find(prop => prop.name === 'textures');
      const url = JSON.parse(Buffer.from(property.value, 'base64').toString('utf8')).textures.SKIN.url;
      const prefix = 'https://textures.minecraft.net/texture/';
      const hash = url.startsWith(prefix) ? url.slice(prefix.length) : '';
      if (!allowed.has(hash) || profile.name !== 'KGS_' + hash.slice(0,10)) throw new Error('unexpected skin URL/profile');
      seen.add(hash);
    } catch (error) { errors.push(String(error)); }
  }
});
c.on('error', e => { errors.push(String(e)); console.log('CLIENT_ERROR', String(e)); });
c.on('end', why => console.log('CLIENT_END', String(why)));
let input='';
process.stdin.on('data', chunk => {
  input += chunk; const lines=input.split('\n'); input=lines.pop();
  for(const line of lines) {
    const [cmd,id] = line.trim().split(/\s+/); const target=Number(id);
    if (cmd === 'RIGHT' || cmd === 'OFF') c.write('use_entity',{target,mouse:0,hand:cmd === 'OFF' ? 1 : 0,sneaking:false});
    else if (cmd === 'AT') c.write('use_entity',{target,mouse:2,x:0,y:1,z:0,hand:0,sneaking:false});
    else if (cmd === 'LEFT') c.write('use_entity',{target,mouse:1,sneaking:false});
    else if (cmd === 'done') {
      const maiden = 'bca614e14e5035f11a3ad5ebe69c2d912e60839f43a8a2517ea057f18f7db093';
      const ok = seen.size === allowed.size && seen.has(maiden) && errors.length === 0;
      console.log(ok ? 'NPC_CLIENT_TEXTURES PASS' : 'NPC_CLIENT_TEXTURES FAIL', 'unique='+seen.size, 'snowMaiden='+seen.has(maiden), errors.join(';'));
      c.end('test complete'); setTimeout(() => process.exit(ok ? 0 : 3),300);
    }
  }
});
setTimeout(() => {c.end('timeout');process.exit(2)},420000);
