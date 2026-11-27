-- Клиент «Улей: Нижний ярус»: фонарь, бег, «связь», интерфейс, атмосфера, скримеры.

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")
local ContextActionService = game:GetService("ContextActionService")
local TweenService = game:GetService("TweenService")
local Lighting = game:GetService("Lighting")

local player = Players.LocalPlayer
local Config = require(ReplicatedStorage:WaitForChild("Shared"):WaitForChild("Config"))
local Remotes = ReplicatedStorage:WaitForChild("HiveRemotes")
local P = Config.Player

---------------------------------------------------------------------
-- Состояние
---------------------------------------------------------------------
local battery = P.BatteryMax
local stamina = P.StaminaMax
local exhausted = false
local flashlightWanted = false
local sprintHeld = false
local sprinting = false
local sentFlash, sentSprint = nil, nil
local lastSend = 0
local bobTime = 0
local shake = 0
local fovPunch = 0

---------------------------------------------------------------------
-- Интерфейс
---------------------------------------------------------------------
local gui = Instance.new("ScreenGui")
gui.Name = "HiveUI"
gui.ResetOnSpawn = false
gui.IgnoreGuiInset = true
gui.ZIndexBehavior = Enum.ZIndexBehavior.Sibling
gui.Parent = player:WaitForChild("PlayerGui")

local function label(props)
	local l = Instance.new("TextLabel")
	l.BackgroundTransparency = 1
	l.Font = Enum.Font.GothamBold
	l.TextColor3 = Color3.fromRGB(235, 225, 205)
	l.TextStrokeTransparency = 0.5
	l.TextSize = 18
	for k, v in pairs(props) do
		(l :: any)[k] = v
	end
	l.Parent = props.Parent or gui
	return l
end

local function frame(props)
	local f = Instance.new("Frame")
	f.BorderSizePixel = 0
	for k, v in pairs(props) do
		(f :: any)[k] = v
	end
	f.Parent = props.Parent or gui
	return f
end

local function corner(parent, r)
	local c = Instance.new("UICorner")
	c.CornerRadius = UDim.new(0, r or 6)
	c.Parent = parent
end

-- виньетка из четырёх градиентных краёв
local vignette = frame({ Name = "Vignette", Size = UDim2.fromScale(1, 1), BackgroundTransparency = 1, ZIndex = 1 })
local vignetteEdges = {}
for _, spec in ipairs({
	{ UDim2.fromScale(1, 0.35), UDim2.fromScale(0, 0), 90 },
	{ UDim2.fromScale(1, 0.35), UDim2.fromScale(0, 0.65), -90 },
	{ UDim2.fromScale(0.3, 1), UDim2.fromScale(0, 0), 0 },
	{ UDim2.fromScale(0.3, 1), UDim2.fromScale(0.7, 0), 180 },
}) do
	local edge = frame({
		Size = spec[1],
		Position = spec[2],
		BackgroundColor3 = Color3.new(0, 0, 0),
		Parent = vignette,
	})
	local g = Instance.new("UIGradient")
	g.Rotation = spec[3]
	g.Transparency = NumberSequence.new({
		NumberSequenceKeypoint.new(0, 0.2),
		NumberSequenceKeypoint.new(1, 1),
	})
	g.Parent = edge
	table.insert(vignetteEdges, edge)
end

local statusLabel = label({
	Name = "Status",
	AnchorPoint = Vector2.new(0.5, 0),
	Position = UDim2.new(0.5, 0, 0, 44),
	Size = UDim2.fromOffset(600, 30),
	TextSize = 22,
	Font = Enum.Font.GothamBlack,
	ZIndex = 5,
})

local objective = frame({
	Name = "Objective",
	Position = UDim2.fromOffset(18, 60),
	Size = UDim2.fromOffset(300, 92),
	BackgroundColor3 = Color3.fromRGB(10, 8, 6),
	BackgroundTransparency = 0.35,
	ZIndex = 5,
})
corner(objective, 8)
label({
	Parent = objective,
	Position = UDim2.fromOffset(12, 6),
	Size = UDim2.new(1, -24, 0, 22),
	TextXAlignment = Enum.TextXAlignment.Left,
	TextColor3 = Color3.fromRGB(255, 180, 60),
	Font = Enum.Font.GothamBlack,
	Text = Config.Chapter,
	ZIndex = 6,
})
local objText = label({
	Parent = objective,
	Position = UDim2.fromOffset(12, 30),
	Size = UDim2.new(1, -24, 0, 58),
	TextXAlignment = Enum.TextXAlignment.Left,
	TextYAlignment = Enum.TextYAlignment.Top,
	TextWrapped = true,
	Font = Enum.Font.Gotham,
	TextSize = 16,
	ZIndex = 6,
})

