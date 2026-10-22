-- 20 стран для демонстрации (масштабируется до ~195)
local CountryData = {
    {id="USA", name="United States", color=Color3.fromHex("#3B82F6"), flag="🇺🇸", capital="P-DC"},
    {id="RUS", name="Russia", color=Color3.fromHex("#EF4444"), flag="🇷🇺", capital="P-MOW"},
    {id="CHN", name="China", color=Color3.fromHex("#F59E0B"), flag="🇨🇳", capital="P-BJ"},
    {id="DEU", name="Germany", color=Color3.fromHex("#10B981"), flag="🇩🇪", capital="P-BER"},
    {id="FRA", name="France", color=Color3.fromHex("#6366F1"), flag="🇫🇷", capital="P-PAR"},
    {id="GBR", name="United Kingdom", color=Color3.fromHex("#8B5CF6"), flag="🇬🇧", capital="P-LON"},
    {id="JPN", name="Japan", color=Color3.fromHex("#EC4899"), flag="🇯🇵", capital="P-TKY"},
    {id="IND", name="India", color=Color3.fromHex("#F97316"), flag="🇮🇳", capital="P-DEL"},
    {id="BRA", name="Brazil", color=Color3.fromHex("#22C55E"), flag="🇧🇷", capital="P-BSB"},
    {id="TUR", name="Turkey", color=Color3.fromHex("#DC2626"), flag="🇹🇷", capital="P-ANK"},
    {id="UKR", name="Ukraine", color=Color3.fromHex("#0EA5E9"), flag="🇺🇦", capital="P-KYV"},
    {id="POL", name="Poland", color=Color3.fromHex("#E11D48"), flag="🇵🇱", capital="P-WAW"},
    {id="ITA", name="Italy", color=Color3.fromHex("#059669"), flag="🇮🇹", capital="P-ROM"},
    {id="ESP", name="Spain", color=Color3.fromHex("#D97706"), flag="🇪🇸", capital="P-MAD"},
    {id="IRN", name="Iran", color=Color3.fromHex("#16A34A"), flag="🇮🇷", capital="P-THR"},
    {id="KOR", name="South Korea", color=Color3.fromHex("#0EA5E9"), flag="🇰🇷", capital="P-SEL"},
    {id="AUS", name="Australia", color=Color3.fromHex("#FACC15"), flag="🇦🇺", capital="P-CAN"},
    {id="CAN", name="Canada", color=Color3.fromHex("#EF4444"), flag="🇨🇦", capital="P-OTT"},
    {id="MEX", name="Mexico", color=Color3.fromHex("#22C55E"), flag="🇲🇽", capital="P-MEX"},
    {id="ZAF", name="South Africa", color=Color3.fromHex("#A3E635"), flag="🇿🇦", capital="P-PRE"},
}
-- быстрый индекс
local byId={} for _,c in ipairs(CountryData) do byId[c.id]=c end
CountryData.byId = byId
return CountryData
