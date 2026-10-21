import {describe,it,expect} from 'vitest';
describe('WinLaucher',()=>{ it('themes list includes 8 themes',()=>{expect(['dark','light','minecraft','purple','ocean','emerald','midnight','custom'].length).toBe(8)}); it('ram presets',()=>{expect([2048,4096,6144,8192,12288,16384]).toContain(4096)}); });
