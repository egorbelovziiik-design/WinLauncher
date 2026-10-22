#!/usr/bin/env python3
"""
Build MiniNations3D.rbxl — valid Roblox XML place file containing all Luau sources.
"""
import os, pathlib, html, hashlib, random, xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).parent
SRC = ROOT / "src"
OUT = ROOT / "MiniNations3D.rbxl"

# Helper to make referent IDs
counter = 0
def ref():
    global counter
    counter+=1
    return f"RBX{counter:08X}"

def esc(s: str) -> str:
    # for XML text nodes, ET will escape, but we use CDATA for Source
    return s

def create_item(parent, cls, name, props=None):
    item = ET.SubElement(parent, "Item")
    item.set("class", cls)
    item.set("referent", ref())
    props = props or {}
    for k,v in props.items():
        p = ET.SubElement(item, "Properties")
        # not used directly
        pass
    # Name property
    prop_name = ET.SubElement(item, "Properties")
    # Actually Roblox format: <Properties><string name="Name">value</string>...
    s = ET.SubElement(prop_name, "string")
    s.set("name", "Name")
    s.text = name
    return item

def add_string_prop(item, name, value):
    props = item.find("Properties")
    if props is None:
        props = ET.SubElement(item, "Properties")
    e = ET.SubElement(props, "string")
    e.set("name", name)
    e.text = value
    return e

def add_bool_prop(item, name, value:bool):
    props = item.find("Properties")
    if props is None:
        props = ET.SubElement(item, "Properties")
    e = ET.SubElement(props, "bool")
    e.set("name", name)
    e.text = "true" if value else "false"
    return e

def add_protected_string(item, name, source_text):
    """Add ProtectedString Source with CDATA"""
    props = item.find("Properties")
    if props is None:
        props = ET.SubElement(item, "Properties")
    e = ET.SubElement(props, "ProtectedString")
    e.set("name", name)
    # ET will escape; we need to ensure content preserved via CDATA manually after serialization
    # Store raw and post-process
    e.text = source_text
    e.set("_isSource", "1")
    return e

def serialize_with_cdata(root):
    # Use ET to string then replace ProtectedString content with CDATA
    import io
    out = io.StringIO()
    # manual write to preserve CDATA
    def write_node(node, indent=0):
        pad = "  "*indent
        if node.tag == "roblox":
            out.write(f'<roblox version="4">\n')
            for ch in node:
                write_node(ch, 1)
            out.write(f'</roblox>\n')
        elif node.tag == "Item":
            cls = node.get("class","")
            refid = node.get("referent","")
            out.write(f'{pad}<Item class="{cls}" referent="{refid}">\n')
            for ch in node:
                write_node(ch, indent+1)
            out.write(f'{pad}</Item>\n')
        elif node.tag == "Properties":
            out.write(f'{pad}<Properties>\n')
            for ch in node:
                write_node(ch, indent+1)
            out.write(f'{pad}</Properties>\n')
        elif node.tag in ("string","bool","ProtectedString","double","int","Vector3","CFrame","Color3"):
            name = node.get("name","")
            isSource = node.get("_isSource")
            if isSource:
                # CDATA
                text = node.text or ""
                # avoid ]]> in source
                text = text.replace("]]>", "]]]]><![CDATA[>")
                out.write(f'{pad}<{node.tag} name="{name}"><![CDATA[{text}]]></{node.tag}>\n')
            else:
                # escape text
                txt = html.escape(node.text or "", quote=False)
                # simple types
                if node.tag == "string":
                    out.write(f'{pad}<string name="{name}">{txt}</string>\n')
                elif node.tag == "bool":
                    out.write(f'{pad}<bool name="{name}">{txt}</bool>\n')
                elif node.tag == "double":
                    out.write(f'{pad}<double name="{name}">{txt}</double>\n')
                elif node.tag == "int":
                    out.write(f'{pad}<int name="{name}">{txt}</int>\n')
                else:
                    out.write(f'{pad}<{node.tag} name="{name}">{txt}</{node.tag}>\n')
        else:
            # generic
            out.write(f'{pad}<{node.tag}')
            for k,v in node.attrib.items():
                if k=="_isSource": continue
                out.write(f' {k}="{html.escape(v, quote=True)}"')
            out.write('>')
            if node.text:
                out.write(html.escape(node.text))
            out.write(f'</{node.tag}>\n')
    write_node(root)
    return out.getvalue()

# Build structure
roblox = ET.Element("roblox")
# Add External markers as in real rbxl (optional)
# DataModel
dm = ET.SubElement(roblox, "Item")
dm.set("class", "DataModel")
dm.set("referent", ref())
# Name
p = ET.SubElement(dm, "Properties")
e = ET.SubElement(p, "string"); e.set("name","Name"); e.text="MiniNations3D"

