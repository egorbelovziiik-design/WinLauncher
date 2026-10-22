local MapConfig = {
    Biomes = {
        {id="Plains", color=Color3.fromHex("#86EFAC"), height=1},
        {id="Forest", color=Color3.fromHex("#15803D"), height=2},
        {id="Mountain", color=Color3.fromHex("#78716C"), height=8},
        {id="Desert", color=Color3.fromHex("#FDE68A"), height=1},
        {id="Ocean", color=Color3.fromHex("#1E40AF"), height=-6},
        {id="Snow", color=Color3.fromHex("#E0F2FE"), height=3},
    },
    MapModes = {"Political","Economic","Military","Resource","Energy","Diplomatic","Infrastructure","Population","Technology"},
}
return MapConfig
