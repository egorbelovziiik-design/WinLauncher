#!/usr/bin/env python3
"""
Build MiniNations3D.rbxl v2 — богатый мир с провинциями, биомами, городами
"""
import pathlib, html, random, math, xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).parent
SRC = ROOT / "src"
OUT = ROOT / "MiniNations3D.rbxl"

counter = 0
def ref():
    global counter; counter+=1; return f"RBX{counter:08X}"

def create_item(parent, cls, name):
    item = ET.SubElement(parent, "Item")
    item.set("class", cls); item.set("referent", ref())
    props = ET.SubElement(item, "Properties")
    s = ET.SubElement(props, "string"); s.set("name","Name"); s.text=name
    return item, props

def add_bool(props, name, val):
    e = ET.SubElement(props, "bool"); e.set("name", name); e.text="true" if val else "false"
def add_string(props, name, val):
    e = ET.SubElement(props, "string"); e.set("name", name); e.text=val
def add_int(props, name, val):
    e = ET.SubElement(props, "int"); e.set("name", name); e.text=str(val)
def add_float(props, name, val):
    e = ET.SubElement(props, "float"); e.set("name", name); e.text=str(val)
def add_vector3(props, name, x,y,z):
    e = ET.SubElement(props, "Vector3"); e.set("name", name)
    ET.SubElement(e, "X").text=str(x); ET.SubElement(e, "Y").text=str(y); ET.SubElement(e, "Z").text=str(z)
def add_cframe(props, name, pos, angles=(0,0,0)):
    # Roblox CFrame: position + rotation matrix
    e = ET.SubElement(props, "CFrame"); e.set("name", name)
    ET.SubElement(e, "X").text=str(pos[0]); ET.SubElement(e, "Y").text=str(pos[1]); ET.SubElement(e, "Z").text=str(pos[2])
    # rotation matrix identity (angles ignored for simplicity)
    ET.SubElement(e, "R00").text="1"; ET.SubElement(e, "R01").text="0"; ET.SubElement(e, "R02").text="0"
    ET.SubElement(e, "R10").text="0"; ET.SubElement(e, "R11").text="1"; ET.SubElement(e, "R12").text="0"
    ET.SubElement(e, "R20").text="0"; ET.SubElement(e, "R21").text="0"; ET.SubElement(e, "R22").text="1"
def add_color3(props, name, hexcolor):
    # Color3uint8 stored as int
    hexcolor=hexcolor.lstrip("#")
    r=int(hexcolor[0:2],16); g=int(hexcolor[2:4],16); b=int(hexcolor[4:6],16)
    e = ET.SubElement(props, "Color3uint8"); e.set("name", name); e.text=str((r<<16)|(g<<8)|b)
    return e
def add_token(props, name, token):
    e = ET.SubElement(props, "token"); e.set("name", name); e.text=str(token)
def add_protected_string(item_props_parent, name, src):
    # find Properties node
    props = item_props_parent if item_props_parent.tag=="Properties" else item_props_parent.find("Properties")
    if props is None:
        props = ET.SubElement(item_props_parent, "Properties")
    e = ET.SubElement(props, "ProtectedString"); e.set("name", name); e.text=src; e.set("_isSource","1")
    return e

