// Compare decoded content, not platform-dependent LZ4 chunk representation.
// Source, hierarchy, every serialized property, geometry and reference targets
// must match; this does not relax floating point comparisons or skip metadata.
import fs from 'node:fs';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import { RobloxFile } from 'rbxm-parser';
const [leftPath,rightPath]=process.argv.slice(2);
if(!leftPath||!rightPath)throw new Error('Usage: node compare-place.mjs place-A.rbxl place-B.rbxl');
const leftBytes=fs.readFileSync(leftPath),rightBytes=fs.readFileSync(rightPath);
function instancePath(node) {
  const names=[]; let cursor=node;
  while(cursor) { names.unshift(`${cursor.ClassName}:${cursor.Name}`); cursor=cursor.Parent; }
  return names.join('/');
}
function value(v) {
  if(typeof v==='bigint')return {bigint:v.toString()};
  if(typeof v==='number'&&!Number.isFinite(v))return {number:String(v)};
  if(!v||typeof v!=='object')return v;
  if('ClassName' in v&&'Props' in v)return {referent:instancePath(v),position:value(v.Props.get('CFrame')?.value)};
  if(Array.isArray(v))return v.map(value);
  return Object.fromEntries(Object.keys(v).sort().map(k=>[k,value(v[k])]));
}
function canonical(bytes) {
  const file=assertFile(RobloxFile.ReadFromBuffer(bytes));
  const nodes=[];
  function visit(node) {
    const props=Object.fromEntries([...node.Props].sort(([a],[b])=>a<b?-1:a>b?1:0).map(([name,prop])=>[name,{type:prop.type,value:value(prop.value)}]));
    nodes.push(JSON.stringify({path:instancePath(node),service:node.IsService,props}));
    node.Children.forEach(visit);
  }
  file.Roots.forEach(visit);
  nodes.sort();
  return {metadata:[...file.Metadata].sort(([a],[b])=>a<b?-1:1),nodes};
}
function assertFile(file){assert.ok(file,'Invalid binary');return file;}
const left=canonical(leftBytes),right=canonical(rightBytes);
try { assert.deepEqual(right,left); }
catch(error) {
  const onlyLeft=left.nodes.filter(n=>!right.nodes.includes(n));
  const onlyRight=right.nodes.filter(n=>!left.nodes.includes(n));
  const details={leftCount:left.nodes.length,rightCount:right.nodes.length,left:onlyLeft.slice(0,2).map(JSON.parse),right:onlyRight.slice(0,2).map(JSON.parse)};
  const message=JSON.stringify(details).slice(0,12000);
  console.error(`::error title=Place content mismatch::${message.replaceAll('%','%25').replaceAll('\n','%0A').replaceAll('\r','%0D')}`);
  throw error;
}
const digest=crypto.createHash('sha256').update(JSON.stringify(left)).digest('hex');
console.log(`PASSED | Decoded content, UTF-8 sources, properties, hierarchy and references match: ${left.nodes.length} instances`);
console.log(`Semantic SHA256: ${digest}`);
console.log(`Raw bytes identical: ${leftBytes.equals(rightBytes)} (${leftBytes.length} / ${rightBytes.length} bytes)`);
