local GameService = {}
local ScenarioService = require(script.Parent.ScenarioService)
local AIService = require(script.Parent.AIService)
local SaveService = require(script.Parent.SaveService)

local matches: {[string]: any} = {}

function GameService.createMatch(host:Player, cfg:any)
    local id = "M_"..tostring(math.random(100000,999999))
    matches[id]={id=id, host=host, config=cfg, players={[host.UserId]=host}, ready={}, started=false}
    return matches[id]
end
function GameService.joinMatch(player:Player, matchId:string)
    local m=matches[matchId]; if not m then return false, "No match" end
    if m.started then return false, "Started" end
    m.players[player.UserId]=player; return true, m
end
function GameService.setReady(matchId:string, player:Player, ready:boolean)
    local m=matches[matchId]; if m then m.ready[player.UserId]=ready end
end
function GameService.start(matchId:string)
    local m=matches[matchId]; if not m or m.started then return false end
    if next(m.ready)==nil then return false end
    m.started=true
    -- инициализация сценария года
    ScenarioService.applyYear(m.config.year or 2026)
    AIService.configure({difficulty=m.config.difficulty or "Normal", aggression=m.config.aggression or 0.6})
    AIService.start()
    for _,pl in pairs(m.players) do
        local rem=game.ReplicatedStorage.Remotes:FindFirstChild("MatchStarted")
        if rem then rem:FireClient(pl, m) end
    end
    return true
end
function GameService.list() return matches end
return GameService
