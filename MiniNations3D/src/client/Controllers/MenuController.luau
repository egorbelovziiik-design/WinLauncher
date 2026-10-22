local MenuController = {}
local UI = {}
function MenuController.init(mainGui: ScreenGui)
    -- управляет переключением панелей главного меню
    MenuController.gui = mainGui
end
function MenuController.show(panel:string)
    for _,ch in ipairs(MenuController.gui:GetDescendants()) do
        if ch:GetAttribute("Panel") then ch.Visible = ch:GetAttribute("Panel")==panel end
    end
end
return MenuController