local function bar(name, y, color)
	local holder = frame({
		Name = name,
		AnchorPoint = Vector2.new(0, 1),
		Position = UDim2.new(0, 18, 1, y),
		Size = UDim2.fromOffset(220, 14),
		BackgroundColor3 = Color3.fromRGB(20, 18, 15),
		BackgroundTransparency = 0.2,
		ZIndex = 5,
	})
	corner(holder, 7)
	local fill = frame({
		Parent = holder,
		Size = UDim2.fromScale(1, 1),
		BackgroundColor3 = color,
		ZIndex = 6,
	})
	corner(fill, 7)
	local text = label({
		Parent = holder,
		Position = UDim2.fromOffset(0, -20),
		Size = UDim2.new(1, 0, 0, 18),
		TextXAlignment = Enum.TextXAlignment.Left,
		TextSize = 14,
		Text = name,
		ZIndex = 6,
	})
	return holder, fill, text
end
local batteryHolder, batteryFill, batteryText = bar("ФОНАРЬ [F]", -24, Color3.fromRGB(255, 225, 140))
local staminaHolder, staminaFill, staminaText = bar("ДЫХАНИЕ [Shift]", -66, Color3.fromRGB(140, 200, 255))

local tetherLabel = label({
	Name = "Tether",
	AnchorPoint = Vector2.new(0, 1),
	Position = UDim2.new(0, 18, 1, -100),
	Size = UDim2.fromOffset(320, 22),
	TextXAlignment = Enum.TextXAlignment.Left,
	TextSize = 16,
	ZIndex = 5,
})

local banner = label({
	Name = "Banner",
	AnchorPoint = Vector2.new(0.5, 0),
	Position = UDim2.new(0.5, 0, 0, 90),
	Size = UDim2.new(0.8, 0, 0, 60),
	TextWrapped = true,
	TextSize = 22,
	TextTransparency = 1,
	TextStrokeTransparency = 1,
	ZIndex = 8,
})

local centerHint = label({
	Name = "CenterHint",
	AnchorPoint = Vector2.new(0.5, 0.5),
	Position = UDim2.fromScale(0.5, 0.72),
	Size = UDim2.fromOffset(600, 60),
	TextWrapped = true,
	TextSize = 22,
	Text = "",
	ZIndex = 8,
})

local hiddenOverlay = frame({
	Name = "HiddenOverlay",
	Size = UDim2.fromScale(1, 1),
	BackgroundColor3 = Color3.new(0, 0, 0),
	BackgroundTransparency = 1,
	ZIndex = 2,
})
-- узкая щель шкафчика: затемняем верх и низ экрана
local slitTop = frame({
	Parent = hiddenOverlay,
	Size = UDim2.fromScale(1, 0.42),
	BackgroundColor3 = Color3.fromRGB(8, 9, 10),
	Visible = false,
	ZIndex = 2,
})
local slitBottom = frame({
	Parent = hiddenOverlay,
	Position = UDim2.fromScale(0, 0.58),
	Size = UDim2.fromScale(1, 0.42),
	BackgroundColor3 = Color3.fromRGB(8, 9, 10),
	Visible = false,
	ZIndex = 2,
})

local spottedLabel = label({
	Name = "Spotted",
	AnchorPoint = Vector2.new(0.5, 0.5),
	Position = UDim2.fromScale(0.5, 0.4),
	Size = UDim2.fromOffset(700, 80),
	Font = Enum.Font.GothamBlack,
	TextSize = 46,
	TextColor3 = Color3.fromRGB(255, 40, 30),
	Text = "ОНО ТЕБЯ ВИДИТ",
	TextTransparency = 1,
	TextStrokeTransparency = 1,
	ZIndex = 9,
})