def serialize(root):
    import io
    out = io.StringIO()
    def write(node, indent=0):
        pad="  "*indent
        if node.tag=="roblox":
            out.write('<roblox version="4">\n')
            for c in node: write(c,1)
            out.write('</roblox>\n')
        elif node.tag=="Item":
            out.write(f'{pad}<Item class="{node.get("class")}" referent="{node.get("referent")}">\n')
            for c in node: write(c, indent+1)
            out.write(f'{pad}</Item>\n')
        elif node.tag=="Properties":
            out.write(f'{pad}<Properties>\n')
            for c in node: write(c, indent+1)
            out.write(f'{pad}</Properties>\n')
        elif node.tag in ("string","bool","int","float","double","token"):
            name=node.get("name","")
            txt=html.escape(node.text or "", quote=False)
            out.write(f'{pad}<{node.tag} name="{name}">{txt}</{node.tag}>\n')
        elif node.tag=="ProtectedString":
            name=node.get("name","")
            isSrc=node.get("_isSource")
            if isSrc:
                txt=node.text or ""
                txt=txt.replace("]]>", "]]]]><![CDATA[>")
                out.write(f'{pad}<ProtectedString name="{name}"><![CDATA[{txt}]]></ProtectedString>\n')
            else:
                out.write(f'{pad}<ProtectedString name="{name}">{html.escape(node.text or "")}</ProtectedString>\n')
        elif node.tag=="Vector3":
            name=node.get("name","")
            out.write(f'{pad}<Vector3 name="{name}">\n')
            for c in node: write(c, indent+1)
            out.write(f'{pad}</Vector3>\n')
        elif node.tag=="CFrame":
            name=node.get("name","")
            out.write(f'{pad}<CFrame name="{name}">\n')
            for c in node: write(c, indent+1)
            out.write(f'{pad}</CFrame>\n')
        elif node.tag=="Color3uint8":
            name=node.get("name","")
            out.write(f'{pad}<Color3uint8 name="{name}">{html.escape(node.text or "")}</Color3uint8>\n')
        else:
            # generic leaf X,Y,Z,R etc
            txt=html.escape(node.text or "")
            out.write(f'{pad}<{node.tag}>{txt}</{node.tag}>\n')
    write(root)
    return out.getvalue()

# Build
roblox = ET.Element("roblox")
dm = ET.SubElement(roblox, "Item"); dm.set("class","DataModel"); dm.set("referent", ref())
p = ET.SubElement(dm, "Properties"); s=ET.SubElement(p,"string"); s.set("name","Name"); s.text="MiniNations3D"

def service(name, cls):
    it = ET.SubElement(dm, "Item"); it.set("class", cls); it.set("referent", ref())
    pp = ET.SubElement(it, "Properties"); ss=ET.SubElement(pp,"string"); ss.set("name","Name"); ss.text=name
    return it

workspace = service("Workspace","Workspace")
rs = service("ReplicatedStorage","ReplicatedStorage")
sss = service("ServerScriptService","ServerScriptService")
starter_gui = service("StarterGui","StarterGui")
starter_player = service("StarterPlayer","StarterPlayer")
lighting = service("Lighting","Lighting")
sound_service = service("SoundService","SoundService")
teams = service("Teams","Teams")

# Lighting
props = lighting.find("Properties")
add_string(props,"Name","Lighting")
add_token(props,"Technology","ShadowMap")
add_float(props,"Brightness","2")
add_float(props,"ExposureCompensation","0.2")
add_string(props,"TimeOfDay","14:00:00")
add_float(props,"GeographicLatitude","41")
# Workspace props
props = workspace.find("Properties")
add_bool(props,"StreamingEnabled",True)
add_int(props,"StreamingTargetRadius",450)
add_token(props,"StreamingIntegrityMode","Default")

