-- Appended to generated harness. All factories above are the unchanged game sources.
local passed,failed=0,0
local function eq(a,b) assert(a==b,"Expected "..tostring(b)..", got "..tostring(a)) end
local function test(name,fn)
    local ok,err=pcall(fn)
    if ok then passed+=1; print("PASSED | MOCK "..name) else failed+=1; print("FAILED | MOCK "..name.." | "..tostring(err)) end
end
local function module(path) return require(nodes[path]) end
local Shared=nodes["ReplicatedStorage/Shared"]
local Domain=nodes["ServerScriptService/Domain"]
local Config=module("ReplicatedStorage/Shared/GameConfig.luau")
local Items=module("ReplicatedStorage/Shared/ItemDefinitions.luau")
local Weapons=module("ReplicatedStorage/Shared/WeaponDefinitions.luau")
local BuildingDefs=module("ReplicatedStorage/Shared/BuildingDefinitions.luau")
local QuestDefs=module("ReplicatedStorage/Shared/QuestDefinitions.luau")
local EconomyDefs=module("ReplicatedStorage/Shared/EconomyConfig.luau")
local Util=module("ServerScriptService/Domain/Util.luau")
local Inventory=module("ServerScriptService/Domain/Inventory.luau")(Items,Weapons,Config,Util)
local Profile=module("ServerScriptService/Domain/Profile.luau")(Config,Items,Weapons,BuildingDefs,QuestDefs,EconomyDefs,Util,Inventory)
local Quests=module("ServerScriptService/Domain/Quests.luau")(QuestDefs,Inventory,Util)
local Progression=module("ServerScriptService/Domain/Progression.luau")(Config,EconomyDefs,Inventory)
local Factory=module("ReplicatedStorage/Shared/ModelFactory.luau")
local player=Engine.Player
local app
local data
local function snapshot(active)
    local p=Profile.new()
    return {profile=p,campaign=p.Campaigns.coop,mode="coop",active=active,plot=1,save="SessionOnly",lastSave=0,stamina=100,power={supply=0,demand=0,deficit=0,spare=0,powered={}},stage=1,serverTime=os.time(),studio=true,weather="Ясно",raid="Спокойно",shop={food=30,water=30,ammo=200},revision=1}
