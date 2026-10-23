--!strict
-- Permanent campaign specialists sold only by the second-city recruiter. Prices use
-- Survival Cash; no Coins/Robux affect combat or production.
return {
    builder = {
        order = 1, name = "Аркадий", role = "Строитель", price = 1250, style = "builder",
        color = {91, 105, 108}, skin = {183, 138, 102}, health = 150, speed = 8,
        description = "Живёт на базе и сокращает время каждого нового строительства на 35%.",
    },
    medic = {
        order = 2, name = "Светлана", role = "Полевой медик", price = 1000, style = "resident_medic",
        color = {137, 157, 143}, skin = {203, 161, 128}, health = 130, speed = 10,
        description = "Следует за вами или дежурит на базе; раз в 6 секунд лечит ближайшего владельца на 4 HP.",
    },
    guard = {
        order = 3, name = "Тимур", role = "Военный охранник", price = 1500, style = "soldier_guard",
        color = {55, 75, 57}, skin = {169, 128, 96}, health = 220, speed = 10,
        description = "Защищает базу или следует за вами. Стреляет по заражённым, расходуя карабинные патроны со склада.",
    },
    scout = {
        order = 4, name = "Алина", role = "Разведчик", price = 900, style = "resident_scout",
        color = {67, 82, 76}, skin = {190, 146, 112}, health = 125, speed = 12,
        description = "Следует или патрулирует базу и передаёт в HUD направление на ближайшую угрозу в радиусе 150 studs.",
    },
    gatherer = {
        order = 5, name = "Егор", role = "Добыватель ресурсов", price = 1100, style = "worker_lev",
        color = {91, 103, 86}, skin = {157, 117, 88}, health = 145, speed = 9,
        description = "Его можно отправить в сохраняемую 120-секундную экспедицию за металлом, шахтными ресурсами или древесиной.",
    },
}
