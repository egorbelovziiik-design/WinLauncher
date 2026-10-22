--!nonstrict
-- Narrow deterministic API double for construction/handler tests only.
-- NOT a renderer, physics simulator, DataStore, or substitute for Roblox Studio QA.
local M = {}
local rawTypeof = typeof
function M.typeof(value) return type(value) == "table" and rawget(value, "_type") or rawTypeof(value) end
local function vector(n, x, y, z)
    local v = {_type = n == 2 and "Vector2" or "Vector3", X = x or 0, Y = y or 0, Z = z or 0}
    local mt = {}
    mt.__add = function(a,b) return vector(n,a.X+b.X,a.Y+b.Y,a.Z+b.Z) end
    mt.__sub = function(a,b) return vector(n,a.X-b.X,a.Y-b.Y,a.Z-b.Z) end
    mt.__mul = function(a,b) if type(a)=="number" then a,b=b,a end; return vector(n,a.X*b,a.Y*b,a.Z*b) end
    mt.__div = function(a,b) return vector(n,a.X/b,a.Y/b,a.Z/b) end
    mt.__eq = function(a,b) return a.X==b.X and a.Y==b.Y and a.Z==b.Z end
    mt.__index = function(a,key)
        if key=="Magnitude" then return math.sqrt(a.X*a.X+a.Y*a.Y+a.Z*a.Z) end
        if key=="Unit" then local mag=a.Magnitude; return mag>0 and a/mag or vector(n,0,0,0) end
        if key=="Dot" then return function(self,b) return self.X*b.X+self.Y*b.Y+self.Z*b.Z end end
    end
    return setmetatable(v,mt)
end
M.Vector3={new=function(x,y,z) return vector(3,x,y,z) end,zero=vector(3,0,0,0)}
M.Vector2={new=function(x,y) return vector(2,x,y,0) end,zero=vector(2,0,0,0)}
local function color(r,g,b)
    return {_type="Color3",R=r,G=g,B=b,Lerp=function(self,other,t) return color(self.R+(other.R-self.R)*t,self.G+(other.G-self.G)*t,self.B+(other.B-self.B)*t) end}
end
M.Color3={new=color,fromRGB=function(r,g,b) return color(r/255,g/255,b/255) end,fromHex=function(hex) local n=assert(tonumber(hex,16)); return color(math.floor(n/65536)%256/255,math.floor(n/256)%256/255,n%256/255) end}
local function frame(pos)
    local f={_type="CFrame",Position=pos or M.Vector3.zero,LookVector=M.Vector3.new(0,0,-1)}
    function f:Lerp(other,t) return frame(self.Position+(other.Position-self.Position)*t) end
    function f:PointToWorldSpace(p) return self.Position+p end
    return setmetatable(f,{__mul=function(a,b) return frame(a.Position+b.Position) end})
end
M.CFrame={new=function(x,y,z) return frame(type(x)=="table" and x or M.Vector3.new(x,y,z)) end,lookAt=function(from,to) local f=frame(from); f.LookVector=(to-from).Unit; return f end,Angles=function() return frame() end}
M.UDim={new=function(scale,offset) return {_type="UDim",Scale=scale or 0,Offset=offset or 0} end}
M.UDim2={new=function(a,b,c,d) return {_type="UDim2",X=M.UDim.new(a,b),Y=M.UDim.new(c,d)} end}
M.UDim2.fromScale=function(x,y) return M.UDim2.new(x,0,y,0) end
M.UDim2.fromOffset=function(x,y) return M.UDim2.new(0,x,0,y) end
M.Enum=setmetatable({},{__index=function(t,key)
    local sub=setmetatable({},{__index=function(s,k) local e={_type="EnumItem",Name=k,Value=1}; rawset(s,k,e); return e end})
    rawset(t,key,sub); return sub
end})
for _,name in {"ColorSequence","NumberSequence","NumberSequenceKeypoint","TweenInfo"} do M[name]={new=function(...) return {_type=name,args={...}} end} end
M.RaycastParams={new=function() return {} end}
M.Random={new=function() return {NextNumber=function(_,a,b) return (a+b)/2 end} end}
function M.signal()
    local event={listeners={}}
    function event:Connect(callback)
        local connection={Connected=true}; function connection:Disconnect() self.Connected=false end
        table.insert(self.listeners,{callback=callback,connection=connection}); return connection
    end
    function event:Fire(...) for _,l in table.clone(self.listeners) do if l.connection.Connected then l.callback(...) end end end
    return event