local titleCard = frame({
	Name = "TitleCard",
	Size = UDim2.fromScale(1, 1),
	BackgroundColor3 = Color3.new(0, 0, 0),
	BackgroundTransparency = 1,
	ZIndex = 20,
})
local titleMain = label({
	Parent = titleCard,
	AnchorPoint = Vector2.new(0.5, 0.5),
	Position = UDim2.fromScale(0.5, 0.45),
	Size = UDim2.fromOffset(900, 80),
	Font = Enum.Font.GothamBlack,
	TextSize = 56,
	TextColor3 = Color3.fromRGB(255, 180, 60),
	Text = Config.GameName,
	TextTransparency = 1,
	TextStrokeTransparency = 1,
	ZIndex = 21,
})
local titleSub = label({
	Parent = titleCard,
	AnchorPoint = Vector2.new(0.5, 0.5),
	Position = UDim2.fromScale(0.5, 0.55),
	Size = UDim2.fromOffset(900, 40),
	Font = Enum.Font.GothamBold,
	TextSize = 28,
	TextColor3 = Color3.fromRGB(200, 50, 40),
	Text = Config.Chapter,
	TextTransparency = 1,
	TextStrokeTransparency = 1,
	ZIndex = 21,
})

-- скример: красный экран, рой из чёрных точек и глаза
local scare = frame({
	Name = "Jumpscare",
	Size = UDim2.fromScale(1, 1),
	BackgroundColor3 = Color3.fromRGB(90, 0, 0),
	Visible = false,
	ZIndex = 50,
})
local scareSwarm = {}
for _ = 1, 140 do
	local s = math.random(10, 60)
	local dot = frame({
		Parent = scare,
		AnchorPoint = Vector2.new(0.5, 0.5),
		Size = UDim2.fromOffset(s, s),
		Position = UDim2.fromScale(math.random(), math.random()),
		BackgroundColor3 = Color3.fromRGB(math.random(0, 25), math.random(0, 15), 0),
		ZIndex = 51,
	})
	corner(dot, 100)
	table.insert(scareSwarm, dot)
end
local scareEyes = {}
for i, pos in ipairs({ Vector2.new(0.4, 0.42), Vector2.new(0.6, 0.42), Vector2.new(0.5, 0.6) }) do
	local size = i == 3 and 60 or 120
	local eye = frame({
		Parent = scare,
		AnchorPoint = Vector2.new(0.5, 0.5),
		Position = UDim2.fromScale(pos.X, pos.Y),
		Size = UDim2.fromOffset(size, size),
		BackgroundColor3 = Color3.fromRGB(255, 40, 20),
		ZIndex = 52,
	})
	corner(eye, 100)
	local pupil = frame({
		Parent = eye,
		AnchorPoint = Vector2.new(0.5, 0.5),
		Position = UDim2.fromScale(0.5, 0.5),
		Size = UDim2.fromScale(0.25, 0.8),
		BackgroundColor3 = Color3.new(0, 0, 0),
		ZIndex = 53,
	})
	corner(pupil, 100)
	table.insert(scareEyes, eye)
end

-- цветокоррекция страха
local fearCC = Instance.new("ColorCorrectionEffect")
fearCC.Name = "HiveFear"
fearCC.Parent = Lighting
local fearBlur = Instance.new("BlurEffect")
fearBlur.Name = "HiveBlur"
fearBlur.Size = 0
fearBlur.Parent = Lighting

local heartbeat
if Config.Sounds.Heartbeat ~= "" then
	heartbeat = Instance.new("Sound")
	heartbeat.SoundId = Config.Sounds.Heartbeat
	heartbeat.Looped = true
	heartbeat.Volume = 0
	heartbeat.Parent = gui
	heartbeat:Play()
end

---------------------------------------------------------------------
-- Сообщения
---------------------------------------------------------------------
local bannerToken = 0
local KIND_COLORS = {
	info = Color3.fromRGB(235, 225, 205),
	good = Color3.fromRGB(120, 255, 150),
	danger = Color3.fromRGB(255, 80, 60),
}
local function showBanner(text, kind)
	bannerToken += 1
	local token = bannerToken
	banner.Text = text
	banner.TextColor3 = KIND_COLORS[kind] or KIND_COLORS.info
	banner.TextTransparency = 0
	banner.TextStrokeTransparency = 0.4
	task.delay(4.5, function()
		if token ~= bannerToken then
			return
		end
		TweenService:Create(banner, TweenInfo.new(1), { TextTransparency = 1, TextStrokeTransparency = 1 }):Play()
	end)
end

Remotes:WaitForChild("Notify").OnClientEvent:Connect(showBanner)

Remotes:WaitForChild("Battery").OnClientEvent:Connect(function(amount)
	battery = math.min(P.BatteryMax, battery + (tonumber(amount) or 0))
	showBanner("+ заряд фонаря", "good")
end)

