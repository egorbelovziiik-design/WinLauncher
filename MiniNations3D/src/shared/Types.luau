export type CountryId = string
export type ProvinceId = string

export type Resources = {
    Oil:number, Gas:number, Coal:number, Iron:number, Copper:number, Gold:number,
    Uranium:number, Lithium:number, Wood:number, Stone:number, Food:number, Water:number
}

export type Economy = {
    gdp:number, budget:number, income:number, expenses:number,
    taxes:number, inflation:number, debt:number, unemployment:number,
    tradeBalance:number, livingStandard:number
}

export type Country = {
    id: CountryId,
    name:string,
    color: Color3,
    flag: string,
    capital: ProvinceId,
    provinces: {ProvinceId},
    economy: Economy,
    resources: Resources,
    tech: {[string]: number},
    relations: {[CountryId]: number}, -- -100..100
    isAI: boolean,
    behavior: string,
}

export type Province = {
    id: ProvinceId,
    name: string,
    owner: CountryId,
    population: number,
    economy: number,
    infrastructure: number,
    stability: number,
    buildings: {string},
    army: {string},
    pos: Vector3,
}

export type War = {
    id: string,
    attackers: {CountryId},
    defenders: {CountryId},
    startTime: number,
    battles: number,
}

export type Alliance = {
    id: string,
    name: string,
    color: Color3,
    leader: CountryId,
    members: {CountryId},
}

export type PlayerProfile = {
    coins: number,
    xp: number,
    level: number,
    wins: number,
    losses: number,
    playTime: number,
    ownedSkins: {string},
    achievements: {string},
    tutorialDone: boolean,
    settings: {[string]: any},
}

return {}
