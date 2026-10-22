local CameraController = {}
local UIS = game:GetService("UserInputService")
local RunService = game:GetService("RunService")

local cam = workspace.CurrentCamera
cam.CameraType = Enum.CameraType.Scriptable

local targetPos = Vector3.new(0, 120, -80)
local targetYaw = 0
local targetPitch = 45
local zoom = 140
local minZoom, maxZoom = 40, 320
local moveSpeed = 60

local keys = {W=false, A=false, S=false, D=false}
local edgeMove = true

function CameraController.init()
    cam.FieldOfView = 70
    UIS.InputBegan:Connect(function(inp, proc)
        if proc then return end
        if inp.KeyCode==Enum.KeyCode.W then keys.W=true end
        if inp.KeyCode==Enum.KeyCode.A then keys.A=true end
        if inp.KeyCode==Enum.KeyCode.S then keys.S=true end
        if inp.KeyCode==Enum.KeyCode.D then keys.D=true end
        if inp.KeyCode==Enum.KeyCode.F then CameraController.focusCapital() end
    end)
    UIS.InputEnded:Connect(function(inp)
        if inp.KeyCode==Enum.KeyCode.W then keys.W=false end
        if inp.KeyCode==Enum.KeyCode.A then keys.A=false end
        if inp.KeyCode==Enum.KeyCode.S then keys.S=false end
        if inp.KeyCode==Enum.KeyCode.D then keys.D=false end
    end)
    UIS.InputChanged:Connect(function(inp)
        if inp.UserInputType==Enum.UserInputType.MouseWheel then
            zoom = math.clamp(zoom - inp.Position.Z*12, minZoom, maxZoom)
        end
        if UIS:IsMouseButtonPressed(Enum.UserInputType.MouseButton2) and inp.UserInputType==Enum.UserInputType.MouseMovement then
            targetYaw -= inp.Delta.X*0.3
            targetPitch = math.clamp(targetPitch - inp.Delta.Y*0.3, 15, 85)
        end
        if inp.UserInputType==Enum.UserInputType.Touch then
            -- pinch handled via separate
        end
    end)

    -- Middle mouse pan
    local panning=false
    local lastPos=nil
    UIS.InputBegan:Connect(function(inp)
        if inp.UserInputType==Enum.UserInputType.MouseButton3 then panning=true; lastPos=inp.Position end
    end)
    UIS.InputEnded:Connect(function(inp) if inp.UserInputType==Enum.UserInputType.MouseButton3 then panning=false end end)
    UIS.InputChanged:Connect(function(inp)
        if panning and inp.UserInputType==Enum.UserInputType.MouseMovement then
            local delta = inp.Position - (lastPos or inp.Position)
            lastPos=inp.Position
            local rad = math.rad(targetYaw)
            targetPos += Vector3.new(-delta.X*0.25*math.cos(rad) - delta.Y*0.25*math.sin(rad),0, -delta.X*0.25*math.sin(rad) + delta.Y*0.25*math.cos(rad))
        end
    end)

    RunService.RenderStepped:Connect(function(dt)
        local mv = Vector3.zero
        if keys.W then mv+=Vector3.new(0,0,-1) end
        if keys.S then mv+=Vector3.new(0,0,1) end
        if keys.A then mv+=Vector3.new(-1,0,0) end
        if keys.D then mv+=Vector3.new(1,0,0) end
        -- edge scroll
        if edgeMove and not UIS:GetFocusedTextBox() then
            local m = UIS:GetMouseLocation()
            local vp = cam.ViewportSize
            if m.X < 18 then mv+=Vector3.new(-1,0,0) end
            if m.X > vp.X-18 then mv+=Vector3.new(1,0,0) end
            if m.Y < 18 then mv+=Vector3.new(0,0,-1) end
            if m.Y > vp.Y-18 then mv+=Vector3.new(0,0,1) end
        end
        if mv.Magnitude>0 then
            mv=mv.Unit * moveSpeed * dt * (zoom/120)
            local rad=math.rad(targetYaw)
            local fwd = Vector3.new(math.sin(rad),0, math.cos(rad))
            local right = Vector3.new(math.cos(rad),0, -math.sin(rad))
            targetPos += right*mv.X + fwd*mv.Z
        end
        targetPos = Vector3.new(math.clamp(targetPos.X,-1200,1200), targetPos.Y, math.clamp(targetPos.Z,-700,700))
        -- плавность
        local desired = targetPos + Vector3.new(0, math.sin(math.rad(targetPitch))*zoom, math.cos(math.rad(targetPitch))*zoom)
        cam.CFrame = cam.CFrame:Lerp(CFrame.lookAt(desired, targetPos), math.clamp(dt*7,0,1))
        cam.CFrame = CFrame.new(cam.CFrame.Position, targetPos) * CFrame.Angles(0,0,0)
    end)
end

function CameraController.focusCapital()
    -- найти столицу выбранной страны (через MapController)
    local MapCtrl = require(script.Parent.MapController)
    local cap = MapCtrl.getSelectedCountryCapitalPos()
    if cap then targetPos = cap end
end
function CameraController.jumpTo(pos:Vector3) targetPos = pos end
return CameraController