Remotes:WaitForChild("Spotted").OnClientEvent:Connect(function()
	spottedLabel.TextTransparency = 0
	spottedLabel.TextStrokeTransparency = 0.3
	fovPunch = math.max(fovPunch, 8)
	shake = math.max(shake, 0.4)
	TweenService:Create(
		spottedLabel,
		TweenInfo.new(1.6, Enum.EasingStyle.Quad, Enum.EasingDirection.In),
		{ TextTransparency = 1, TextStrokeTransparency = 1 }
	):Play()
end)

Remotes:WaitForChild("Jumpscare").OnClientEvent:Connect(function()
	scare.Visible = true
	scare.BackgroundTransparency = 0
	shake = 2.5
	fovPunch = 30
	local t0 = os.clock()
	local conn
	conn = RunService.RenderStepped:Connect(function()
		local t = os.clock() - t0
		for _, dot in ipairs(scareSwarm) do
			local p = dot.Position
			dot.Position = UDim2.fromScale(
				(p.X.Scale + (math.random() - 0.5) * 0.04) % 1,
				(p.Y.Scale + (math.random() - 0.5) * 0.04) % 1
			)
		end
		local pulse = 1 + math.sin(t * 40) * 0.15 + t * 0.6
		for i, eye in ipairs(scareEyes) do
			local base = i == 3 and 60 or 120
			eye.Size = UDim2.fromOffset(base * pulse, base * pulse)
		end
		scare.BackgroundColor3 = (math.floor(t * 20) % 2 == 0) and Color3.fromRGB(110, 0, 0) or Color3.fromRGB(20, 0, 0)
		if t > 1.3 then
			conn:Disconnect()
			TweenService:Create(scare, TweenInfo.new(0.6), { BackgroundTransparency = 1 }):Play()
			task.delay(0.6, function()
				scare.Visible = false
			end)
		end
	end)
end)

local function playTitleCard()
	titleCard.BackgroundTransparency = 0
	titleMain.TextTransparency = 1
	titleSub.TextTransparency = 1
	local info = TweenInfo.new(1.2)
	TweenService:Create(titleMain, info, { TextTransparency = 0, TextStrokeTransparency = 0.6 }):Play()
	task.wait(0.8)
	TweenService:Create(titleSub, info, { TextTransparency = 0, TextStrokeTransparency = 0.6 }):Play()
	task.wait(2.2)
	local out = TweenInfo.new(1.5)
	TweenService:Create(titleMain, out, { TextTransparency = 1, TextStrokeTransparency = 1 }):Play()
	TweenService:Create(titleSub, out, { TextTransparency = 1, TextStrokeTransparency = 1 }):Play()
	TweenService:Create(titleCard, out, { BackgroundTransparency = 1 }):Play()
end

Remotes:WaitForChild("Round").OnClientEvent:Connect(function(kind)
	if kind == "Start" then
		battery = P.BatteryMax
		stamina = P.StaminaMax
		exhausted = false
		flashlightWanted = false
		task.spawn(playTitleCard)
	end
end)

---------------------------------------------------------------------
-- Управление (клавиатура, геймпад, телефон)
---------------------------------------------------------------------
local function onSprint(_, state)
	if state == Enum.UserInputState.Begin then
		sprintHeld = true
	elseif state == Enum.UserInputState.End or state == Enum.UserInputState.Cancel then
		sprintHeld = false
	end
	return Enum.ContextActionResult.Pass
end

local function onFlashlight(_, state)
	if state == Enum.UserInputState.Begin and player:GetAttribute("InRound") then
		if battery > 0 then
			flashlightWanted = not flashlightWanted
		else
			showBanner("Фонарь разряжен. Ищи батарейки.", "danger")
		end
	end
	return Enum.ContextActionResult.Pass
end

ContextActionService:BindAction("HiveSprint", onSprint, true, Enum.KeyCode.LeftShift, Enum.KeyCode.ButtonL3)
ContextActionService:SetTitle("HiveSprint", "Бег")
ContextActionService:SetPosition("HiveSprint", UDim2.new(1, -170, 1, -150))
ContextActionService:BindAction("HiveFlashlight", onFlashlight, true, Enum.KeyCode.F, Enum.KeyCode.ButtonY)
ContextActionService:SetTitle("HiveFlashlight", "Фонарь")
ContextActionService:SetPosition("HiveFlashlight", UDim2.new(1, -230, 1, -90))

---------------------------------------------------------------------
-- Главный цикл кадра
---------------------------------------------------------------------
local function findMonster()
	local mapFolder = workspace:FindFirstChild("HiveMap")
	return mapFolder and mapFolder:FindFirstChild("HiveMonster")
end

