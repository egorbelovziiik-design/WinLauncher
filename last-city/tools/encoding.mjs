// rbxm-parser 1.1.4 exposes binary strings as JS Latin-1 code units, NOT UTF-8.
// Convert authored Unicode text exactly once at the serialization boundary.
// Do not touch binary fields such as AttributesSerialize, Tags or terrain data.
export function encodeAuthoredText(file) {
  const fields=new Set(['Name','Source','Text','ToolTip']);
  const bytes=text=>Buffer.from(text,'utf8').toString('latin1');
  function visit(node) {
    for(const [key,prop] of node.Props) {
      if(prop.type===1&&typeof prop.value==='string'&&(fields.has(key)||(key==='Value'&&node.ClassName==='StringValue'))) {
        node.SetProp(key,prop.type,bytes(prop.value));
      }
    }
    for(const child of node.Children)visit(child);
  }
  for(const node of file.Roots)visit(node);
  for(const [key,val] of file.Metadata)file.Metadata.set(key,bytes(val));
}
export function decodeBinaryText(text) {
  return new TextDecoder('utf-8',{fatal:true}).decode(Buffer.from(text,'latin1'));
}