# Helper to create service items under DataModel
def service(name, cls):
    it = ET.SubElement(dm, "Item")
    it.set("class", cls)
    it.set("referent", ref())
    props = ET.SubElement(it, "Properties")
    s = ET.SubElement(props, "string"); s.set("name","Name"); s.text=name
    return it

workspace = service("Workspace", "Workspace")
rs = service("ReplicatedStorage", "ReplicatedStorage")
sss = service("ServerScriptService", "ServerScriptService")
ssplayer = service("StarterPlayer", "StarterPlayer")
starter_gui = service("StarterGui", "StarterGui")
lighting = service("Lighting", "Lighting")
sound_service = service("SoundService", "SoundService")
players_svc = service("Players", "Players")
# Lighting props
props = lighting.find("Properties")
def add_color3(parent, name, c):
    e = ET.SubElement(parent, "Color3")
    e.set("name", name)
    # Roblox wants RGB packed? Use simple
    r = ET.SubElement(e, "R"); r.text = str(c[0])
    g = ET.SubElement(e, "G"); g.text = str(c[1])
    b = ET.SubElement(e, "B"); b.text = str(c[2])
# quick ambient
# Workspace props for streaming
p = workspace.find("Properties")
e = ET.SubElement(p, "bool"); e.set("name","StreamingEnabled"); e.text="true"
e = ET.SubElement(p, "int"); e.set("name","StreamingTargetRadius"); e.text="350"

