-- Главный серверный скрипт «Улей: Нижний ярус».
-- Цикл: лобби → раунд в новом лабиринте → итоги → лобби.

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")
local Lighting = game:GetService("Lighting")
local StarterPlayer = game:GetService("StarterPlayer")

local Config = require(ReplicatedStorage:WaitForChild("Shared"):WaitForChild("Config"))
local MapBuilder = require(script:WaitForChild("MapBuilder"))
local Monster = require(script:WaitForChild("Monster"))

---------------------------------------------------------------------
-- Подготовка мира
---------------------------------------------------------------------
local remotes = Instance.new("Folder")
remotes.Name = "HiveRemotes"
local function remote(name)
	local r = Instance.new("RemoteEvent")
	r.Name = name
	r.Parent = remotes
	return r
end
local NotifyRemote = remote("Notify") -- S→C (text, kind)
local JumpscareRemote = remote("Jumpscare") -- S→C
local SpottedRemote = remote("Spotted") -- S→C
local RoundRemote = remote("Round") -- S→C ("Start"|"End", info)
local BatteryRemote = remote("Battery") -- S→C (amount)
local ClientStateRemote = remote("ClientState") -- C→S (flashlightOn, sprinting)
remotes.Parent = ReplicatedStorage

ReplicatedStorage:SetAttribute("Phase", "Lobby")
ReplicatedStorage:SetAttribute("TimeLeft", 0)
ReplicatedStorage:SetAttribute("FusesInserted", 0)
ReplicatedStorage:SetAttribute("FusesRequired", Config.FusesRequired)
ReplicatedStorage:SetAttribute("ExitOpen", false)

-- Тьма
Lighting.Ambient = Color3.fromRGB(0, 0, 0)
Lighting.OutdoorAmbient = Color3.fromRGB(6, 5, 8)
Lighting.Brightness = 0
Lighting.ClockTime = 0
Lighting.GlobalShadows = true
Lighting.FogColor = Color3.fromRGB(0, 0, 0)
Lighting.FogStart = 0
Lighting.FogEnd = 95
Lighting.EnvironmentDiffuseScale = 0
Lighting.EnvironmentSpecularScale = 0
pcall(function()
	Lighting.Technology = Enum.Technology.Future
end)
for _, child in ipairs(Lighting:GetChildren()) do
	if child:IsA("Sky") or child:IsA("Atmosphere") or child:IsA("Clouds") then
		child:Destroy()
	end
end
local baseCC = Instance.new("ColorCorrectionEffect")
baseCC.Name = "HiveGrade"
baseCC.Saturation = -0.35
baseCC.Contrast = 0.15
baseCC.TintColor = Color3.fromRGB(255, 240, 215)
baseCC.Parent = Lighting
local bloom = Instance.new("BloomEffect")
bloom.Intensity = 0.6
bloom.Size = 30
bloom.Threshold = 0.9
bloom.Parent = Lighting

StarterPlayer.EnableMouseLockOption = false
StarterPlayer.CharacterWalkSpeed = Config.Player.LobbyWalkSpeed
Players.RespawnTime = 4

-- Чужие точки появления (например, из шаблона Baseplate) мешают лобби
for _, obj in ipairs(workspace:GetDescendants()) do
	if obj:IsA("SpawnLocation") then
		obj:Destroy()
	end
end
MapBuilder.BuildLobby(Config)

local ambient
if Config.Sounds.Ambient ~= "" then
	ambient = Instance.new("Sound")
	ambient.Name = "HiveAmbient"
	ambient.SoundId = Config.Sounds.Ambient
	ambient.Looped = true
	ambient.Volume = 0.4
	ambient.Parent = workspace
	ambient:Play()
end

local function playSoundAt(parent, id, volume)
	if id == "" then
		return
	end
	local s = Instance.new("Sound")
	s.SoundId = id
	s.Volume = volume or 1
	s.RollOffMaxDistance = 80
	s.Parent = parent
	s:Play()
	s.Ended:Connect(function()
		s:Destroy()
	end)
	task.delay(10, function()
		if s.Parent then
			s:Destroy()
		end
	end)
