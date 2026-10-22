import fs from 'node:fs';
import { readBinary, readXml } from 'rbx-dom';

const base = process.argv[2] || 'build/DreamSimulator';
const binPath = base + '.rbxl';

const dom = readBinary(fs.readFileSync(binPath));
const snap = dom.snapshot();
const byRef = new Map(snap.instances.map((i) => [i.referent, i]));
const root = byRef.get(snap.rootRef);

function childrenOf(ref) {
  return snap.instances.filter((i) => i.parent === ref);
}
let counts = new Map();
let scriptBytes = 0, scriptCount = 0, partCount = 0;
function walk(ref, depth, out) {
  for (const inst of childrenOf(ref)) {
    counts.set(inst.className, (counts.get(inst.className) || 0) + 1);
    if (['Script', 'LocalScript', 'ModuleScript'].includes(inst.className)) {
      const src = inst.properties.Source;
      const s = typeof src === 'string' ? src : (src?.String ?? src?.ProtectedString ?? '');
      if (!s || s.length < 5) out.push(`EMPTY SCRIPT: ${inst.className} ${inst.name}`);
      scriptBytes += s.length; scriptCount++;
    }
    if (inst.className === 'Part') partCount++;
    if (depth < 3) walk(inst.referent, depth + 1, out);
  }
}
const problems = [];
walk(snap.rootRef, 0, problems);
console.log('root', root.className, root.name, 'instances', dom.instanceCount);
console.log('parts', partCount, 'scripts', scriptCount, 'script bytes', scriptBytes);
const top = new Map();
for (const c of childrenOf(snap.rootRef)) top.set(c.name, c.className);
console.log('services:', [...top.entries()].map(([n, c]) => `${n}(${c})`).join(', '));
const interesting = [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 18);
console.log('classes:', interesting.map(([n, c]) => `${n}:${c}`).join(' '));
// xml round trip
const xml = fs.readFileSync(base + '.rbxlx');
const fromXml = readXml(xml);
console.log('xml instances', fromXml.instanceCount, 'xml bytes', xml.length, 'bin bytes', fs.statSync(binPath).size);
if (problems.length) { console.log('PROBLEMS:'); problems.slice(0, 20).forEach((p) => console.log('  ' + p)); }
if (fromXml.instanceCount !== dom.instanceCount) console.log('WARN: xml/binary instance mismatch');