end
test("client construction and event bindings",function() app=module("StarterPlayer/StarterPlayerScripts/Client.client.luau"); assert(app.Gui.Parent); assert(app.Menu); assert(app.HUD) end)
test("loading snapshot reveals menu and marks client ready",function() data=snapshot(false); app.Remotes.StateRemote.OnClientEvent:Fire("state",data); eq(app.Menu.Visible,true); eq(app.HUD.Visible,false); eq(player:GetAttribute("LastCityClientReady"),true) end)
test("play button sends real server action",function() app.PlayButton.Activated:Fire(); eq(app.Remotes.SessionRemote.LastPayload.action,"play") end)
test("active snapshot opens first-time tutorial",function() data=snapshot(true); app.Remotes.StateRemote.OnClientEvent:Fire("state",data); eq(app.HUD.Visible,true); eq(app.Menu.Visible,false); eq(app.PanelName,"tutorial") end)
for _,name in {"inventory","quests","tutorial","build","base","craft","cosmetics","trade","weapons","profile","achievements","settings","updates","map"} do
    test("construct panel "..name,function() app:OpenPanel(name); assert(#app.Body:GetChildren()>1); app:RefreshPanel(); app:ClosePanel(); eq(app.PanelName,nil) end)
end
test("NPC dialogue builds viewport and all reply branches",function()
    local model=Factory.rig("Alex",CFrame.new(0,3,-100),Color3.fromRGB(40,60,40),Color3.fromRGB(180,140,120)); model.Parent=workspace
    app.Remotes.StateRemote.OnClientEvent:Fire("dialogue",{id="alex",name="Капитан Алекс",role="Командир",text="Помоги убежищу",model=model})
    eq(app.PanelName,"dialogue"); assert(app.Camera.Dialogue)
    for _,child in app.Body:GetDescendants() do if child:IsA("TextButton") and child.Text=="Что я получу?" then child.Activated:Fire(); break end end
    assert(app.DialogueText:find("Coins")); app:ClosePanel()
end)
test("every building preview contains real parts and pivots",function()
    for id,def in BuildingDefs do local m=Factory.building(id,def,CFrame.new()); assert(m.PrimaryPart); assert(#m:GetDescendants()>2) end
end)
test("placing ghost checks ownership and emits snapped coordinates",function()
    app:ClosePanel(); player.Character.HumanoidRootPart.CFrame=CFrame.new(-144,3,-252)
    app.Build:Start("wall"); app.Build.X=0; app.Build.Z=0; app.Build.Valid=app.Build:Check(); assert(app.Build.Valid)
    app.Build:Place(); eq(app.Remotes.BuildRemote.LastPayload.x,0); eq(app.Remotes.BuildRemote.LastPayload.id,"wall"); app.Build:Cancel()
    eq(app.Build.Active,false)
end)
test("UI survives mobile portrait layout",function() workspace.CurrentCamera.ViewportSize=Vector2.new(390,844); app:Layout(); app:OpenPanel("inventory"); app:OpenPanel("dialogue"); app:ClosePanel(); app:UpdateHUD() end)
test("UI survives narrow landscape layout",function() workspace.CurrentCamera.ViewportSize=Vector2.new(844,390); app:Layout(); for _,name in {"base","quests","settings"} do app:OpenPanel(name) end; app:ClosePanel(); app:UpdateHUD() end)
test("camera callbacks execute menu survivor and commander paths",function()
    workspace.CurrentCamera.ViewportSize=Vector2.new(1280,720); app:Layout()
    local run=game:GetService("RunService")
    data=snapshot(false); app.Remotes.StateRemote.OnClientEvent:Fire("state",data); run.Bound.LastCityCamera(0.016)
    data=snapshot(true); data.campaign.Tutorial.status="Skipped"; app.Remotes.StateRemote.OnClientEvent:Fire("state",data); app:ClosePanel(); run.Bound.LastCityCamera(0.016)
    app.Camera:SetCommander(true); run.Bound.LastCityCamera(0.016); app.Camera:SetCommander(false)
end)
test("quest-completed and equipped-firearm HUD paths",function()
    data=snapshot(true); data.campaign.Tutorial.status="Skipped"; Quests.accept(data.campaign,"first_blood",os.time()); Quests.record(data.campaign,"kill","walker",5,os.time())
    Quests.claim(data.profile,data.campaign,"first_blood",os.time())
    for _,e in data.campaign.Inventory do if e.id=="pistol" then Inventory.equip(data.campaign,e.uid) end end
    app.Remotes.StateRemote.OnClientEvent:Fire("state",data); app:UpdateHUD(); assert(app.EquipmentTitle.Text:find("P9"))
end)
test("UI settings are sent to server, not applied as fabricated state",function()
    app:OpenPanel("settings")
    for _,child in app.Body:GetDescendants() do if child:IsA("TextButton") and child.Text=="Low" then child.Activated:Fire(); break end end
    eq(app.Remotes.SessionRemote.LastPayload.action,"setting"); eq(app.Remotes.SessionRemote.LastPayload.value,"Low"); eq(app.State.profile.Settings.graphics,"High")
end)

local function context()
    local p=Profile.new()
    local ctx={Sessions={},Services={},Config=Config,Items=Items,Weapons=Weapons,BuildingDefs=BuildingDefs,QuestDefs=QuestDefs,EconomyDefs=EconomyDefs,Util=Util,Inventory=Inventory,Profile=Profile,Quests=Quests,Progression=Progression,Factory=Factory,Notices={},NearNPC=true,NearPlot=true}
    ctx.Lease=module("ServerScriptService/Domain/Lease.luau"); ctx.RateLimiter=module("ServerScriptService/Domain/RateLimiter.luau")
    ctx.Building=module("ServerScriptService/Domain/Building.luau")(BuildingDefs,Inventory,Config,Util)
    ctx.Economy=module("ServerScriptService/Domain/Economy.luau")(BuildingDefs,EconomyDefs,Inventory,Util)
    ctx.Remotes=Engine.add(nil,"Folder","TestRemotes")
    for _,name in {"SessionRemote","QuestRemote","InventoryRemote","CombatRemote","BuildRemote","ShopRemote","NPCRemote","MovementRemote","CraftingRemote","StateRemote","EffectsRemote"} do Engine.add(ctx.Remotes,"RemoteEvent",name) end
    local s={Data=p,Campaign=p.Campaigns.coop,Loaded=true,Active=true,Plot=1,Stamina=100,SaveState="Saved",Persistent=true,Expires=os.time()+180,Token="mock-server:unique-mock-token",Key="player_123"}
    ctx.Sessions[player]=s
    function ctx:Alive() return player.Character.Humanoid end
    function ctx:Root() return player.Character.HumanoidRootPart end
    function ctx:InPlot() return self.NearPlot end
    function ctx:InShelter() return true end
    function ctx:LineOfSight() return true end
    function ctx:Changed() s.Data.Revision+=1 end
    function ctx:Notify(_,text,kind) table.insert(self.Notices,text) end
    function ctx:FX(kind,payload) self.LastFX=payload end
    ctx.Services.NPCService={Near=function() return ctx.NearNPC end}
    ctx.Services.NetworkService=module("ServerScriptService/Services/NetworkService.luau")(ctx)
    ctx.Services.CombatService={SyncTool=function() ctx.ToolSync=true end}
    ctx.Services.QuestService=module("ServerScriptService/Services/QuestService.luau")(ctx); ctx.Services.QuestService:Init()
    ctx.World={Plots=Engine.add(nil,"Folder","Plots"),Buildings=Engine.add(workspace,"Folder","TestBuildings")}
    for index in Config.Plots do local plot=Engine.add(ctx.World.Plots,"Model","Plot"..index); local terminal=Engine.add(plot,"Part","Terminal"); local gui=Engine.add(terminal,"BillboardGui","OwnerGui"); Engine.add(gui,"TextLabel","Label") end
    ctx.Services.ResourceService={CanDrop=function() return true end,Drop=function() ctx.Dropped=true end}
    return ctx,s
end
local function request(ctx,remote,payload) ctx.Remotes[remote].OnServerEvent:Fire(player,payload) end
test("quest remote enforces NPC proximity",function() local ctx,s=context(); ctx.NearNPC=false; request(ctx,"QuestRemote",{action="accept",id="first_blood"}); eq(s.Campaign.Quests.first_blood,nil) end)
test("quest remote accepts at NPC and pays once",function() local ctx,s=context(); request(ctx,"QuestRemote",{action="accept",id="first_blood"}); Quests.record(s.Campaign,"kill","walker",5,os.time()); request(ctx,"QuestRemote",{action="claim",id="first_blood"}); request(ctx,"QuestRemote",{action="claim",id="first_blood"}); eq(s.Data.Coins,200); eq(Inventory.count(s.Campaign,"pistol"),1) end)
test("client-supplied reward fields do not affect real reward",function() local ctx,s=context(); request(ctx,"QuestRemote",{action="accept",id="first_blood",coins=9999999}); request(ctx,"QuestRemote",{action="claim",id="first_blood",coins=9999999}); eq(s.Data.Coins,0) end)
test("frozen closing and inactive sessions cannot mutate quests",function() for _,key in {"Frozen","Closing","Active"} do local ctx,s=context(); s[key]=key~="Active"; request(ctx,"QuestRemote",{action="accept",id="first_blood"}); eq(s.Campaign.Quests.first_blood,nil) end end)
test("remote payload rejects nested data NaN instances and long strings",function()
    local cases={"invalid",{action="accept",id="first_blood",data={}}, {action="accept",id="first_blood",count=0/0}, {action="accept",id="first_blood",target=player}, {action=string.rep("A",100),id="first_blood"}}
    for _,payload in cases do local ctx,s=context(); request(ctx,"QuestRemote",payload); eq(s.Campaign.Quests.first_blood,nil) end
end)
test("remote burst rate limiter bounds callback execution",function() local ctx=context(); local n=0; ctx.Services.NetworkService:Register("InventoryRemote",true,function() n+=1 end); for _=1,100 do request(ctx,"InventoryRemote",{action="sort"}) end; assert(n<=8); assert(n>0) end)
test("inventory adapter rejects remote stock access outside own base",function() local ctx,s=context(); ctx.NearPlot=false; local service=module("ServerScriptService/Services/InventoryService.luau")(ctx); service:Init(); local before=s.Campaign.Stockpile.metal; request(ctx,"InventoryRemote",{action="withdraw",id="metal",count=5}); eq(s.Campaign.Stockpile.metal,before) end)
test("inventory adapter use consumes exactly one real medkit",function() local ctx,s=context(); local service=module("ServerScriptService/Services/InventoryService.luau")(ctx); service:Init(); player.Character.Humanoid.Health=20; local uid; for _,e in s.Campaign.Inventory do if e.id=="medkit" then uid=e.uid end end; request(ctx,"InventoryRemote",{action="use",uid=uid}); eq(player.Character.Humanoid.Health,80); eq(Inventory.count(s.Campaign,"medkit"),1) end)
test("building adapter refuses placement on another plot",function() local ctx,s=context(); ctx.NearPlot=false; ctx.Services.BuildingService=module("ServerScriptService/Services/BuildingService.luau")(ctx); ctx.Services.BuildingService:Init(); request(ctx,"BuildRemote",{action="place",id="wall",x=0,z=0,r=0}); eq(#s.Campaign.Buildings,0); eq(s.Campaign.Cash,200) end)
test("building adapter creates a model and charges resources",function() local ctx,s=context(); ctx.Services.BuildingService=module("ServerScriptService/Services/BuildingService.luau")(ctx); ctx.Services.BuildingService:Init(); request(ctx,"BuildRemote",{action="place",id="wall",x=0,z=0,r=0}); eq(#s.Campaign.Buildings,1); eq(s.Campaign.Cash,190); assert(ctx.Services.BuildingService.Models[player].b1); eq(ctx.Services.BuildingService.Models[player].b1:GetAttribute("Owner"),player.UserId) end)
test("building adapter destruction removes model and worker job",function() local ctx,s=context(); ctx.Services.BuildingService=module("ServerScriptService/Services/BuildingService.luau")(ctx); ctx.Services.BuildingService:Init(); request(ctx,"BuildRemote",{action="place",id="wall",x=0,z=0,r=0}); s.Campaign.Workers[1].job="b1"; ctx.Services.BuildingService:Damage(player,"b1",10000); eq(#s.Campaign.Buildings,0); eq(s.Campaign.Workers[1].job,""); eq(ctx.Services.BuildingService.Models[player].b1,nil) end)
local function combatContext()
    local ctx,s=context(); player.Character.Humanoid.Health=100
    for _,tool in player.Character:GetChildren() do if tool:IsA("Tool") then tool:Destroy() end end
    ctx.Services.CombatService=module("ServerScriptService/Services/CombatService.luau")(ctx); ctx.Services.CombatService:Init()
    ctx.Services.ZombieService={Zombies={},FromPart=function(_,part) return part end,Damage=function(_,z,amount,owner) ctx.Damage=(ctx.Damage or 0)+amount end}
    Inventory.grant(s.Campaign,{pistol=1,ammo=16}); local e
    for _,entry in s.Campaign.Inventory do if entry.id=="pistol" then e=entry end end
    Inventory.equip(s.Campaign,e.uid); e.ammo=8; ctx.Services.CombatService:SyncTool(player)
    workspace.Raycast=function() return {Instance={},Position=Vector3.new(0,3,-120)} end
    return ctx,s,e
end
test("combat adapter spends ammo and uses server weapon damage",function() local ctx,s,e=combatContext(); request(ctx,"CombatRemote",{action="attack",aim=Vector3.new(0,0,-1),damage=999999}); eq(e.ammo,7); eq(ctx.Damage,32); assert(ctx.LastFX.hit) end)
test("combat adapter blocks shot spam",function() local ctx,s,e=combatContext(); for _=1,3 do request(ctx,"CombatRemote",{action="attack",aim=Vector3.new(0,0,-1)}) end; eq(e.ammo,7); eq(ctx.Damage,32) end)
test("combat adapter validates aim equipped tool and ammunition",function() local ctx,s,e=combatContext(); request(ctx,"CombatRemote",{action="attack",aim=Vector3.new(0,0,0)}); eq(e.ammo,8); e.ammo=0; request(ctx,"CombatRemote",{action="attack",aim=Vector3.new(0,0,-1)}); eq(ctx.Damage,nil); e.ammo=8; player.Character:FindFirstChildOfClass("Tool"):Destroy(); request(ctx,"CombatRemote",{action="attack",aim=Vector3.new(0,0,-1)}); eq(e.ammo,8) end)
test("reload adapter defers ammo transfer and consumes reserve once",function() local ctx,s,e=combatContext(); e.ammo=0; ctx.Services.CombatService:Reload(player,s); eq(e.ammo,0); local delay=Engine.task.delayed[#Engine.task.delayed]; delay.callback(); eq(e.ammo,8); eq(Inventory.count(s.Campaign,"ammo"),8); delay.callback(); eq(Inventory.count(s.Campaign,"ammo"),8) end)
test("reload completion after player departure cannot grant ammo",function() local ctx,s,e=combatContext(); e.ammo=0; ctx.Services.CombatService:Reload(player,s); local delay=Engine.task.delayed[#Engine.task.delayed]; ctx.Sessions[player]=nil; delay.callback(); eq(e.ammo,0); eq(Inventory.count(s.Campaign,"ammo"),16) end)

local function saveContext()
    Engine.Store.records={}; Engine.Store.calls=0; Engine.Store.fail=false; Engine.Store.replay=1
    game:GetService("RunService").Studio=true; player.Kicked=nil
    local ctx,s=context(); local save=module("ServerScriptService/Services/SaveService.luau")(ctx); ctx.Services.SaveService=save
    return ctx,s,save
end
test("SaveService acquires new profile with UpdateAsync lease",function() local ctx,s,save=saveContext(); local loaded=assert(save:Load(player)); eq(loaded.Persistent,true); assert(Engine.Store.records.player_123.Lease); eq(Engine.Store.calls,1) end)
test("SaveService stores coins inventory quests and releases lease",function() local ctx,s,save=saveContext(); local loaded=assert(save:Load(player)); for k,v in loaded do s[k]=v end; s.Campaign=s.Data.Campaigns.coop; s.Data.Coins=321; Quests.accept(s.Campaign,"first_blood",os.time()); assert(save:Save(player,true)); eq(Engine.Store.records.player_123.Data.Coins,321); eq(Engine.Store.records.player_123.Data.Campaigns.coop.Quests.first_blood.status,"Active"); eq(Engine.Store.records.player_123.Lease,nil) end)
test("UpdateAsync callback replay does not duplicate startup items",function() local ctx,s,save=saveContext(); Engine.Store.replay=3; local loaded=assert(save:Load(player)); eq(Inventory.count(loaded.Data.Campaigns.coop,"crowbar"),1) end)
test("save outage preserves existing data and retries four times",function() local ctx,s,save=saveContext(); local loaded=assert(save:Load(player)); for k,v in loaded do s[k]=v end; Engine.Store.fail=true; s.Data.Coins=999; local before=Engine.Store.calls; eq(save:Save(player,false),false); eq(Engine.Store.calls-before,4); eq(s.SaveState,"Error"); eq(Engine.Store.records.player_123.Data.Coins,0) end)
test("live load failure does not silently create default persistent profile",function() local ctx,s,save=saveContext(); Engine.Store.fail=true; game:GetService("RunService").Studio=false; local loaded,err=save:Load(player); eq(loaded,nil); assert(err); eq(Engine.Store.records.player_123,nil) end)
test("Studio load failure explicitly falls back to session-only",function() local ctx,s,save=saveContext(); Engine.Store.fail=true; local loaded=assert(save:Load(player)); eq(loaded.Persistent,false); eq(loaded.SaveState,"SessionOnly") end)
test("future schema is never overwritten during load",function() local ctx,s,save=saveContext(); Engine.Store.records.player_123={Data={SchemaVersion=99}}; local loaded=save:Load(player); eq(loaded,nil); eq(Engine.Store.records.player_123.Data.SchemaVersion,99) end)
test("missing data in an existing envelope is rejected",function() local ctx,s,save=saveContext(); Engine.Store.records.player_123={Broken=true}; eq(save:Load(player),nil); assert(Engine.Store.records.player_123.Broken) end)
test("lost lease freezes the stale server without overwriting new owner",function() local ctx,s,save=saveContext(); Engine.Store.records.player_123={Data={SchemaVersion=1,Coins=700},Lease={Token="another-server",Expires=os.time()+180}}; eq(save:Save(player,false),false); eq(s.Frozen,true); assert(player.Kicked); eq(Engine.Store.records.player_123.Data.Coins,700) end)
local server
local serverSession
test("full server composition root initializes every service",function()
    local world=Engine.add(workspace,"Folder","World")
    for _,name in {"Plots","Buildings","NPCSpawns","LootSpawns","Zombies","ZombieSpawns"} do Engine.add(world,"Folder",name) end
    for index in Config.Plots do
        local p=Engine.add(world.Plots,"Model","Plot"..index); local t=Engine.add(p,"Part","Terminal"); local g=Engine.add(t,"BillboardGui","OwnerGui"); Engine.add(g,"TextLabel","Label")
    end
    Engine.add(world.ZombieSpawns,"Part","Spawn1").CFrame=CFrame.new(0,0,30)
    workspace.Raycast=function() return nil end
    game:GetService("RunService").Studio=true
    Engine.Store.fail=false; Engine.Store.records={}; player.Kicked=nil
    server=module("ServerScriptService/Bootstrap.server.luau")
    eq(workspace:GetAttribute("LastCityReady"),true); assert(server.Services.PlayerDataService); assert(server.Services.NPCService.NPCs.alex)
end)
test("full server join loads data restores base and creates workers",function()
    server.Services.PlayerDataService:Join(player)
    serverSession=assert(server.Sessions[player]); eq(serverSession.Loaded,true); eq(serverSession.Active,false)
    eq(#server.Services.NPCService.Workers[player],2)
    server.Services.PlayerDataService:Character(player,player.Character)
end)
test("full server play unfreezes character and equips starter weapon",function()
    request(server,"SessionRemote",{action="play"}); eq(serverSession.Active,true); eq(player.Character.HumanoidRootPart.Anchored,false)
    assert(player.Character:FindFirstChildOfClass("Tool")); assert(serverSession.Campaign.Started)
end)
test("full server five kills complete quest and pay only once",function()
    player.Character.HumanoidRootPart.CFrame=CFrame.new(-18,3,-55)
    request(server,"QuestRemote",{action="accept",id="first_blood"})
    eq(serverSession.Campaign.Quests.first_blood.status,"Active")
    for _=1,5 do
        local z=server.Services.ZombieService:Spawn("walker",Vector3.new(-18,0,-10))
        server.Services.ZombieService:Damage(z,1000,player)
        server.Services.ZombieService:Damage(z,1000,player)
    end
    eq(serverSession.Campaign.Stats.kills,5); eq(serverSession.Campaign.Quests.first_blood.status,"Completed")
    request(server,"QuestRemote",{action="claim",id="first_blood"}); request(server,"QuestRemote",{action="claim",id="first_blood"})
    eq(Inventory.count(serverSession.Campaign,"pistol"),1); eq(serverSession.Data.Coins,275)
end)
test("full server loot prompt cannot be double-picked",function()
    local node=Engine.add(server.World.LootSpawns,"Part","TestMetal"); node.CFrame=CFrame.new(-18,2,-53)
    local prompt=server.Services.ResourceService:Attach(node,"metal",10,90,false)
    prompt.Triggered:Fire(player); prompt.Triggered:Fire(player)
    eq(Inventory.count(serverSession.Campaign,"metal"),10); eq(prompt.Enabled,false)
end)
test("full server snapshot contains authoritative progress and save status",function()
    server.Services.PlayerDataService:Snapshot(player)
    local outgoing=server.Remotes.StateRemote.LastClient
    eq(outgoing.kind,"state"); eq(outgoing.payload.campaign.Stats.kills,5); eq(outgoing.payload.profile.Coins,275)
    eq(outgoing.payload.revision,serverSession.Data.Revision); eq(outgoing.payload.save,"Saved")
end)
test("dropped items cannot cross player profiles or session leases",function()
    local c=serverSession.Campaign
    local node=Engine.add(server.World.LootSpawns,"Part","PrivateDrop"); node.CFrame=CFrame.new(-18,2,-53)
    local prompt=server.Services.ResourceService:Attach(node,"wood",3,0,true,player.UserId,"old-session-token")
    prompt.Triggered:Fire(player); eq(Inventory.count(c,"wood"),0); assert(node.Parent)
    local node2=Engine.add(server.World.LootSpawns,"Part","OtherPlayersDrop"); node2.CFrame=node.CFrame
    local prompt2=server.Services.ResourceService:Attach(node2,"wood",3,0,true,9876,serverSession.Token)
    prompt2.Triggered:Fire(player); eq(Inventory.count(c,"wood"),0); assert(node2.Parent)
end)
test("network guard denies unvalidated character warps",function()
    local ctx,s=context(); ctx.PositionValid=function() return false end
    request(ctx,"QuestRemote",{action="accept",id="first_blood"}); eq(s.Campaign.Quests.first_blood,nil)
end)
test("full server tutorial starts skips and replays one-time milestones",function()
    request(server,"SessionRemote",{action="tutorial",choice="start"}); eq(serverSession.Campaign.Tutorial.status,"Active")
    assert(serverSession.Campaign.Tutorial.facts.quest); assert(serverSession.Campaign.Tutorial.facts.reward)
    request(server,"SessionRemote",{action="tutorial",choice="skip"}); eq(serverSession.Campaign.Tutorial.status,"Skipped")
    request(server,"SessionRemote",{action="tutorial",choice="start"}); eq(serverSession.Campaign.Tutorial.status,"Active")
    eq(serverSession.Data.Coins,275)
end)
test("full server NPC prompt returns actual dialogue payload",function()
    player.Character.HumanoidRootPart.CFrame=CFrame.new(-18,3,-55)
    local npc=server.Services.NPCService.NPCs.alex
    npc.model.PrimaryPart:FindFirstChildOfClass("ProximityPrompt").Triggered:Fire(player)
    eq(server.Remotes.StateRemote.LastClient.kind,"dialogue"); eq(server.Remotes.StateRemote.LastClient.payload.id,"alex")
    eq(serverSession.Campaign.Tutorial.facts.talk,true)
end)
print(string.format("\nADAPTER MOCK RESULTS: %d PASSED / %d FAILED (NOT Studio tests)",passed,failed))
assert(failed==0,"Mock adapter regression suite failed")
