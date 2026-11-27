import { readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { protectAndroidDexBuffer, inspectAndroidDex } from 'bla-agents';

const pkg = JSON.parse(readFileSync(new URL('node_modules/bla-agents/package.json', import.meta.url)));
if (pkg.version !== '3.6.1') throw new Error('Unvalidated BLA version; expected 3.6.1');
const [input, output] = process.argv.slice(2);
if (!input || !output || input === output) throw new Error('Distinct input and output DEX paths required');
const original = readFileSync(input);
if (!inspectAndroidDex(original).validDex) throw new Error('Invalid or unsupported original DEX');
if (inspectAndroidDex(original).blaProtected) throw new Error('Refusing to harden an already protected DEX');
const result = protectAndroidDexBuffer(original, { intensity: 'stealth', seed: 'mimic-bla-3.6.1-stealth-v1' });
const protectedDex = result.buffer;
const inspection = inspectAndroidDex(protectedDex);
if (!inspection.validDex || !inspection.blaProtected || protectedDex.length <= original.length)
  throw new Error('BLA DEX verification failed');
if (!original.subarray(36).equals(protectedDex.subarray(36, original.length)))
  throw new Error('Stealth shield modified original program data');
if (!protectedDex.subarray(12, 32).equals(createHash('sha1').update(protectedDex.subarray(32)).digest()))
  throw new Error('Protected DEX SHA-1 mismatch');
writeFileSync(output, protectedDex, { flag: 'wx' });
console.log(JSON.stringify({ version: pkg.version, intensity: 'stealth',
  originalBytes: original.length, protectedBytes: protectedDex.length,
  originalProgramUnchanged: true, addedBytes: protectedDex.length - original.length,
  actualProtection: 'removable decoy-text trailer; original string/type/class tables and method bodies unchanged',
  methodObfuscationAdded: false, runtimeEncryptionAdded: false, decompilerResistanceDemonstrated: false,
  inspection }));
