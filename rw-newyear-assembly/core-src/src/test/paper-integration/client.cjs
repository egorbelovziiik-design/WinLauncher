const mc = require('minecraft-protocol');
const wanted = 'https://kinggrief.vercel.app/kinggrief-mega-rp-v65.zip';
const expectedHash = 'fc61175f0692ab85486898160dce223097aa4382';
const c = mc.createClient({host:'127.0.0.1', port:Number(process.argv[2]), username:'ProbeTester', version:'1.21.4', auth:'offline'});
let gotPack = false, gotWindow = false;
c.on('packet', (data,meta) => {
  if (meta.name === 'add_resource_pack') {
    console.log('PACKET', meta.name, JSON.stringify(data));
    if (data.url === wanted && data.hash === expectedHash) {gotPack=true; console.log('KG-WIRE PACK PASS');}
  }
  if (meta.name === 'window_items' && data.windowId !== 0) {
    gotWindow=true; console.log('KG-WIRE MENU PACKET PASS', 'window',data.windowId,'items', data.items?.length);
  }
});
c.on('error', e => console.log('CLIENT_ERROR', String(e)));
c.on('end', why => console.log('CLIENT_END', String(why)));
setTimeout(() => { c.end('probe complete'); setTimeout(() => process.exit(gotPack && gotWindow ? 0 : 2),500); }, 12000);
