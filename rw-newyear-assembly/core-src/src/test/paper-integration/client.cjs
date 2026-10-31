const mc = require('minecraft-protocol');
const wanted = 'https://kinggrief.vercel.app/kinggrief-mega-rp-v72.zip';
const expectedHash = '1729e4f29ad7fded64ab2f23c8b27f783936cd3d';
const c = mc.createClient({host:'127.0.0.1', port:Number(process.argv[2]), username:'ProbeTester', version:'1.21.4', auth:'offline'});
let gotPack = false, gotWindow = false, gotChat = false;
c.on('packet', (data,meta) => {
  if (meta.name === 'add_resource_pack') {
    console.log('PACKET', meta.name, JSON.stringify(data));
    if (data.url === wanted && data.hash === expectedHash) {gotPack=true; console.log('KG-WIRE PACK PASS');}
  }
  if (meta.name === 'system_chat' && JSON.stringify(data).includes('CHAT_PROBE')) {
    const components = data.content?.value?.extra?.value?.value || [];
    const message = components.find(part => part.text?.value?.startsWith('CHAT_PROBE'));
    if (message?.text?.value === 'CHAT_PROBE plain red' && message?.color?.value?.toUpperCase() === '#F4A2B1' && message?.bold?.value === 0) {
      gotChat = true; console.log('KG-WIRE CHAT PASS: expected donor color, no bold or injected color');
    } else console.log('KG-WIRE CHAT FAIL', JSON.stringify(data));
  }
  if (meta.name === 'window_items' && data.windowId !== 0) {
    gotWindow=true; console.log('KG-WIRE MENU PACKET PASS', 'window',data.windowId,'items', data.items?.length);
  }
});
c.on('error', e => console.log('CLIENT_ERROR', String(e)));
c.on('end', why => console.log('CLIENT_END', String(why)));
function finish() { c.end('probe complete'); setTimeout(() => process.exit(gotPack && gotWindow && gotChat ? 0 : 2), 500); }
process.stdin.on('data', () => setTimeout(finish, 1500));
setTimeout(finish, 150000);
