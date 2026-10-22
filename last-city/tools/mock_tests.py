#!/usr/bin/env python3
"""Generate an adapter harness from the actual sources. No rewritten game logic."""
from pathlib import Path
import subprocess, os
ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "last-city/src"
def build_harness():
    header = '''-- Generated; the API double is deliberately NOT Roblox Studio.
local Engine = require("../last-city/tests/MockRoblox")
local game,workspace,task = Engine.game,Engine.workspace,Engine.task
local Instance,Enum,Vector3,Vector2,Color3,CFrame,UDim,UDim2 = Engine.Instance,Engine.Enum,Engine.Vector3,Engine.Vector2,Engine.Color3,Engine.CFrame,Engine.UDim,Engine.UDim2
local TweenInfo,ColorSequence,NumberSequence,NumberSequenceKeypoint,RaycastParams,Random = Engine.TweenInfo,Engine.ColorSequence,Engine.NumberSequence,Engine.NumberSequenceKeypoint,Engine.RaycastParams,Engine.Random
local typeof=Engine.typeof
local warn=function(...) print("MOCK WARNING",...) end
local factories,cache,nodes={},{},{}
local require
require=function(module)
    assert(module,"require(nil)")
    if cache[module]~=nil then return cache[module] end
    local fn=assert(factories[module],"No factory for "..module.Name)
    local value=fn(module); cache[module]=value; return value
end
nodes["ReplicatedStorage"]=game:GetService("ReplicatedStorage")
nodes["ServerScriptService"]=game:GetService("ServerScriptService")
nodes["ReplicatedFirst"]=game:GetService("ReplicatedFirst")
nodes["StarterPlayer"]=Engine.add(nil,"StarterPlayer","StarterPlayer")
nodes["StarterPlayer/StarterPlayerScripts"]=Engine.Player.PlayerScripts
local remotes=Engine.add(nodes["ReplicatedStorage"],"Folder","Remotes")
for _,name in {"SessionRemote","QuestRemote","InventoryRemote","CombatRemote","BuildRemote","ShopRemote","NPCRemote","MovementRemote","CraftingRemote","StateRemote","EffectsRemote"} do Engine.add(remotes,"RemoteEvent",name) end
'''
    parts=[header]
    existing={"ReplicatedStorage","ServerScriptService","ReplicatedFirst","StarterPlayer","StarterPlayer/StarterPlayerScripts"}
    for file in sorted(SRC.rglob('*.luau')):
        rel=file.relative_to(SRC)
        for parent in reversed(rel.parents):
            if str(parent)=='.' or parent.as_posix() in existing: continue
            key=parent.as_posix(); p=parent.parent.as_posix()
            parts.append(f'nodes["{key}"]=Engine.add(nodes["{p}"],"Folder","{parent.name}")\n')
            existing.add(key)
        key=rel.as_posix(); name=file.name.replace('.client.luau','').replace('.server.luau','').replace('.luau','')
        cls='LocalScript' if '.client.' in file.name else 'ModuleScript'
        parts.append(f'nodes["{key}"]=Engine.add(nodes["{rel.parent.as_posix()}"],"{cls}","{name}")\n')
        code=file.read_text()
        if file.name=='Loading.client.luau':
            code='local os={clock=function() return Engine.LoadingClock end}\nlocal task={wait=function(step) Engine.LoadingClock+=step; return step end}\n'+code
        if file.name=='Client.client.luau': code+='\nreturn app\n'
        if file.name=='Bootstrap.server.luau': code+='\nreturn ctx\n'
        parts.append(f'factories[nodes["{key}"]]=function(script)\n{code}\nend\n')
    parts.append((ROOT/'last-city/tests/adapters.spec.luau').read_text())
    out=ROOT/'.local/last_city_adapters.luau'; out.parent.mkdir(exist_ok=True); out.write_text(''.join(parts))
    return out
if __name__=='__main__':
    luau=os.environ.get('LUAU',str(ROOT/'.local/bin/luau'))
    raise SystemExit(subprocess.call([luau,str(build_harness())],cwd=ROOT))