end

---------------------------------------------------------------------
-- Состояние раунда
---------------------------------------------------------------------
local rng = Random.new()
local map = nil
local monster = nil
local roundId = 0
local fusesInserted = 0
local exitOpen = false
local cocoons = {} -- [player] = {Model, Prompt, EndsAt}
local lockerOf = {} -- [player] = locker

local function notify(text, kind, target)
	if target then
		NotifyRemote:FireClient(target, text, kind or "info")
	else
		NotifyRemote:FireAllClients(text, kind or "info")
	end
end

local function getHumanoid(plr)
	local char = plr.Character
	return char and char:FindFirstChildOfClass("Humanoid")
end

local function getRoot(plr)
	local char = plr.Character
	return char and char:FindFirstChild("HumanoidRootPart")
end

local function isAlive(plr)
	local hum = getHumanoid(plr)
	return hum ~= nil and hum.Health > 0
end

local function inRound(plr)
	return plr:GetAttribute("InRound") == true and isAlive(plr)
end

-- игроки, которых рой может преследовать
local function monsterTargets()
	local list = {}
	for _, plr in ipairs(Players:GetPlayers()) do
		if inRound(plr) and not plr:GetAttribute("Hidden") and not plr:GetAttribute("Cocooned") then
			table.insert(list, plr)
		end
	end
	return list
end

local function freeParticipants(except)
	local n = 0
	for _, plr in ipairs(Players:GetPlayers()) do
		if plr ~= except and inRound(plr) and not plr:GetAttribute("Cocooned") then
			n += 1
		end
	end
	return n
end

local function resetPlayerAttributes(plr)
	plr:SetAttribute("InRound", false)
	plr:SetAttribute("Fuses", 0)
	plr:SetAttribute("Hidden", false)
	plr:SetAttribute("Cocooned", false)
	plr:SetAttribute("CocoonEndsAt", 0)
	plr:SetAttribute("Escaped", false)
	plr:SetAttribute("FlashlightOn", false)
	plr:SetAttribute("Sprinting", false)
end

local function setFlashlight(plr, on)
	plr:SetAttribute("FlashlightOn", on)
	local char = plr.Character
	local head = char and char:FindFirstChild("Head")
	local light = head and head:FindFirstChild("HiveFlashlight")
	if light then
		light.Enabled = on
	end
end

---------------------------------------------------------------------
-- Предметы
---------------------------------------------------------------------
local spawnFuse -- forward

local function onFusePicked(plr, part)
	if not inRound(plr) or plr:GetAttribute("Cocooned") or not part.Parent then
		return
	end
	part:Destroy()
	plr:SetAttribute("Fuses", (plr:GetAttribute("Fuses") or 0) + 1)
	notify("Предохранитель у тебя. Неси его к генератору у входа.", "good", plr)
	playSoundAt(getRoot(plr), Config.Sounds.Pickup, 0.8)
end

spawnFuse = function(position)
	if not map then
		return
	end
	local part = MapBuilder.NewPart({
		Name = "Fuse",
		Size = Vector3.new(0.9, 1.8, 0.9),
		CFrame = CFrame.new(position) * CFrame.Angles(0, rng:NextNumber(0, math.pi), 0.3),
		Color = Color3.fromRGB(255, 170, 40),
		Material = Enum.Material.Neon,
		CanCollide = false,
	}, map.Interactables)
	local light = Instance.new("PointLight")
	light.Range = 9
	light.Brightness = 1.2
	light.Color = Color3.fromRGB(255, 170, 40)
	light.Parent = part
	local prompt = MapBuilder.NewPrompt(part, "Взять", "Предохранитель", 0.4, 8)
	prompt.Triggered:Connect(function(plr)
		onFusePicked(plr, part)
	end)
	return part
end

