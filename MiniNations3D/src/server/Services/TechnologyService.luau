local TechnologyService = {}
local trees = {
    Economy={"Market","TradeHub","DigitalTax"},
    Industry={"Automation","Robotics","AdvancedMetallurgy"},
    Army={"ModernInfantry","CompositeArmor","Stealth"},
    Energy={"SmartGrid","FusionProto","Hydrogen"},
    Transport={"HighSpeedRail","Maglev","DronesLogistics"},
    Aviation={"JetEngine","Supersonic","UAV"},
    Navy={"Carrier","NuclearSub","Aegis"},
    Medicine={"Vaccine","Biotech","Longevity"},
    Space={"Satellite","MoonBase","MarsMission"},
}
local progress: {[string]: {[string]: number}} = {} -- country -> tech -> level

function TechnologyService.research(countryId:string, branch:string, tech:string)
    progress[countryId]=progress[countryId] or {}
    local key = branch..":"..tech
    progress[countryId][key]=(progress[countryId][key] or 0)+1
    if progress[countryId][key]==1 then
        -- бонус
        local rem=game.ReplicatedStorage.Remotes:FindFirstChild("TechUnlocked")
        if rem then rem:FireAllClients(countryId, branch, tech) end
    end
    return progress[countryId][key]
end
function TechnologyService.get(countryId:string) return progress[countryId] or {} end
function TechnologyService.trees() return trees end
return TechnologyService