# --- ReplicatedStorage: Shared modules + Remotes folder ---
shared_folder = ET.SubElement(rs, "Item")
shared_folder.set("class","Folder"); shared_folder.set("referent", ref())
pp = ET.SubElement(shared_folder, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="Shared"

remotes_folder = ET.SubElement(rs, "Item")
remotes_folder.set("class","Folder"); remotes_folder.set("referent", ref())
pp = ET.SubElement(remotes_folder, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="Remotes"
# add placeholder RemoteEvents as instances (will also be created at runtime, but include for Studio view)
for rn in ["RequestProfile","CoinsUpdated","ProvinceOwnerChanged","BuildingPlaced","ArmyCreated","ArmyMoved","WarDeclared","BattleResult","PeaceSigned","RelationChanged","AllianceCreated","TechUnlocked","ShopPurchased","AchievementUnlocked","MapSaved","CityFounded","MatchStarted","BuyItem","BuildRequest","MoveArmy","DeclareWar","MakePeace","CreateAlliance","ResearchTech","JoinMatch","CreateMatch","StartMatch","SaveMap","LoadMap","SetReady","ToggleTutorial","GetEconomy","GetResources","GetProvinces","GetProfile"]:
    isFunc = rn=="GetProfile"
    cls = "RemoteFunction" if isFunc else "RemoteEvent"
    it = ET.SubElement(remotes_folder, "Item")
    it.set("class", cls); it.set("referent", ref())
    pp = ET.SubElement(it, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text=rn

# Add ModuleScripts for shared
for file in sorted((SRC/"shared").glob("*.luau")):
    src_text = file.read_text(encoding="utf-8")
    item = ET.SubElement(shared_folder, "Item")
    item.set("class","ModuleScript"); item.set("referent", ref())
    pp = ET.SubElement(item, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text=file.stem
    add_protected_string(item, "Source", src_text)

# --- ServerScriptService ---
services_folder = ET.SubElement(sss, "Item")
services_folder.set("class","Folder"); services_folder.set("referent", ref())
pp = ET.SubElement(services_folder, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="Services"

for file in sorted((SRC/"server"/"Services").glob("*.luau")):
    src_text = file.read_text(encoding="utf-8")
    item = ET.SubElement(services_folder, "Item")
    item.set("class","ModuleScript"); item.set("referent", ref())
    pp = ET.SubElement(item, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text=file.stem
    add_protected_string(item, "Source", src_text)

# Main.server.luau -> Script
for fname in ["Main.server.luau","AntiCheat.server.luau"]:
    fpath = SRC/"server"/fname
    if fpath.exists():
        src_text = fpath.read_text(encoding="utf-8")
        item = ET.SubElement(sss, "Item")
        item.set("class","Script"); item.set("referent", ref())
        pp = ET.SubElement(item, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text=fname.replace(".server.luau","").replace(".luau","")
        add_protected_string(item, "Source", src_text)

# --- StarterGui: Client UI ---
# Create a Folder for Client then scripts
client_folder = ET.SubElement(starter_gui, "Item")
client_folder.set("class","Folder"); client_folder.set("referent", ref())
pp = ET.SubElement(client_folder, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="Client"

# LocalScript Main.client
main_client = SRC/"client"/"Main.client.luau"
if main_client.exists():
    src_text = main_client.read_text(encoding="utf-8")
    item = ET.SubElement(starter_gui, "Item")
    # Place LocalScript in StarterGui to run; also duplicate to StarterPlayer
    item.set("class","LocalScript"); item.set("referent", ref())
    pp = ET.SubElement(item, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="Main"
    add_protected_string(item, "Source", src_text)

# Controllers subfolder
ctrl_folder = ET.SubElement(client_folder, "Item")
ctrl_folder.set("class","Folder"); ctrl_folder.set("referent", ref())
pp = ET.SubElement(ctrl_folder, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="Controllers"
for file in sorted((SRC/"client"/"Controllers").glob("*.luau")):
    src_text = file.read_text(encoding="utf-8")
    item = ET.SubElement(ctrl_folder, "Item")
    item.set("class","ModuleScript"); item.set("referent", ref())
    pp = ET.SubElement(item, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text=file.stem
    add_protected_string(item, "Source", src_text)

ui_folder = ET.SubElement(client_folder, "Item")
ui_folder.set("class","Folder"); ui_folder.set("referent", ref())
pp = ET.SubElement(ui_folder, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="UI"
for file in sorted((SRC/"client"/"UI").glob("*.luau")):
    src_text = file.read_text(encoding="utf-8")
    item = ET.SubElement(ui_folder, "Item")
    item.set("class","ModuleScript"); item.set("referent", ref())
    pp = ET.SubElement(item, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text=file.stem
    add_protected_string(item, "Source", src_text)

# Also add copy to StarterPlayer > StarterPlayerScripts
sps = ET.SubElement(ssplayer, "Item")
sps.set("class","StarterPlayerScripts"); sps.set("referent", ref())
pp = ET.SubElement(sps, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="StarterPlayerScripts"
if main_client.exists():
    src_text = main_client.read_text(encoding="utf-8")
    item = ET.SubElement(sps, "Item")
    item.set("class","LocalScript"); item.set("referent", ref())
    pp = ET.SubElement(item, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="Main"
    add_protected_string(item, "Source", src_text)

# --- Workspace: procedural map parts ---
# Generate simple terrain parts for visual
provinces_folder = ET.SubElement(workspace, "Item")
provinces_folder.set("class","Folder"); provinces_folder.set("referent", ref())
pp = ET.SubElement(provinces_folder, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="Provinces"

# Create a baseplate
base = ET.SubElement(workspace, "Item")
base.set("class","Part"); base.set("referent", ref())
pp = ET.SubElement(base, "Properties")
s = ET.SubElement(pp,"string"); s.set("name","Name"); s.text="BaseTerrain"
# Size & CFrame placeholders (Studio will fix)
s = ET.SubElement(pp,"Vector3"); s.set("name","Size"); 
x=ET.SubElement(s,"X"); x.text="3000"; y=ET.SubElement(s,"Y"); y.text="2"; z=ET.SubElement(s,"Z"); z.text="2000"
e = ET.SubElement(pp,"CFrame"); e.set("name","CFrame"); e.text="0, -5, 0, 1,0,0, 0,1,0, 0,0,1"

# Generate 40 province plates as Parts (representative; runtime will regenerate full)
import random
random.seed(42)
colors = ["#3B82F6","#EF4444","#F59E0B","#10B981","#6366F1","#8B5CF6","#EC4899"]
for i in range(40):
    part = ET.SubElement(provinces_folder, "Item")
    part.set("class","Part"); part.set("referent", ref())
    pp = ET.SubElement(part, "Properties")
    s = ET.SubElement(pp,"string"); s.set("name","Name"); s.text=f"P-{i:03d}"
    s = ET.SubElement(pp,"Vector3"); s.set("name","Size")
    x=ET.SubElement(s,"X"); x.text=str(random.randint(32,48)); y=ET.SubElement(s,"Y"); y.text=str(random.randint(3,9)); z=ET.SubElement(s,"Z"); z.text=str(random.randint(32,48))
    e = ET.SubElement(pp,"bool"); e.set("name","Anchored"); e.text="true"
    e = ET.SubElement(pp,"Color3"); e.set("name","Color3uint8"); e.text=str(random.choice(colors)) # placeholder

# Serialize
xml_content = serialize_with_cdata(roblox)
OUT.write_text(xml_content, encoding="utf-8")
print(f"✅ Built {OUT} ({len(xml_content)} bytes, {counter} instances)")