local function spawnBattery(position)
	local part = MapBuilder.NewPart({
		Name = "Battery",
		Shape = Enum.PartType.Cylinder,
		Size = Vector3.new(1.4, 0.6, 0.6),
		CFrame = CFrame.new(position) * CFrame.Angles(0, rng:NextNumber(0, math.pi), 0),
		Color = Color3.fromRGB(90, 200, 255),
		Material = Enum.Material.Neon,
		CanCollide = false,
	}, map.Interactables)
	local light = Instance.new("PointLight")
	light.Range = 6
	light.Brightness = 0.8
	light.Color = Color3.fromRGB(90, 200, 255)
	light.Parent = part
	local prompt = MapBuilder.NewPrompt(part, "Взять", "Батарейка", 0, 8)
	prompt.Triggered:Connect(function(plr)
		if not inRound(plr) or plr:GetAttribute("Cocooned") or not part.Parent then
			return
		end
		part:Destroy()
		BatteryRemote:FireClient(plr, Config.Player.BatteryPickup)
		playSoundAt(getRoot(plr), Config.Sounds.Pickup, 0.6)
	end)
end

local function dropFuses(plr, position)
	local count = plr:GetAttribute("Fuses") or 0
	plr:SetAttribute("Fuses", 0)
	if count <= 0 or not map then
		return
	end
	local cx, cz = map:WorldToCell(position)
	local center = map:CellToWorld(cx, cz, 1.5)
	for i = 1, count do
		spawnFuse(center + Vector3.new((i - 1) * 2 - (count - 1), 0, 0))
	end
	notify(plr.DisplayName .. " выронил(а) предохранитель. Он остался там, где всё случилось.", "danger")
end

---------------------------------------------------------------------
-- Шкафчики
---------------------------------------------------------------------
local function leaveLocker(plr)
	local locker = lockerOf[plr]
	if not locker then
		return
	end
	lockerOf[plr] = nil
	locker.Occupant = nil
	locker.Prompt.ActionText = "Спрятаться"
	locker.Prompt.ObjectText = "Шкафчик"
	plr:SetAttribute("Hidden", false)
	local root = getRoot(plr)
	if root then
		root.Anchored = false
		if isAlive(plr) then
			root.CFrame = locker.ExitPoint.WorldCFrame
		end
	end
end

local catchPlayer -- forward

local function enterLocker(plr, locker)
	if locker.Occupant or lockerOf[plr] then
		return
	end
	local root = getRoot(plr)
	if not root then
		return
	end
	locker.Occupant = plr
	lockerOf[plr] = locker
	locker.Prompt.ActionText = "Выйти"
	locker.Prompt.ObjectText = "Занято"
	root.Anchored = true
	root.CFrame = locker.HidePoint.WorldCFrame
	plr:SetAttribute("Sprinting", false)

	-- рой видел, как ты прятался?
	if monster and monster.Target == plr and monster.State == "Chase" then
		local dist = (monster:GetPosition() - root.Position).Magnitude
		if dist < 16 and rng:NextNumber() < Config.Monster.LockerRipChance then
			local myRound = roundId
			task.delay(1.4, function()
				if roundId == myRound and lockerOf[plr] == locker and inRound(plr) then
					leaveLocker(plr)
					catchPlayer(plr)
				end
			end)
			return
		end
	end
	plr:SetAttribute("Hidden", true)
end

local function hookLocker(locker)
	locker.Prompt.Triggered:Connect(function(plr)
		if not inRound(plr) or plr:GetAttribute("Cocooned") then
			return
		end
		if locker.Occupant == plr then
			leaveLocker(plr)
		elseif locker.Occupant == nil then
			enterLocker(plr, locker)
		end
	end)
end

---------------------------------------------------------------------
-- Кокон и спасение (механика «связи»)
---------------------------------------------------------------------
local function clearCocoon(plr)
	local c = cocoons[plr]
	if c then
		cocoons[plr] = nil
		if c.Model then
			c.Model:Destroy()
		end
	end
	plr:SetAttribute("Cocooned", false)
	plr:SetAttribute("CocoonEndsAt", 0)
end

local function killPlayer(plr)
	local hum = getHumanoid(plr)
	if hum then
		hum.Health = 0
	end
end

local function rescue(plr, rescuer)
	local c = cocoons[plr]
	if not c then
		return
	end
	clearCocoon(plr)
	local root = getRoot(plr)
	if root then
		root.Anchored = false
	end
	notify(rescuer.DisplayName .. " вырвал(а) " .. plr.DisplayName .. " из кокона!", "good")