# ReplicatedStorage structure
shared_folder = ET.SubElement(rs, "Item"); shared_folder.set("class","Folder"); shared_folder.set("referent", ref())
pp = ET.SubElement(shared_folder, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="Shared"
remotes_folder = ET.SubElement(rs, "Item"); remotes_folder.set("class","Folder"); remotes_folder.set("referent", ref())
pp = ET.SubElement(remotes_folder, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="Remotes"
for rn in ["RequestProfile","CoinsUpdated","ProvinceOwnerChanged","BuildingPlaced","ArmyCreated","ArmyMoved","WarDeclared","BattleResult","PeaceSigned","RelationChanged","AllianceCreated","TechUnlocked","ShopPurchased","AchievementUnlocked","MapSaved","CityFounded","MatchStarted","BuyItem","BuildRequest","MoveArmy","DeclareWar","MakePeace","CreateAlliance","ResearchTech","JoinMatch","CreateMatch","StartMatch","SaveMap","LoadMap","SetReady","ToggleTutorial","GetEconomy","GetResources","GetProvinces","GetProfile"]:
    cls = "RemoteFunction" if rn=="GetProfile" else "RemoteEvent"
    it = ET.SubElement(remotes_folder, "Item"); it.set("class", cls); it.set("referent", ref())
    pp = ET.SubElement(it, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text=rn

# Add shared ModuleScripts
for file in sorted((SRC/"shared").glob("*.luau")):
    it = ET.SubElement(shared_folder, "Item"); it.set("class","ModuleScript"); it.set("referent", ref())
    pp = ET.SubElement(it, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text=file.stem
    add_protected_string(pp, "Source", file.read_text(encoding="utf-8"))

# ServerScriptService
services_folder = ET.SubElement(sss, "Item"); services_folder.set("class","Folder"); services_folder.set("referent", ref())
pp = ET.SubElement(services_folder, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="Services"
for file in sorted((SRC/"server"/"Services").glob("*.luau")):
    it = ET.SubElement(services_folder, "Item"); it.set("class","ModuleScript"); it.set("referent", ref())
    pp = ET.SubElement(it, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text=file.stem
    add_protected_string(pp, "Source", file.read_text(encoding="utf-8"))
for fname in ["Main.server.luau","AntiCheat.server.luau"]:
    fpath = SRC/"server"/fname
    if fpath.exists():
        it = ET.SubElement(sss, "Item"); it.set("class","Script"); it.set("referent", ref())
        pp = ET.SubElement(it, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text=fname.replace(".server.luau","").replace(".luau","")
        add_protected_string(pp, "Source", fpath.read_text(encoding="utf-8"))

# StarterGui
client_folder = ET.SubElement(starter_gui, "Item"); client_folder.set("class","Folder"); client_folder.set("referent", ref())
pp = ET.SubElement(client_folder, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="Client"
main_client = SRC/"client"/"Main.client.luau"
if main_client.exists():
    it = ET.SubElement(starter_gui, "Item"); it.set("class","LocalScript"); it.set("referent", ref())
    pp = ET.SubElement(it, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="Main"
    add_protected_string(pp, "Source", main_client.read_text(encoding="utf-8"))
ctrl_folder = ET.SubElement(client_folder, "Item"); ctrl_folder.set("class","Folder"); ctrl_folder.set("referent", ref())
pp = ET.SubElement(ctrl_folder, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="Controllers"
for file in sorted((SRC/"client"/"Controllers").glob("*.luau")):
    it = ET.SubElement(ctrl_folder, "Item"); it.set("class","ModuleScript"); it.set("referent", ref())
    pp = ET.SubElement(it, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text=file.stem
    add_protected_string(pp, "Source", file.read_text(encoding="utf-8"))
ui_folder = ET.SubElement(client_folder, "Item"); ui_folder.set("class","Folder"); ui_folder.set("referent", ref())
pp = ET.SubElement(ui_folder, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="UI"
for file in sorted((SRC/"client"/"UI").glob("*.luau")):
    it = ET.SubElement(ui_folder, "Item"); it.set("class","ModuleScript"); it.set("referent", ref())
    pp = ET.SubElement(it, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text=file.stem
    add_protected_string(pp, "Source", file.read_text(encoding="utf-8"))

# StarterPlayer -> StarterPlayerScripts
sps = ET.SubElement(starter_player, "Item"); sps.set("class","StarterPlayerScripts"); sps.set("referent", ref())
pp = ET.SubElement(sps, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="StarterPlayerScripts"
if main_client.exists():
    it = ET.SubElement(sps, "Item"); it.set("class","LocalScript"); it.set("referent", ref())
    pp = ET.SubElement(it, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="Main"
    add_protected_string(pp, "Source", main_client.read_text(encoding="utf-8"))
spsc = ET.SubElement(starter_player, "Item"); spsc.set("class","StarterCharacterScripts"); spsc.set("referent", ref())
pp = ET.SubElement(spsc, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="StarterCharacterScripts"

# ===== WORKSPACE: Богатый мир =====
random.seed(42)

# Terrain folder for organization
world_model = ET.SubElement(workspace, "Item"); world_model.set("class","Model"); world_model.set("referent", ref())
pp = ET.SubElement(world_model, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="World"

# Ocean base
ocean = ET.SubElement(world_model, "Item"); ocean.set("class","Part"); ocean.set("referent", ref())
pp = ET.SubElement(ocean, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="Ocean"
add_vector3(pp,"Size",2800,6,1800)
add_cframe(pp,"CFrame",(0,-6,0))
add_bool(pp,"Anchored",True); add_bool(pp,"CanCollide",False)
add_string(pp,"Material","SmoothPlastic")
add_color3(pp,"Color3uint8","#1E3A8A")
add_float(pp,"Transparency","0.15")

# Sand bottom under ocean
sand = ET.SubElement(world_model, "Item"); sand.set("class","Part"); sand.set("referent", ref())
pp = ET.SubElement(sand, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="OceanFloor"
add_vector3(pp,"Size",2800,4,1800)
add_cframe(pp,"CFrame",(0,-12,0))
add_bool(pp,"Anchored",True)
add_string(pp,"Material","Sand")
add_color3(pp,"Color3uint8","#1E40AF")

# Clouds (decorative parts in sky)
for i in range(12):
    cl = ET.SubElement(world_model, "Item"); cl.set("class","Part"); cl.set("referent", ref())
    pp = ET.SubElement(cl, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text=f"Cloud_{i}"
    add_vector3(pp,"Size",random.randint(80,160), random.randint(6,14), random.randint(60,120))
    add_cframe(pp,"CFrame",(random.randint(-1200,1200), random.randint(80,110), random.randint(-800,800)))
    add_bool(pp,"Anchored",True); add_bool(pp,"CanCollide",False)
    add_string(pp,"Material","SmoothPlastic")
    add_color3(pp,"Color3uint8","#FFFFFF")
    add_float(pp,"Transparency","0.55")

# Countries definitions for provinces
countries = [
    ("USA","#3B82F6"),("RUS","#EF4444"),("CHN","#F59E0B"),("DEU","#10B981"),("FRA","#6366F1"),
    ("GBR","#8B5CF6"),("JPN","#EC4899"),("IND","#F97316"),("BRA","#22C55E"),("TUR","#DC2626"),
    ("UKR","#0EA5E9"),("POL","#E11D48"),("ITA","#059669"),("ESP","#D97706"),("IRN","#16A34A"),
    ("KOR","#0EA5E9"),("AUS","#FACC15"),("CAN","#EF4444"),("MEX","#22C55E"),("ZAF","#A3E635"),
]

provinces_folder = ET.SubElement(world_model, "Item"); provinces_folder.set("class","Folder"); provinces_folder.set("referent", ref())
pp = ET.SubElement(provinces_folder, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="Provinces"

armies_folder = ET.SubElement(world_model, "Item"); armies_folder.set("class","Folder"); armies_folder.set("referent", ref())
pp = ET.SubElement(armies_folder, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="Armies"

cities_folder = ET.SubElement(world_model, "Item"); cities_folder.set("class","Folder"); cities_folder.set("referent", ref())
pp = ET.SubElement(cities_folder, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="Cities"

roads_folder = ET.SubElement(world_model, "Item"); roads_folder.set("class","Folder"); roads_folder.set("referent", ref())
pp = ET.SubElement(roads_folder, "Properties"); s=ET.SubElement(pp,"string"); s.set("name","Name"); s.text="Roads"

# Helper to create province cluster per country
prov_positions = []
idx=0
for ci, (cid,col) in enumerate(countries):
    # cluster center
    cx = (ci % 5 -2)*500 + random.randint(-80,80)
    cz = (ci //5 -1.5)*400 + random.randint(-60,60)
    for j in range(6): # 6 провинций на страну = 120
        idx+=1
        # province plate
        part = ET.SubElement(provinces_folder, "Item"); part.set("class","Part"); part.set("referent", ref())
        pp2 = ET.SubElement(part, "Properties")
        s=ET.SubElement(pp2,"string"); s.set("name","Name"); s.text=f"P-{idx:03d}"
        # size varies by biome
        is_mountain = (j==5 and random.random()<0.5)
        h = 14 if is_mountain else random.randint(4,8)
        sz = random.randint(36,52)
        add_vector3(pp2,"Size", sz, h, sz)
        # position with jitter
        x = cx + random.randint(-90,90)
        z = cz + random.randint(-70,70)
        y = h/2 -0.5
        if is_mountain:
            add_vector3(pp2,"Size", sz, 18, sz)
            y = 8
        add_cframe(pp2,"CFrame",(x, y, z))
        add_bool(pp2,"Anchored",True); add_bool(pp2,"CanCollide",True)
        # color by owner
        if is_mountain:
            add_string(pp2,"Material","Slate")
            add_color3(pp2,"Color3uint8","#57534E")
        else:
            add_string(pp2,"Material","Grass")
            add_color3(pp2,"Color3uint8",col)
        prov_positions.append((x,z, cid, f"P-{idx:03d}"))

        # Trees on province
        if not is_mountain and random.random()<0.6:
            for t in range(random.randint(1,3)):
                tree = ET.SubElement(part, "Item"); tree.set("class","Part"); tree.set("referent", ref())
                pp3 = ET.SubElement(tree, "Properties")
                s=ET.SubElement(pp3,"string"); s.set("name","Name"); s.text=f"Tree_{t}"
                add_vector3(pp3,"Size",3, random.randint(8,14),3)
                tx = x + random.randint(-14,14)
                tz = z + random.randint(-14,14)
                add_cframe(pp3,"CFrame",(tx, y+6, tz))
                add_bool(pp3,"Anchored",True)
                add_string(pp3,"Material","Wood")
                add_color3(pp3,"Color3uint8","#15803D")

        # City on first province of each country (capital)
        if j==0:
            city = ET.SubElement(cities_folder, "Item"); city.set("class","Model"); city.set("referent", ref())
            pp3 = ET.SubElement(city, "Properties"); s=ET.SubElement(pp3,"string"); s.set("name","Name"); s.text=f"Capital_{cid}"
            # City base
            base = ET.SubElement(city, "Item"); base.set("class","Part"); base.set("referent", ref())
            pp4 = ET.SubElement(base, "Properties"); s=ET.SubElement(pp4,"string"); s.set("name","Name"); s.text="CityBase"
            add_vector3(pp4,"Size",18,12,18)
            add_cframe(pp4,"CFrame",(x, y+7, z))
            add_bool(pp4,"Anchored",True)
            add_string(pp4,"Material","Concrete")
            add_color3(pp4,"Color3uint8","#E5E7EB")
            # Skyscraper
            tower = ET.SubElement(city, "Item"); tower.set("class","Part"); tower.set("referent", ref())
            pp4 = ET.SubElement(tower, "Properties"); s=ET.SubElement(pp4,"string"); s.set("name","Name"); s.text="Skyscraper"
            add_vector3(pp4,"Size",8, random.randint(20,32),8)
            add_cframe(pp4,"CFrame",(x+6, y+18, z+6))
            add_bool(pp4,"Anchored",True)
            add_string(pp4,"Material","Glass")
            add_color3(pp4,"Color3uint8","#0EA5E9")
            # Flag
            flag = ET.SubElement(city, "Item"); flag.set("class","Part"); flag.set("referent", ref())
            pp4 = ET.SubElement(flag, "Properties"); s=ET.SubElement(pp4,"string"); s.set("name","Name"); s.text="Flag"
            add_vector3(pp4,"Size",2,14,2)
            add_cframe(pp4,"CFrame",(x-7, y+12, z))
            add_bool(pp4,"Anchored",True)
            add_string(pp4,"Material","SmoothPlastic")
            add_color3(pp4,"Color3uint8",col)

            # Army near capital
            army = ET.SubElement(armies_folder, "Item"); army.set("class","Part"); army.set("referent", ref())
            pp4 = ET.SubElement(army, "Properties"); s=ET.SubElement(pp4,"string"); s.set("name","Name"); s.text=f"ARMY_{cid}_{idx}"
            add_vector3(pp4,"Size",7,3,9)
            add_cframe(pp4,"CFrame",(x+12, y+2, z+2))
            add_bool(pp4,"Anchored",True)
            add_string(pp4,"Material","SmoothPlastic")
            add_color3(pp4,"Color3uint8","#FACC15")

# Roads between capitals (simple straight roads)
for i in range(len(countries)-1):
    if i>=15: break
    a = prov_positions[i*6]  # capital pos
    b = prov_positions[(i+1)*6]
    # midpoint
    mx = (a[0]+b[0])/2; mz = (a[1]+b[1])/2
    dist = math.hypot(a[0]-b[0], a[1]-b[1])
    if dist>800: continue
    road = ET.SubElement(roads_folder, "Item"); road.set("class","Part"); road.set("referent", ref())
    pp2 = ET.SubElement(road, "Properties")
    s=ET.SubElement(pp2,"string"); s.set("name","Name"); s.text=f"Road_{i}"
    add_vector3(pp2,"Size", dist, 0.6, 4)
    add_cframe(pp2,"CFrame",(mx, 0.3, mz))
    add_bool(pp2,"Anchored",True); add_bool(pp2,"CanCollide",False)
    add_string(pp2,"Material","Asphalt")
    add_color3(pp2,"Color3uint8","#334155")

# Mountains range decorative
for i in range(10):
    m = ET.SubElement(world_model, "Item"); m.set("class","Part"); m.set("referent", ref())
    pp2 = ET.SubElement(m, "Properties")
    s=ET.SubElement(pp2,"string"); s.set("name","Name"); s.text=f"Mountain_{i}"
    add_vector3(pp2,"Size", random.randint(50,90), random.randint(22,38), random.randint(50,90))
    add_cframe(pp2,"CFrame",(random.randint(-1100,1100), random.randint(8,14), random.randint(-600,600)))
    add_bool(pp2,"Anchored",True)
    add_string(pp2,"Material","Rock")
    add_color3(pp2,"Color3uint8","#78716C")

# Islands
for i in range(6):
    isl = ET.SubElement(world_model, "Item"); isl.set("class","Part"); isl.set("referent", ref())
    pp2 = ET.SubElement(isl, "Properties")
    s=ET.SubElement(pp2,"string"); s.set("name","Name"); s.text=f"Island_{i}"
    add_vector3(pp2,"Size", random.randint(40,70), 3, random.randint(40,70))
    add_cframe(pp2,"CFrame",(random.randint(-1200,1200), -1, random.randint(-700,700)))
    add_bool(pp2,"Anchored",True)
    add_string(pp2,"Material","Sand")
    add_color3(pp2,"Color3uint8","#FDE68A")

# Spawn point
spawn = ET.SubElement(workspace, "Item"); spawn.set("class","SpawnLocation"); spawn.set("referent", ref())
pp2 = ET.SubElement(spawn, "Properties")
s=ET.SubElement(pp2,"string"); s.set("name","Name"); s.text="SpawnLocation"
add_vector3(pp2,"Size",12,1,12)
add_cframe(pp2,"CFrame",(0,5,900))
add_bool(pp2,"Anchored",True); add_bool(pp2,"CanCollide",False)
add_string(pp2,"Material","SmoothPlastic")
add_color3(pp2,"Color3uint8","#22C55E")
add_bool(pp2,"Enabled",True)

out_xml = serialize(roblox)
OUT.write_text(out_xml, encoding="utf-8")
print(f"✅ Built {OUT} ({len(out_xml)} bytes, {counter} instances, {idx} provinces)")