end
local inheritance={
    Part={BasePart=true,PVInstance=true}, SpawnLocation={Part=true,BasePart=true,PVInstance=true}, Model={PVInstance=true},
    Frame={GuiObject=true},TextButton={GuiObject=true,GuiButton=true},TextLabel={GuiObject=true},ImageLabel={GuiObject=true},ScrollingFrame={GuiObject=true},ViewportFrame={GuiObject=true},CanvasGroup={GuiObject=true},
    PointLight={Light=true},SpotLight={Light=true},Script={LuaSourceContainer=true},LocalScript={LuaSourceContainer=true},ModuleScript={LuaSourceContainer=true},
}
local signals={Activated=true,MouseEnter=true,MouseLeave=true,OnClientEvent=true,OnServerEvent=true,RenderStepped=true,Heartbeat=true,InputChanged=true,InputBegan=true,InputEnded=true,WindowFocusReleased=true,CharacterAdded=true,HealthChanged=true,Died=true,PlayerAdded=true,PlayerRemoving=true,TeleportInitFailed=true,Triggered=true}
local expected={Text="string",TextColor3="Color3",BackgroundColor3="Color3",Color="Color3",TintColor="Color3",CFrame="CFrame",Visible="boolean",Enabled="boolean",TextSize="number",WalkSpeed="number",Transparency="number",GroupTransparency="number",Source="string"}
local methods={}
local instanceMT={}
function instanceMT.__index(self,key)
    local props=rawget(self,"_props")
    if props[key]~=nil then return props[key] end
    if methods[key] then return methods[key] end
    for _,child in rawget(self,"_children") do if child.Name==key then return child end end
    if signals[key] then local signal=M.signal(); props[key]=signal; return signal end
    if key=="Position" and self:IsA("BasePart") then return self.CFrame.Position end
    return nil
end
function instanceMT.__newindex(self,key,value)
    local props=rawget(self,"_props")
    if key=="Parent" then
        local old=props.Parent
        if old then local i=table.find(old._children,self); if i then table.remove(old._children,i) end end
        props.Parent=value; if value then assert(rawget(value,"_children"),"Parent must be Instance"); table.insert(value._children,self) end
    else
        if expected[key] and value~=nil and not (self.ClassName=="UIGradient" and (key=="Color" and M.typeof(value)=="ColorSequence" or key=="Transparency" and M.typeof(value)=="NumberSequence")) then assert(M.typeof(value)==expected[key],self.ClassName.."."..key.." expected "..expected[key]..", got "..M.typeof(value)) end
        props[key]=value
    end
end
M.Instance={}
function M.Instance.new(class)
    local self=setmetatable({_type="Instance",_props={ClassName=class,Name=class},_children={},_attributes={}},instanceMT)
    self.CFrame=M.CFrame.new(); self.Size=M.Vector3.new(1,1,1); self.Color=M.Color3.new(1,1,1)
    self.Visible=true; self.Enabled=true; self.Transparency=0; self.CanvasPosition=M.Vector2.zero
    self.AssemblyLinearVelocity=M.Vector3.zero; self.ViewportSize=M.Vector2.new(1280,720)
    self.Health=100; self.MaxHealth=100; self.HipHeight=0; self.MoveDirection=M.Vector3.zero
    self.C0=M.CFrame.new(); self.C1=M.CFrame.new(); self.Grip=M.CFrame.new(); self.Rotation=0
    return self