end

local function cocoonPlayer(plr)
	local root = getRoot(plr)
	if not root or not map then
		killPlayer(plr)
		return
	end
	-- рой утаскивает жертву в случайную дальнюю клетку
	local x, z = map:RandomCell(rng)
	local pos = map:CellToWorld(x, z, 0)
	local model = Instance.new("Model")
	model.Name = "Cocoon"
	local shell = MapBuilder.NewPart({
		Name = "Shell",
		Shape = Enum.PartType.Ball,
		Size = Vector3.new(5, 7.5, 5),
		Position = pos + Vector3.new(0, 3.2, 0),
		Color = Color3.fromRGB(110, 85, 40),
		Material = Enum.Material.Sand,
		Transparency = 0.35,
		CanCollide = false,
	}, model)
	local glow = Instance.new("PointLight")
	glow.Color = Color3.fromRGB(255, 120, 40)
	glow.Range = 14
	glow.Brightness = 1.5
	glow.Parent = shell
	local prompt = MapBuilder.NewPrompt(shell, "Освободить", plr.DisplayName, Config.Tether.RescueHoldTime, 10)
	model.Parent = map.Interactables

	local endsAt = workspace:GetServerTimeNow() + Config.Tether.CocoonTime
	cocoons[plr] = { Model = model, Prompt = prompt, EndsAt = endsAt }
	plr:SetAttribute("Cocooned", true)
	plr:SetAttribute("CocoonEndsAt", endsAt)
	plr:SetAttribute("Sprinting", false)
	setFlashlight(plr, false)

	root.Anchored = true
	root.CFrame = CFrame.new(pos + Vector3.new(0, 3, 0))

	prompt.Triggered:Connect(function(rescuer)
		if rescuer ~= plr and inRound(rescuer) and not rescuer:GetAttribute("Cocooned") then
			rescue(plr, rescuer)
		end
	end)

	notify(
		plr.DisplayName .. " схвачен(а) роем! Найдите светящийся кокон за " .. Config.Tether.CocoonTime .. " сек.",
		"danger"
	)
end

catchPlayer = function(plr)
	if not inRound(plr) or plr:GetAttribute("Cocooned") then
		return
	end
	JumpscareRemote:FireClient(plr)
	playSoundAt(getRoot(plr), Config.Sounds.Jumpscare, 1)
	leaveLocker(plr)
	local root = getRoot(plr)
	if root then
		dropFuses(plr, root.Position)
	end
	if freeParticipants(plr) > 0 then
		task.delay(1.1, function()
			if inRound(plr) then
				cocoonPlayer(plr)
			end
		end)
	else
		task.delay(1.1, function()
			killPlayer(plr)
		end)
	end
end

---------------------------------------------------------------------
-- Генератор и лифт
---------------------------------------------------------------------
local function openExit()
	exitOpen = true
	ReplicatedStorage:SetAttribute("ExitOpen", true)
	local lift = map.Lift
	lift.Barrier.CanCollide = false
	lift.Barrier.Transparency = 1
	lift.Beacon.Color = MapBuilder.Colors.Safe
	lift.Light.Color = MapBuilder.Colors.Safe
	lift.Pad.Color = Color3.fromRGB(40, 90, 50)
	lift.Label.Text = "ЛИФТ: ВЫХОД"
	lift.Label.TextColor3 = MapBuilder.Colors.Safe
	-- аварийный свет
	for _, lamp in ipairs(map.Lamps) do
		lamp.Color = MapBuilder.Colors.Danger
		local l = lamp:FindFirstChildOfClass("PointLight")
		if l then
			l.Color = MapBuilder.Colors.Danger
		end
	end
	if monster then
		monster:SetPowerOn(true)
	end
	playSoundAt(map.Generator.Model.PrimaryPart, Config.Sounds.PowerOn, 1)
	notify("ЛИФТ ЗАПИТАН! Улей проснулся — бегите к зелёному маяку!", "danger")
end

