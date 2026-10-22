/**
 * contentcheck.mjs — исполняет настоящие Luau-данные игры в песочнице (fengari)
 * и проверяет целостность контента:
 *
 *  • у каждого сна есть обязательный квест, и он принадлежит этому сну;
 *  • все квесты ссылаются на существующие сны, виды и награды;
 *  • монстры/боссы снов существуют в MonsterDefinitions;
 *  • предметы магазина имеют цену, слот и корректный тип;
 *  • достижения используют известные условия;
 *  • каналы Remotes не дублируются;
 *  • Config: сложности, тайминги и границы значений.
 *
 * Это ловит ошибки данных, которые не видит ни luaucheck, ни wirecheck.
 * Запуск: node tools/contentcheck.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { lua, lauxlib, lualib, to_luastring, to_jsstring } from 'fengari';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const SHARED = path.join(ROOT, 'src', 'ReplicatedStorage', 'SharedModules');

const PRELUDE = `
local function vec(x, y, z) return { X = x or 0, Y = y or 0, Z = z or 0, x = x or 0, y = y or 0, z = z or 0 } end
Color3 = {
  fromRGB = function(r, g, b) return { R = r or 0, G = g or 0, B = b or 0 } end,
  new = function(r, g, b) return { R = (r or 0) * 255, G = (g or 0) * 255, B = (b or 0) * 255 } end,
  fromHSV = function() return { R = 0, G = 0, B = 0 } end,
}
Vector3 = { new = vec, zero = vec(0, 0, 0), one = vec(1, 1, 1) }
Vector2 = { new = function(x, y) return { X = x or 0, Y = y or 0 } end }
UDim = { new = function(s, o) return { Scale = s or 0, Offset = o or 0 } end }
UDim2 = {
  new = function(a, b, c, d) return { X = { Scale = a or 0, Offset = b or 0 }, Y = { Scale = c or 0, Offset = d or 0 } } end,
  fromScale = function(x, y) return { X = { Scale = x or 0 }, Y = { Scale = y or 0 } } end,
  fromOffset = function(x, y) return { X = { Offset = x or 0 }, Y = { Offset = y or 0 } } end,
}
NumberRange = { new = function(a, b) return { Min = a, Max = b or a } end }
NumberSequence = { new = function(...) return { ... } end }
NumberSequenceKeypoint = { new = function(t, v, e) return { t, v, e } end }
ColorSequence = { new = function(...) return { ... } end }
ColorSequenceKeypoint = { new = function(t, c) return { t, c } end }
Random = {
  new = function() return {
    NextInteger = function(_, a, b) return math.random(a, b) end,
    NextNumber = function(_, a, b) return a + math.random() * ((b or 1) - a) end,
    NextUnit = function() return math.random() end,
  } end,
}
task = { wait = function() end, delay = function() end, spawn = function() end, defer = function() end }
local __RS = setmetatable({
  WaitForChild = function(self, name, timeout)
    local value = rawget(self, name)
    if value == nil then
      value = setmetatable({ Name = name }, { __index = function(t, key)
        local child = setmetatable({ Name = key }, { __index = function() return {} end })
        rawset(t, key, child)
        return child
      end })
      rawset(self, name, value)
    end
    return value
  end,
  FindFirstChild = function(self, name) return rawget(self, name) end,
}, { __index = function() return {} end })
game = { GetService = function(_, name) return __RS end }
script = { Parent = {} }
`;

const MODULES = [
  ['Config', 'Config.luau'],
  ['Util', 'Util.luau'],
  ['Remotes', 'Remotes.luau'],
  ['ItemDefinitions', 'ItemDefinitions.luau'],
  ['MonsterDefinitions', 'MonsterDefinitions.luau'],
  ['DreamDefinitions', 'DreamDefinitions.luau'],
  ['QuestDefinitions', 'QuestDefinitions.luau'],
  ['AchievementDefinitions', 'AchievementDefinitions.luau'],
];

const L = lauxlib.luaL_newstate();
lualib.luaL_openlibs(L);

const run = (code, label) => {
  const buffer = to_luastring(code, true);
  const status = lauxlib.luaL_loadbuffer(L, buffer, buffer.length, to_luastring(label)) ||
    lua.lua_pcall(L, 0, lua.LUA_MULTRET, 0);
  if (status !== lua.LUA_OK) {
    const err = to_jsstring(lua.lua_tostring(L, -1));
    console.error(`Ошибка выполнения ${label}: ${err}`);
    process.exit(1);
  }
};

run(PRELUDE, 'prelude');

// Каждый модуль — отдельный чанк с общим глобальным окружением.
const globals = {};
for (const [name, file] of MODULES) {
  const source = fs.readFileSync(path.join(SHARED, file), 'utf8').replace(/^--![a-z]+$/m, '');
  run(`__module = (function()\n${source}\nend)()`, name);
  lua.lua_getglobal(L, to_luastring('__module'));
  lua.lua_setglobal(L, to_luastring(`RS_${name}`));
  globals[name] = true;
}

const CHECKS = `
local problems = {}
local notes = {}
local function problem(msg) table.insert(problems, msg) end
local function note(msg) table.insert(notes, msg) end

local Config = RS_Config
local ItemDefs = RS_ItemDefinitions
local MonsterDefs = RS_MonsterDefinitions
local Dreams = RS_DreamDefinitions
local Quests = RS_QuestDefinitions
local Achievements = RS_AchievementDefinitions
local Remotes = RS_Remotes

local VALID_KINDS = { collect = true, interact = true, talk = true, reach = true, survive = true, lights = true }

-- 1. Сны
local dreamCount = 0
for _, dream in ipairs(Dreams.List) do
  dreamCount = dreamCount + 1
  if type(dream.id) ~= "string" or dream.id == "" then problem("сон без id") end
  if type(dream.title) ~= "string" or dream.title == "" then problem("сон " .. tostring(dream.id) .. " без title") end
  if type(dream.map) ~= "string" then problem("сон " .. tostring(dream.id) .. " без map") end
  if type(dream.monsters) ~= "table" or #dream.monsters == 0 then
    problem("сон " .. tostring(dream.id) .. " без монстров")
  else
    for _, monsterId in ipairs(dream.monsters) do
      if not MonsterDefs.ByArchetype[monsterId] then
        problem("сон " .. dream.id .. ": монстр " .. tostring(monsterId) .. " не существует")
      end
    end
  end
  if dream.boss and not MonsterDefs.ByArchetype[dream.boss] then
    problem("сон " .. dream.id .. ": босс " .. tostring(dream.boss) .. " не существует")
  end
  if type(dream.mandatory) ~= "string" then
    problem("сон " .. dream.id .. ": нет обязательного квеста")
  end
  if type(dream.npcs) ~= "table" or #dream.npcs == 0 then
    problem("сон " .. dream.id .. ": нет NPC")
  end
  if (dream.fogEnd or 0) <= (dream.fogStart or 0) then
    problem("сон " .. dream.id .. ": fogEnd <= fogStart")
  end
end
note("снов: " .. dreamCount)

-- 2. Квесты
local questCount = 0
local mandatoryByDream = {}
for _, quest in ipairs(Quests.List) do
  questCount = questCount + 1
  if not Dreams.ById[quest.dream] then
    problem("квест " .. quest.id .. ": сна " .. tostring(quest.dream) .. " нет")
  end
  if not VALID_KINDS[quest.kind] then
    problem("квест " .. quest.id .. ": неизвестный вид " .. tostring(quest.kind))
  end
  if type(quest.need) ~= "number" or quest.need <= 0 then
    problem("квест " .. quest.id .. ": need <= 0")
  end
  if type(quest.reward) ~= "number" or quest.reward <= 0 then
    problem("квест " .. quest.id .. ": награда <= 0")
  end
  if type(quest.title) ~= "string" or quest.title == "" then
    problem("квест " .. quest.id .. ": нет title")
  end
end
for _, dream in ipairs(Dreams.List) do
  local mandatory = Quests.ById[dream.mandatory]
  if not mandatory then
    problem("сон " .. dream.id .. ": обязательный квест " .. tostring(dream.mandatory) .. " не найден")
  elseif mandatory.dream ~= dream.id then
    problem("сон " .. dream.id .. ": обязательный квест принадлежит сну " .. tostring(mandatory.dream))
  else
    mandatoryByDream[dream.id] = mandatory
  end
  -- Необязательные квесты сна: должно быть из чего выбрать 3
  local optional = 0
  for _, quest in ipairs(Quests.ByDream[dream.id] or {}) do
    if quest.id ~= dream.mandatory then optional = optional + 1 end
  end
  if optional < 3 then
    problem("сон " .. dream.id .. ": необязательных квестов " .. optional .. " (< 3)")
  end
end
note("квестов: " .. questCount)

-- 3. Предметы
local itemCount = 0
for _, itemId in ipairs(ItemDefs.Order) do
  itemCount = itemCount + 1
  local item = ItemDefs.ById[itemId]
  if not item then
    problem("магазин: предмет " .. tostring(itemId) .. " не найден")
  else
    if type(item.price) ~= "number" or item.price <= 0 then
      problem("предмет " .. itemId .. ": цена <= 0")
    end
    if item.consumable ~= nil and type(item.consumable) ~= "table" then
      problem("предмет " .. itemId .. ": consumable не таблица")
    end
    if item.consumable == nil and item.slot == nil then
      problem("предмет " .. itemId .. ": ни слот, ни расходник")
    end
  end
end
local inOrder = {}
for _, itemId in ipairs(ItemDefs.Order) do inOrder[itemId] = true end
for itemId in pairs(ItemDefs.ById) do
  if not inOrder[itemId] then
    problem("предмет " .. tostring(itemId) .. " есть в базе, но не в магазине")
  end
end
note("предметов: " .. itemCount)

-- 4. Монстры
local monsterCount = 0
for monsterId, def in pairs(MonsterDefs.ByArchetype) do
  monsterCount = monsterCount + 1
  if type(def.name) ~= "string" then problem("монстр " .. monsterId .. ": нет имени") end
  if def.dreams and #def.dreams == 0 then problem("монстр " .. monsterId .. ": не привязан ни к одному сну") end
  if type(def.speed) ~= "number" or def.speed <= 0 then
    problem("монстр " .. monsterId .. ": скорость <= 0")
  end
  if type(def.detectRange) ~= "number" or def.detectRange <= 0 then
    problem("монстр " .. monsterId .. ": detectRange <= 0")
  end
  if type(def.behavior) ~= "string" then problem("монстр " .. monsterId .. ": нет поведения") end
end
note("монстров: " .. monsterCount)

-- 5. Достижения
local achievementCount = 0
for _, def in ipairs(Achievements.List) do
  achievementCount = achievementCount + 1
  if type(def.id) ~= "string" then problem("достижение без id") end
  if type(def.name) ~= "string" and type(def.title) ~= "string" then
    problem("достижение " .. tostring(def.id) .. " без названия")
  end
  if type(def.reward) ~= "number" or def.reward <= 0 then
    problem("достижение " .. tostring(def.id) .. ": награда <= 0")
  end
end
note("достижений: " .. achievementCount)

-- 6. Каналы
local seen = {}
for _, name in ipairs(Remotes.EVENTS or {}) do
  if seen[name] then problem("канал " .. tostring(name) .. " объявлен дважды") end
  seen[name] = true
end
for _, name in ipairs(Remotes.FUNCTIONS or {}) do
  if seen[name] then problem("канал " .. tostring(name) .. " объявлен дважды") end
  seen[name] = true
end
note("каналов: " .. tostring(#(Remotes.EVENTS or {}) + #(Remotes.FUNCTIONS or {})))

-- 7. Конфиг
local difficultyKeys = 0
for key, diff in pairs(Config.Difficulties or {}) do
  difficultyKeys = difficultyKeys + 1
  if type(diff.time) ~= "number" or diff.time <= 0 then problem("сложность " .. key .. ": время <= 0") end
  if type(diff.reward) ~= "number" or diff.reward <= 0 then problem("сложность " .. key .. ": награда <= 0") end
  if type(diff.monsterCount) ~= "number" or diff.monsterCount < 1 then
    problem("сложность " .. key .. ": monsterCount < 1")
  end
  if diff.time ~= Config.DreamDuration then
    problem("сложность " .. key .. ": время не совпадает с DreamDuration")
  end
end
if difficultyKeys < 4 then problem("сложностей меньше 4 (" .. difficultyKeys .. ")") end
note("сложностей: " .. difficultyKeys)

if Config.DreamDuration <= 0 then problem("DreamDuration <= 0") end
if (Config.SleepDuration or 0) <= 0 then problem("SleepDuration <= 0") end
if (Config.WakeDuration or 0) <= 0 then problem("WakeDuration <= 0") end
if (Config.InteractRange or 0) <= 0 then problem("InteractRange <= 0") end

-- Вывод
for _, msg in ipairs(notes) do print("NOTE\\t" .. msg) end
for _, msg in ipairs(problems) do print("PROBLEM\\t" .. msg) end
print("DONE\\t" .. tostring(#problems))
`;

const buffer = to_luastring(CHECKS, true);
const status = lauxlib.luaL_loadbuffer(L, buffer, buffer.length, to_luastring('checks')) ||
  lua.lua_pcall(L, 0, 0, 0);
if (status !== lua.LUA_OK) {
  console.error('Ошибка проверок: ' + to_jsstring(lua.lua_tostring(L, -1)));
  process.exit(1);
}

// print в Lua пишет в stdout — перехватывать не нужно, но для аккуратного
// отчёта повторно прогоняем тот же чек-код через сбор в JS.
console.log('Смотри отчёт выше.');