end
function methods:IsA(class) return self.ClassName==class or class=="Instance" or (inheritance[self.ClassName] and inheritance[self.ClassName][class]) or false end
function methods:GetChildren() return table.clone(self._children) end
function methods:GetDescendants()
    local results={}; local function add(node) for _,c in node:GetChildren() do table.insert(results,c); add(c) end end; add(self); return results
end
function methods:FindFirstChild(name,recursive)
    for _,c in self:GetChildren() do if c.Name==name then return c end end
    if recursive then for _,c in self:GetDescendants() do if c.Name==name then return c end end end
    return nil
end
function methods:WaitForChild(name) return assert(self:FindFirstChild(name),"Missing mock child "..self.Name.."."..name) end
function methods:FindFirstChildOfClass(class) for _,c in self:GetChildren() do if c:IsA(class) then return c end end; return nil end
function methods:FindFirstAncestorOfClass(class) local p=self.Parent; while p do if p:IsA(class) then return p end; p=p.Parent end; return nil end
function methods:IsDescendantOf(parent) local p=self.Parent; while p do if p==parent then return true end; p=p.Parent end; return false end
function methods:Destroy() for _,child in self:GetChildren() do child:Destroy() end; self.Parent=nil end
function methods:SetAttribute(key,value)
    local changed=self._attributes[key]~=value; self._attributes[key]=value
    if changed and self._attributeSignals and self._attributeSignals[key] then self._attributeSignals[key]:Fire() end
end
function methods:GetAttributeChangedSignal(key)
    self._attributeSignals=self._attributeSignals or {}
    if not self._attributeSignals[key] then self._attributeSignals[key]=M.signal() end
    return self._attributeSignals[key]
end
function methods:RemoveDefaultLoadingScreen() end
function methods:GetAttribute(key) return self._attributes[key] end
function methods:Clone()
    local result=M.Instance.new(self.ClassName); local map={[self]=result}
    local function cloneChildren(a,b)
        for key,value in a._props do if key~="Parent" and not signals[key] then b[key]=value end end
        for _,child in a:GetChildren() do local n=M.Instance.new(child.ClassName); map[child]=n; n.Parent=b; cloneChildren(child,n) end
    end
    cloneChildren(self,result)
    for original,clone in map do for key,val in original._props do if M.typeof(val)=="Instance" and key~="Parent" then clone[key]=map[val] or val end end end
    return result