local function hookGenerator()
	map.Generator.Prompt.Triggered:Connect(function(plr)
		if not inRound(plr) or exitOpen then
			return
		end
		local carried = plr:GetAttribute("Fuses") or 0
		if carried <= 0 then
			notify(
				string.format("Нужно ещё %d предохр. Ищи их в тупиках улья.", Config.FusesRequired - fusesInserted),
				"info",
				plr
			)
			return
		end
		local use = math.min(carried, Config.FusesRequired - fusesInserted)
		plr:SetAttribute("Fuses", carried - use)
		for _ = 1, use do
			fusesInserted += 1
			local ind = map.Generator.Indicators[fusesInserted]
			if ind then
				ind.Color = MapBuilder.Colors.Safe
				local l = ind:FindFirstChildOfClass("PointLight")
				if l then
					l.Color = MapBuilder.Colors.Safe
				end
			end
		end
		ReplicatedStorage:SetAttribute("FusesInserted", fusesInserted)
		if monster then
			monster:SetRage(fusesInserted)
		end
		if fusesInserted >= Config.FusesRequired then
			openExit()
		else
			notify(
				string.format(
					"Генератор: %d/%d. Где-то в стенах что-то зашевелилось быстрее…",
					fusesInserted,
					Config.FusesRequired
				),
				"info"
			)
		end
	end)
end

local function hookExit()
	map.Lift.Zone.Touched:Connect(function(hit)
		if not exitOpen then
			return
		end
		local char = hit:FindFirstAncestorOfClass("Model")
		local plr = char and Players:GetPlayerFromCharacter(char)
		if not plr or not inRound(plr) or plr:GetAttribute("Escaped") or plr:GetAttribute("Cocooned") then
			return
		end
		plr:SetAttribute("Escaped", true)
		plr:SetAttribute("InRound", false)
		setFlashlight(plr, false)
		notify(plr.DisplayName .. " выбрался(-ась) из улья!", "good")
		task.delay(1.5, function()
			if plr.Parent then
				plr:LoadCharacter()
			end
		end)
	end)
end

---------------------------------------------------------------------
-- Персонажи
---------------------------------------------------------------------
local function onCharacterAdded(plr, char)
	local head = char:WaitForChild("Head", 10)
	if head and not head:FindFirstChild("HiveFlashlight") then
		local light = Instance.new("SpotLight")
		light.Name = "HiveFlashlight"
		light.Face = Enum.NormalId.Front
		light.Range = 52
		light.Angle = 48
		light.Brightness = 3
		light.Color = Color3.fromRGB(255, 244, 222)
		light.Shadows = true
		light.Enabled = false
		light.Parent = head
	end
	local hum = char:WaitForChild("Humanoid", 10)
	if hum then
		hum.WalkSpeed = Config.Player.LobbyWalkSpeed
		hum.Died:Connect(function()
			local root = getRoot(plr)
			if plr:GetAttribute("InRound") then
				if root then
					dropFuses(plr, root.Position)
				end
				leaveLocker(plr)
				clearCocoon(plr)
				plr:SetAttribute("InRound", false)
				notify(plr.DisplayName .. " больше не отвечает…", "danger")
			end
			setFlashlight(plr, false)
		end)
	end
end

Players.PlayerAdded:Connect(function(plr)
	resetPlayerAttributes(plr)
	plr.CharacterAdded:Connect(function(char)
		onCharacterAdded(plr, char)
	end)
	if plr.Character then
		task.spawn(onCharacterAdded, plr, plr.Character)
	end
end)
for _, plr in ipairs(Players:GetPlayers()) do
	resetPlayerAttributes(plr)
	plr.CharacterAdded:Connect(function(char)
		onCharacterAdded(plr, char)
	end)
	if plr.Character then
		task.spawn(onCharacterAdded, plr, plr.Character)
	end
end

Players.PlayerRemoving:Connect(function(plr)
	local root = getRoot(plr)
	if plr:GetAttribute("InRound") and root then
		dropFuses(plr, root.Position)
	end
	leaveLocker(plr)
	clearCocoon(plr)
end)