local function nearestTeammate(root)
	local best, bestDist = nil, math.huge
	for _, other in ipairs(Players:GetPlayers()) do
		if other ~= player and other:GetAttribute("InRound") then
			local r = other.Character and other.Character:FindFirstChild("HumanoidRootPart")
			if r then
				local d = (r.Position - root.Position).Magnitude
				if d < bestDist then
					best, bestDist = other, d
				end
			end
		end
	end
	return best, bestDist
end

local function formatTime(sec)
	sec = math.max(0, math.floor(sec or 0))
	return string.format("%d:%02d", sec // 60, sec % 60)
end

RunService.RenderStepped:Connect(function(dt)
	local camera = workspace.CurrentCamera
	local char = player.Character
	local hum = char and char:FindFirstChildOfClass("Humanoid")
	local root = char and char:FindFirstChild("HumanoidRootPart")
	local phase = ReplicatedStorage:GetAttribute("Phase")
	local inRound = player:GetAttribute("InRound") == true and hum ~= nil and hum.Health > 0
	local hidden = player:GetAttribute("Hidden") == true
	local cocooned = player:GetAttribute("Cocooned") == true

	-- камера: в улье только от первого лица
	if inRound then
		player.CameraMode = Enum.CameraMode.LockFirstPerson
	else
		player.CameraMode = Enum.CameraMode.Classic
	end

	-- «связь» с напарником
	local linked, mate = false, nil
	if inRound and root then
		local other, dist = nearestTeammate(root)
		if other then
			linked = dist <= Config.Tether.Range
			mate = other
		end
	end

	-- бег и выносливость
	local moving = hum ~= nil and hum.MoveDirection.Magnitude > 0.1
	if stamina <= 0 then
		exhausted = true
	elseif exhausted and stamina >= P.StaminaRecoverAt then
		exhausted = false
	end
	sprinting = inRound and sprintHeld and moving and not exhausted and not hidden and not cocooned
	if sprinting then
		stamina = math.max(0, stamina - P.StaminaDrain * dt)
	else
		local regen = P.StaminaRegen * (linked and Config.Tether.StaminaRegenMultiplier or 1)
		stamina = math.min(P.StaminaMax, stamina + regen * dt)
	end
	if hum then
		if inRound then
			hum.WalkSpeed = sprinting and P.SprintSpeed or P.WalkSpeed
		else
			hum.WalkSpeed = P.LobbyWalkSpeed
		end
	end

	-- фонарь
	local flashOn = inRound and flashlightWanted and battery > 0 and not cocooned
	if flashOn then
		local drain = P.BatteryDrain * (linked and Config.Tether.BatteryDrainMultiplier or 1)
		battery = math.max(0, battery - drain * dt)
		if battery <= 0 then
			flashlightWanted = false
			flashOn = false
			showBanner("Фонарь погас…", "danger")
		end
	end
	-- при низком заряде фонарь мигает (только у себя на экране)
	local head = char and char:FindFirstChild("Head")
	local light = head and head:FindFirstChild("HiveFlashlight")
	if light and flashOn then
		local low = battery < 20 and math.random() < 0.08
		light.Brightness = low and 0.3 or 3
	end

	-- отправка состояния на сервер (только при изменении)
	local now = os.clock()
	if (flashOn ~= sentFlash or sprinting ~= sentSprint) and now - lastSend > 0.1 then
		sentFlash, sentSprint, lastSend = flashOn, sprinting, now
		Remotes.ClientState:FireServer(flashOn, sprinting)
	end

	-- страх: близость роя
	local fear = 0
	local monster = findMonster()
	if monster and inRound then
		local d = (monster:GetPivot().Position - camera.CFrame.Position).Magnitude
		fear = math.clamp(1 - d / 60, 0, 1)
		if monster:GetAttribute("State") == "Chase" then
			fear = math.max(fear, 0.35)
		end
	end
	fearCC.Saturation = -fear * 0.7
	fearCC.Contrast = fear * 0.35
	fearCC.TintColor = Color3.new(1, 1 - fear * 0.35, 1 - fear * 0.4)
	fearBlur.Size = fear > 0.6 and (fear - 0.6) * 10 or 0
	if heartbeat then
		heartbeat.Volume = fear * 1.2
		heartbeat.PlaybackSpeed = 0.9 + fear * 0.6
	end
	shake = math.max(shake - dt * 2, fear * 0.12)
	for _, edge in ipairs(vignetteEdges) do
		edge.BackgroundTransparency = math.clamp(0.25 - fear * 0.25 - (hidden and 0.2 or 0), 0, 1)
	end

	-- покачивание камеры и тряска
	if hum and inRound then
		if moving and not hidden then
			bobTime += dt * (sprinting and 14 or 8)
		end
		local bobAmp = sprinting and 0.28 or 0.14
		local bob = Vector3.new(math.sin(bobTime) * bobAmp * 0.6, math.abs(math.cos(bobTime)) * bobAmp, 0)
		local jitter = Vector3.new(math.random() - 0.5, math.random() - 0.5, math.random() - 0.5) * shake
		hum.CameraOffset = (moving and not hidden) and (bob + jitter) or jitter
	elseif hum then
		hum.CameraOffset = Vector3.zero
	end
	fovPunch = math.max(0, fovPunch - dt * 25)
	local baseFov = (inRound and sprinting) and 78 or 70
	camera.FieldOfView = camera.FieldOfView + ((baseFov - fovPunch) - camera.FieldOfView) * math.min(1, dt * 8)

	-- прятки
	slitTop.Visible = hidden
	slitBottom.Visible = hidden

	-- интерфейс
	local timeLeft = ReplicatedStorage:GetAttribute("TimeLeft") or 0
	if phase == "Lobby" then
		statusLabel.Text = "Спуск в улей через " .. formatTime(timeLeft)
	elseif phase == "Round" then
		statusLabel.Text = inRound and ("⏱ " .. formatTime(timeLeft))
			or ("Идёт раунд · " .. formatTime(timeLeft) .. " · дождитесь следующего")
	else
		statusLabel.Text = "Итоги раунда"
	end
	statusLabel.TextColor3 = (phase == "Round" and timeLeft < 60) and Color3.fromRGB(255, 70, 60)
		or Color3.fromRGB(235, 225, 205)

	objective.Visible = inRound
	batteryHolder.Visible = inRound
	staminaHolder.Visible = inRound
	tetherLabel.Visible = inRound
	if inRound then
		local inserted = ReplicatedStorage:GetAttribute("FusesInserted") or 0
		local required = ReplicatedStorage:GetAttribute("FusesRequired") or Config.FusesRequired
		local carried = player:GetAttribute("Fuses") or 0
		if ReplicatedStorage:GetAttribute("ExitOpen") then
			objText.Text = "Лифт запитан! Беги к зелёному маяку."
		else
			objText.Text = string.format(
				"Генератор: %d/%d\nПредохранителей у тебя: %d%s",
				inserted,
				required,
				carried,
				carried > 0 and "  → неси к генератору у входа" or ""
			)
		end
		batteryFill.Size = UDim2.fromScale(battery / P.BatteryMax, 1)
		batteryFill.BackgroundColor3 = battery < 20 and Color3.fromRGB(255, 90, 60) or Color3.fromRGB(255, 225, 140)
		batteryText.Text = string.format("ФОНАРЬ [F] %d%%%s", math.floor(battery), flashOn and " · вкл" or "")
		staminaFill.Size = UDim2.fromScale(stamina / P.StaminaMax, 1)
		staminaFill.BackgroundColor3 = exhausted and Color3.fromRGB(150, 150, 150) or Color3.fromRGB(140, 200, 255)
		staminaText.Text = exhausted and "ДЫХАНИЕ — задыхаешься…" or "ДЫХАНИЕ [Shift]"
		if mate then
			if linked then
				tetherLabel.Text = "🔗 Связь с " .. mate.DisplayName .. ": дыхание и фонарь дольше"
				tetherLabel.TextColor3 = Color3.fromRGB(120, 255, 150)
			else
				tetherLabel.Text = "⛓ Связь потеряна — держись ближе к команде"
				tetherLabel.TextColor3 = Color3.fromRGB(255, 120, 90)
			end
		else
			tetherLabel.Text = "Ты один в улье"
			tetherLabel.TextColor3 = Color3.fromRGB(170, 160, 150)
		end
	end

	if cocooned then
		local left = (player:GetAttribute("CocoonEndsAt") or 0) - workspace:GetServerTimeNow()
		centerHint.Text = string.format("Ты в коконе. Команда может тебя вытащить: %d сек.", math.max(0, math.ceil(left)))
		centerHint.TextColor3 = Color3.fromRGB(255, 140, 60)
	elseif hidden then
		centerHint.Text = "Не дыши… [E] — выйти"
		centerHint.TextColor3 = Color3.fromRGB(200, 200, 200)
	else
		centerHint.Text = ""
	end
end)