end
function methods:Play() self.IsPlaying=true end
function methods:Stop() self.IsPlaying=false end
function methods:FireServer(payload) self.LastPayload=payload; self.FireCount=(self.FireCount or 0)+1 end
function methods:FireClient(player,kind,payload) self.LastClient={player=player,kind=kind,payload=payload} end
function methods:PivotTo(cf) self.CFrame=cf; if self.PrimaryPart then self.PrimaryPart.CFrame=cf end end
function methods:ViewportPointToRay(x,y) return {Origin=M.Vector3.new(0,80,0),Direction=M.Vector3.new(0,-1,0)} end
function methods:Raycast() return nil end
function methods:MoveTo(position) self.Target=position end
function methods:SetNetworkOwner() end
function methods:TakeDamage(amount) self.Health=math.max(0,self.Health-amount); if self.Health<=0 then self.Died:Fire() end end
function methods:EquipTool(tool) tool.Parent=self.Parent end
M.task={queued={},delayed={}}
function M.task.spawn(callback,...) table.insert(M.task.queued,{callback=callback,args={...}}) end
function M.task.delay(seconds,callback) table.insert(M.task.delayed,{seconds=seconds,callback=callback}) end
function M.task.wait() return 0.1 end
function M.task.defer(callback) table.insert(M.task.queued,{callback=callback,args={}}) end
local services={}
M.game={JobId="mock-server",PrivateServerId="",PrivateServerOwnerId=0,PlaceId=1}
function M.game:GetService(name) return assert(services[name],"Unmocked Roblox service: "..name) end
function M.game:BindToClose(callback) M.close=callback end
function M.add(parent,class,name) local o=M.Instance.new(class); o.Name=name; o.Parent=parent; return o end
function M.service(name) local s=M.Instance.new(name); s.Name=name; services[name]=s; return s end
M.workspace=M.service("Workspace"); M.workspace.CurrentCamera=M.add(M.workspace,"Camera","Camera")
M.service("ReplicatedStorage"); M.service("ReplicatedFirst"); M.service("ServerScriptService"); M.service("SoundService")
local players=M.service("Players"); M.Player=M.add(players,"Player","Tester"); players.LocalPlayer=M.Player
M.Player.UserId=123; M.Player.DisplayName="Тестовый выживший"; M.Player.CameraMinZoomDistance=7
M.add(M.Player,"PlayerGui","PlayerGui"); M.add(M.Player,"PlayerScripts","PlayerScripts")
local character=M.add(M.workspace,"Model","Character"); M.Player.Character=character
M.add(character,"Part","HumanoidRootPart").CFrame=M.CFrame.new(0,3,-106)
M.add(character,"Part","Head").CFrame=M.CFrame.new(0,4.5,-106)
M.add(character,"Humanoid","Humanoid")
character.PrimaryPart=character.HumanoidRootPart
M.add(M.Player,"Backpack","Backpack")
function players:GetPlayers() return {M.Player} end
function M.Player:Kick(message) self.Kicked=message end
local input=M.service("UserInputService"); input.TouchEnabled=false
function input:GetFocusedTextBox() return nil end
function input:GetMouseLocation() return M.Vector2.new(640,396) end
function input:IsKeyDown() return false end
local gui=M.service("GuiService"); function gui:GetGuiInset() return M.Vector2.new(0,36),M.Vector2.zero end
local run=M.service("RunService"); run.Bound={}; run.Studio=true
function run:BindToRenderStep(name,priority,callback) self.Bound[name]=callback end
function run:IsStudio() return self.Studio end
local actions=M.service("ContextActionService"); actions.Actions={}
function actions:BindAction(name,callback) self.Actions[name]=callback end
function actions:SetTitle() end
function actions:SetPosition() end
function actions:GetButton() return nil end
local tween=M.service("TweenService"); function tween:Create(instance,info,goals) return {Play=function() for k,v in goals do instance[k]=v end end} end
local starter=M.service("StarterGui"); function starter:SetCoreGuiEnabled() end
local light=M.service("Lighting"); light.ClockTime=15.5; M.add(light,"BloomEffect","Bloom"); M.add(light,"ColorCorrectionEffect","Color")
local path=M.service("PathfindingService"); function path:CreatePath() return {ComputeAsync=function() end,Status=M.Enum.PathStatus.Success,GetWaypoints=function() return {} end} end
local debris=M.service("Debris"); function debris:AddItem() end
local collection=M.service("CollectionService"); function collection:AddTag() end
local tele=M.service("TeleportService"); function tele:TeleportAsync() return {} end
local http=M.service("HttpService"); function http:GenerateGUID() return "unique-mock-token" end
M.Store={records={},calls=0,fail=false,replay=1}
local function copy(value)
    if type(value)~="table" then return value end
    local result={}; for k,v in value do result[k]=copy(v) end; return result
end
function M.Store:UpdateAsync(key,callback)
    self.calls+=1; if self.fail then error("Simulated DataStore outage") end
    local proposed
    for _=1,self.replay do proposed=callback(copy(self.records[key])) end
    if proposed~=nil then self.records[key]=copy(proposed) end
    return copy(proposed)
end
local ds=M.service("DataStoreService"); ds.GetCalls=0
function ds:GetDataStore() self.GetCalls+=1; if self.FailGet then error("You must publish this place to the web to access DataStore") end; return M.Store end
return M