-- клиент сообщает: фонарь вкл/выкл, бежит ли
local lastState = {}
ClientStateRemote.OnServerEvent:Connect(function(plr, flashlightOn, sprinting)
	local now = os.clock()
	if lastState[plr] and now - lastState[plr] < 0.05 then
		return
	end
	lastState[plr] = now
	local active = inRound(plr) and not plr:GetAttribute("Cocooned")
	setFlashlight(plr, active and flashlightOn == true)
	plr:SetAttribute("Sprinting", active and sprinting == true and not plr:GetAttribute("Hidden"))
end)
Players.PlayerRemoving:Connect(function(plr)
	lastState[plr] = nil
end)

---------------------------------------------------------------------
-- Мерцание ламп
---------------------------------------------------------------------
local function flickerLoop(myRound)
	while roundId == myRound and map do
		task.wait(rng:NextNumber(0.08, 0.6))
		if not map or #map.Lamps == 0 then
			break
		end
		local lamp = map.Lamps[rng:NextInteger(1, #map.Lamps)]
		local light = lamp and lamp.Parent and lamp:FindFirstChildOfClass("PointLight")
		if light then
			local base = lamp:GetAttribute("BaseBrightness") or 1
			task.spawn(function()
				for _ = 1, rng:NextInteger(1, 4) do
					light.Brightness = 0
					lamp.Transparency = 0.8
					task.wait(rng:NextNumber(0.03, 0.15))
					if not lamp.Parent then
						return
					end
					light.Brightness = base
					lamp.Transparency = 0
					task.wait(rng:NextNumber(0.03, 0.2))
				end
			end)
		end
	end
end

-- лампы рядом с роем гаснут
local function lampsNearMonster()
	if not monster or not map then
		return
	end
	local pos = monster:GetPosition()
	for _, lamp in ipairs(map.Lamps) do
		local light = lamp:FindFirstChildOfClass("PointLight")
		if light then
			local near = (lamp.Position - pos).Magnitude < 20
			if near then
				light.Brightness = 0
				lamp.Transparency = 0.85
				lamp:SetAttribute("Dimmed", true)
			elseif lamp:GetAttribute("Dimmed") then
				lamp:SetAttribute("Dimmed", false)
				light.Brightness = lamp:GetAttribute("BaseBrightness") or 1
				lamp.Transparency = 0
			end
		end
	end
end

---------------------------------------------------------------------
-- Раунд
---------------------------------------------------------------------
local function cleanupRound()
	roundId += 1
	if monster then
		monster:Destroy()
		monster = nil
	end
	for plr in pairs(lockerOf) do
		leaveLocker(plr)
	end
	for plr in pairs(cocoons) do
		clearCocoon(plr)
	end
	if map then
		map:Destroy()
		map = nil
	end
	fusesInserted = 0
	exitOpen = false
	ReplicatedStorage:SetAttribute("FusesInserted", 0)
	ReplicatedStorage:SetAttribute("ExitOpen", false)
end

local function countActive()
	local free, cocooned = 0, 0
	for _, plr in ipairs(Players:GetPlayers()) do
		if inRound(plr) then
			if plr:GetAttribute("Cocooned") then
				cocooned += 1
			else
				free += 1
			end
		end
	end
	return free, cocooned
end

local function runRound()
	cleanupRound()
	local myRound = roundId
	map = MapBuilder.Build(Config, rng)
	for _, spot in ipairs(map.FuseSpots) do
		spawnFuse(spot)
	end
	for _, spot in ipairs(map.BatterySpots) do
		spawnBattery(spot)
	end
	for _, locker in ipairs(map.Lockers) do
		hookLocker(locker)
	end
	hookGenerator()
	hookExit()

	local participants = {}
	for i, plr in ipairs(Players:GetPlayers()) do
		local root = getRoot(plr)
		local hum = getHumanoid(plr)
		if root and hum and hum.Health > 0 then
			resetPlayerAttributes(plr)
			plr:SetAttribute("InRound", true)
			hum.WalkSpeed = Config.Player.WalkSpeed
			local spot = map.SpawnPoints[((i - 1) % #map.SpawnPoints) + 1]
			plr.Character:PivotTo(CFrame.lookAt(spot, spot + Vector3.new(0, 0, 1)))
			table.insert(participants, plr)
		end
	end
	if #participants == 0 then
		cleanupRound()
		return
	end

	ReplicatedStorage:SetAttribute("Phase", "Round")
	RoundRemote:FireAllClients("Start", { Seed = myRound })
	notify("Найдите " .. Config.FusesRequired .. " предохранителя. Не бегите без нужды.", "info")
	task.spawn(flickerLoop, myRound)

	task.delay(Config.Monster.SpawnDelay, function()
		if roundId ~= myRound or not map then
			return
		end
		monster = Monster.new(map, Config, rng, {
			GetTargets = monsterTargets,
			OnCatch = catchPlayer,
			OnSpotted = function(plr)
				SpottedRemote:FireClient(plr)
				playSoundAt(getRoot(plr), Config.Sounds.Spotted, 0.9)
			end,
		}, map.Folder)
		monster:SetRage(fusesInserted)
		monster:SetPowerOn(exitOpen)
		notify("Что-то шевелится в стенах…", "danger")
	end)

	local hbConn = RunService.Heartbeat:Connect(function(dt)
		if monster and roundId == myRound then
			monster:Step(math.min(dt, 0.1))
		end
	end)

	local timeLeft = Config.RoundTime
	local result = "timeout"
	while roundId == myRound do
		ReplicatedStorage:SetAttribute("TimeLeft", timeLeft)
		-- коконы с истёкшим таймером
		local now = workspace:GetServerTimeNow()
		for plr, c in pairs(cocoons) do
			if now >= c.EndsAt then
				clearCocoon(plr)
				killPlayer(plr)
			end
		end
		local free, cocooned = countActive()
		if free == 0 and cocooned > 0 then
			-- спасать некому
			for plr in pairs(cocoons) do
				clearCocoon(plr)
				killPlayer(plr)
			end
		end
		if free == 0 then
			result = "done"
			break
		end
		if timeLeft <= 0 then
			result = "timeout"
			break
		end
		-- лампы гаснут рядом с роем (проверяем 4 раза в секунду)
		for _ = 1, 4 do
			lampsNearMonster()
			task.wait(0.25)
		end
		timeLeft -= 1
	end
	hbConn:Disconnect()

	local escaped = {}
	for _, plr in ipairs(participants) do
		if plr.Parent and plr:GetAttribute("Escaped") then
			table.insert(escaped, plr.DisplayName)
		end
	end
	if result == "timeout" then
		notify("Время вышло. Улей запечатан навсегда.", "danger")
		for _, plr in ipairs(Players:GetPlayers()) do
			if inRound(plr) then
				killPlayer(plr)
			end
		end
	end
	if #escaped > 0 then
		notify("Выбрались: " .. table.concat(escaped, ", "), "good")
	else
		notify("Никто не выбрался. Рой сыт… пока что.", "danger")
	end
	RoundRemote:FireAllClients("End", { Escaped = escaped })
	ReplicatedStorage:SetAttribute("Phase", "Results")
	task.wait(6)
	cleanupRound()
	for _, plr in ipairs(Players:GetPlayers()) do
		resetPlayerAttributes(plr)
		plr:LoadCharacter()
	end
end

---------------------------------------------------------------------
-- Главный цикл
---------------------------------------------------------------------
while true do
	ReplicatedStorage:SetAttribute("Phase", "Lobby")
	local t = Config.IntermissionTime
	while t > 0 do
		ReplicatedStorage:SetAttribute("TimeLeft", t)
		task.wait(1)
		if #Players:GetPlayers() >= Config.MinPlayers then
			t -= 1
		else
			t = Config.IntermissionTime
		end
	end
	local ok, err = pcall(runRound)
	if not ok then
		warn("[Hive] Ошибка раунда: " .. tostring(err))
		cleanupRound()
		for _, plr in ipairs(Players:GetPlayers()) do
			resetPlayerAttributes(plr)
			plr:LoadCharacter()
		end
		task.wait(3)
	end
end
