--!strict
-- Все настройки игры «Улей: Нижний ярус» в одном месте.
-- Меняйте числа здесь — серверу и клиенту ничего больше править не нужно.

local Config = {
	GameName = "УЛЕЙ: НИЖНИЙ ЯРУС",
	Chapter = "ГЛАВА 4 — ГНЕЗДО",

	-- Лабиринт генерируется заново каждый раунд
	MazeWidth = 9, -- клеток по X
	MazeHeight = 9, -- клеток по Z
	CellSize = 26, -- ширина клетки в стадах
	WallHeight = 16,
	WallThickness = 2,
	ExtraLoops = 12, -- сколько дополнительных проходов прорубить (меньше тупиков)
	LampChance = 0.4, -- шанс лампы в клетке
	NestChance = 0.35, -- шанс «наростов улья» в клетке
	MazeOrigin = Vector3.new(0, 100, 0),
	LobbyPosition = Vector3.new(0, 100, -320),

	-- Раунд
	IntermissionTime = 20,
	RoundTime = 480,
	MinPlayers = 1,
	FusesRequired = 3,
	FusesSpawned = 4, -- на один больше, чем нужно — запас
	LockerCount = 14,
	BatteryCount = 10,

	Player = {
		LobbyWalkSpeed = 16,
		WalkSpeed = 12,
		SprintSpeed = 21,
		StaminaMax = 100,
		StaminaDrain = 24, -- в секунду при беге
		StaminaRegen = 13,
		StaminaRecoverAt = 30, -- после полного истощения бег вернётся с этого значения
		BatteryMax = 100,
		BatteryDrain = 1.5, -- в секунду при включённом фонаре
		BatteryPickup = 45,
	},

	Monster = {
		SpawnDelay = 12, -- секунд после старта раунда
		WanderSpeed = 9,
		InvestigateSpeed = 13,
		ChaseSpeed = 18.5,
		SpeedPerFuse = 1.1, -- ускорение за каждый вставленный предохранитель
		PowerOnBonus = 2.5, -- когда лифт запитан
		SightRange = 65,
		FlashlightSightBonus = 30, -- включённый фонарь виден издалека
		HearSprintRange = 48,
		CatchDistance = 4.5,
		LoseTargetTime = 4,
		HuntChance = 0.3, -- шанс, что при блуждании рой пойдёт к клетке игрока
		LockerRipChance = 0.5, -- шанс вытащить из шкафчика, если видел, как ты туда залез
		HoverHeight = 5,
	},

	-- «Связь» (tether): держитесь вместе
	Tether = {
		Range = 22, -- радиус связи с напарником
		StaminaRegenMultiplier = 1.6,
		BatteryDrainMultiplier = 0.65,
		CocoonTime = 40, -- сколько секунд у команды, чтобы освободить пойманного
		RescueHoldTime = 3,
	},

	-- Звуки по желанию: вставьте ID из Creator Store (Toolbox → Audio),
	-- например "rbxassetid://1234567". Пустая строка = звук не используется.
	Sounds = {
		Ambient = "",
		Buzz = "",
		Jumpscare = "",
		Heartbeat = "",
		Spotted = "",
		Pickup = "",
		PowerOn = "",
	},
}

return Config
