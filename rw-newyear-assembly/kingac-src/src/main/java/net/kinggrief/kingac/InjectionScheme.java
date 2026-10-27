package net.kinggrief.kingac;

public enum InjectionScheme {

    BACK_360(
            "back",
            "Спина-360° [Rage/KillAura]",
            "SHIELD",
            6,
            "Привязывает невидимого микро-бота строго за затылком (1.75м)",
            "и вращает его за спиной при повороте камеры игрока."
    ),
    THROUGH_WALL(
            "wall",
            "Сквозь-Стену [WallHit/Трапка]",
            "IRON_BARS",
            9,
            "Ставит приманку на 0.95м ЗА сплошной стеной / стеклом трапки.",
            "Честный клиент не видит цель за блоком, а WallAura пробивает!"
    ),
    ORBIT_VORTEX(
            "orbit",
            "Орбита-Вихрь [AimLock/Track]",
            "ENDER_EYE",
            6,
            "Закручивает фантома по 3D-спирали вокруг игрока (28°/тик),",
            "ловя магнит камеры (AimBot) и удары по летящей приманке."
    ),
    ZENITH_CROWN(
            "zenith",
            "Верхний-Купол +85° [SilentAura]",
            "BEACON",
            7,
            "Подвешивает микро-цель над макушкой (+2.30м вне экрана PvP).",
            "Ловит SilentAura и MultiPoints без единого шанса на миссклик!"
    ),
    SWARM_QUAD(
            "swarm",
            "Мульти-Рой x4 [Multi/SwitchAura]",
            "NETHER_STAR",
            8,
            "Спавнит сразу 4 синхронных фантома в слепых зонах игрока.",
            "Если за серию задеты 2+ фантома — 100% детект MultiAura!"
    ),
    HITBOX_OFFSET(
            "hitbox",
            "Байт-Хитбокс [HitBoxes/AirHit]",
            "SPECTRAL_ARROW",
            6,
            "Размещает приманку в пустом воздухе в 0.95м сбоку от прицела.",
            "Ловит выкрученные HitBoxes (+0.5..+2.0) и SilentAim!"
    ),
    BACKTRACK_PULSE(
            "backtrack",
            "Призрак-Отступ [Backtrack/Reach]",
            "FEATHER",
            6,
            "Фантом появляется сзади-сбоку (2.4м) и резко отпрыгивает на 4.2м,",
            "детектя Reach и Backtrack-задержку пакетов атаки!"
    ),
    NADIR_FLOOR(
            "nadir",
            "Надир-Пол [Голова-Вниз Аура]",
            "ANVIL",
            7,
            "Вешает фантом ПОД НОГАМИ игрока (-1.15м) — зона, куда честный",
            "игрок не бьёт никогда. Ловит ауры с наводкой «голова вниз»!"
    ),
    TWIN_WALL(
            "twinwall",
            "Двойная-Стена [WallHit x2]",
            "GLASS",
            9,
            "Ставит приманки ЗА ДВУМЯ стенами сразу (фронт + фланг 90°).",
            "WallAura через стекло трапки попадёт хотя бы по одной!"
    ),
    SWAP_DECOY(
            "swap",
            "Фантом-Обман [Switch/Trigger]",
            "ENDER_PEARL",
            7,
            "Фантом стоит СПЕРЕДИ (2.3м), а через 5 тиков телепортируется",
            "ЗА СПИНУ — ловит TriggerBot и ауры с фиксацией цели!"
    ),
    LOW_SWEEP(
            "sweep",
            "Нижний-Луч [Low-Aim Аура]",
            "IRON_BOOTS",
            6,
            "Фантом на уровне СТУПНЕЙ в 2.9м медленно ползёт по дуге.",
            "Ловит ауры, бьющие по ногам сквозь блоки и полублоки!"
    ),
    FULL_CASCADE(
            "cascade",
            "Каскад «Полный Нейро-Скан»",
            "END_CRYSTAL",
            8,
            "4-секундный комбо-прогон всех схем подряд (360° -> Купол ->",
            "Хитбокс -> Вихрь -> Рой x4 + Стена) с отчётом в чат!"
    );

    public final String id;
    public final String displayTitle;
    public final String iconMaterialName;
    public final int baseVl;
    public final String descLine1;
    public final String descLine2;

    InjectionScheme(String id, String displayTitle, String iconMaterialName, int baseVl, String descLine1, String descLine2) {
        this.id = id;
        this.displayTitle = displayTitle;
        this.iconMaterialName = iconMaterialName;
        this.baseVl = baseVl;
        this.descLine1 = descLine1;
        this.descLine2 = descLine2;
    }

    public static InjectionScheme fromId(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String s = raw.trim().toLowerCase();
        for (InjectionScheme sc : values()) {
            if (sc.id.equals(s) || sc.name().toLowerCase().equals(s)) {
                return sc;
            }
        }
        if ("360".equals(s) || "behind".equals(s) || "спина".equals(s)) return BACK_360;
        if ("wallprobe".equals(s) || "стена".equals(s)) return THROUGH_WALL;
        if ("vortex".equals(s) || "вихрь".equals(s) || "орбита".equals(s)) return ORBIT_VORTEX;
        if ("top".equals(s) || "crown".equals(s) || "купол".equals(s)) return ZENITH_CROWN;
        if ("multi".equals(s) || "quad".equals(s) || "рой".equals(s)) return SWARM_QUAD;
        if ("box".equals(s) || "air".equals(s) || "хитбокс".equals(s)) return HITBOX_OFFSET;
        if ("reach".equals(s) || "bt".equals(s)) return BACKTRACK_PULSE;
        if ("floor".equals(s) || "пол".equals(s) || "низ".equals(s)) return NADIR_FLOOR;
        if ("twin".equals(s) || "2wall".equals(s) || "двойная".equals(s)) return TWIN_WALL;
        if ("decoy".equals(s) || "обман".equals(s)) return SWAP_DECOY;
        if ("low".equals(s) || "legs".equals(s) || "ноги".equals(s)) return LOW_SWEEP;
        if ("all".equals(s) || "full".equals(s) || "scan".equals(s) || "каскад".equals(s)) return FULL_CASCADE;
        return null;
    }
}
