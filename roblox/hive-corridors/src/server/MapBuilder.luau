-- Генератор лабиринта-улья и лобби. Всё строится из обычных Part,
-- поэтому игра работает в пустом плейсе без единого ассета.

local MapBuilder = {}

local DIRS = {
	N = { dx = 0, dz = -1, opposite = "S" },
	E = { dx = 1, dz = 0, opposite = "W" },
	S = { dx = 0, dz = 1, opposite = "N" },
	W = { dx = -1, dz = 0, opposite = "E" },
}
local DIR_ORDER = { "N", "E", "S", "W" }

local COLORS = {
	Wall = Color3.fromRGB(62, 56, 46),
	WallTrim = Color3.fromRGB(40, 36, 30),
	Floor = Color3.fromRGB(34, 31, 27),
	Ceiling = Color3.fromRGB(22, 20, 18),
	Lamp = Color3.fromRGB(255, 210, 150),
	Nest = Color3.fromRGB(96, 72, 30),
	NestDark = Color3.fromRGB(52, 38, 16),
	Locker = Color3.fromRGB(66, 76, 80),
	Generator = Color3.fromRGB(46, 58, 44),
	Danger = Color3.fromRGB(255, 40, 40),
	Safe = Color3.fromRGB(60, 255, 120),
}
MapBuilder.Colors = COLORS

local function newPart(props, parent)
	local p = Instance.new("Part")
	p.Anchored = true
	p.TopSurface = Enum.SurfaceType.Smooth
	p.BottomSurface = Enum.SurfaceType.Smooth
	for k, v in pairs(props) do
		(p :: any)[k] = v
	end
	p.Parent = parent
	return p
end
MapBuilder.NewPart = newPart

local function newPrompt(parent, actionText, objectText, holdDuration, distance)
	local prompt = Instance.new("ProximityPrompt")
	prompt.ActionText = actionText
	prompt.ObjectText = objectText or ""
	prompt.HoldDuration = holdDuration or 0
	prompt.MaxActivationDistance = distance or 9
	prompt.RequiresLineOfSight = false
	prompt.KeyboardKeyCode = Enum.KeyCode.E
	prompt.Parent = parent
	return prompt
end
MapBuilder.NewPrompt = newPrompt

local function billboard(parent, text, color, studsOffset)
	local gui = Instance.new("BillboardGui")
	gui.Size = UDim2.fromOffset(220, 50)
	gui.StudsOffset = studsOffset or Vector3.new(0, 4, 0)
	gui.AlwaysOnTop = false
	gui.MaxDistance = 60
	gui.LightInfluence = 0
	local label = Instance.new("TextLabel")
	label.Size = UDim2.fromScale(1, 1)
	label.BackgroundTransparency = 1
	label.Font = Enum.Font.GothamBlack
	label.TextScaled = true
	label.TextColor3 = color
	label.TextStrokeTransparency = 0.3
	label.Text = text
	label.Parent = gui
	gui.Parent = parent
	return gui, label
end

---------------------------------------------------------------------
-- Генерация лабиринта (recursive backtracker + петли)
---------------------------------------------------------------------
local function generateMaze(w, h, rng, extraLoops)
	local cells = {}
	for x = 1, w do
		cells[x] = {}
		for z = 1, h do
			cells[x][z] = { N = false, E = false, S = false, W = false, visited = false }
		end
	end

	local stack = { { 1, 1 } }
	cells[1][1].visited = true
	while #stack > 0 do
		local cur = stack[#stack]
		local cx, cz = cur[1], cur[2]
		local options = {}
		for _, d in ipairs(DIR_ORDER) do
			local nx, nz = cx + DIRS[d].dx, cz + DIRS[d].dz
			if nx >= 1 and nx <= w and nz >= 1 and nz <= h and not cells[nx][nz].visited then
				table.insert(options, d)
			end
		end
		if #options == 0 then
			table.remove(stack)
		else
			local d = options[rng:NextInteger(1, #options)]
			local nx, nz = cx + DIRS[d].dx, cz + DIRS[d].dz
			cells[cx][cz][d] = true
			cells[nx][nz][DIRS[d].opposite] = true
			cells[nx][nz].visited = true
			table.insert(stack, { nx, nz })
		end
	end

	local added, tries = 0, 0
	while added < extraLoops and tries < extraLoops * 30 do
		tries += 1
		local x, z = rng:NextInteger(1, w), rng:NextInteger(1, h)
		local d = DIR_ORDER[rng:NextInteger(1, 4)]
		local nx, nz = x + DIRS[d].dx, z + DIRS[d].dz
		if nx >= 1 and nx <= w and nz >= 1 and nz <= h and not cells[x][z][d] then
			cells[x][z][d] = true
			cells[nx][nz][DIRS[d].opposite] = true
			added += 1
		end
	end

	return cells
end

---------------------------------------------------------------------
-- Объект карты: навигация по клеткам
---------------------------------------------------------------------
local Map = {}
Map.__index = Map

function Map:CellToWorld(x, z, y)
	return self.Origin + Vector3.new((x - 1) * self.CellSize, y or 0, (z - 1) * self.CellSize)
end

function Map:WorldToCell(pos)
	local rel = pos - self.Origin
	local x = math.clamp(math.floor(rel.X / self.CellSize + 0.5) + 1, 1, self.Width)
	local z = math.clamp(math.floor(rel.Z / self.CellSize + 0.5) + 1, 1, self.Height)
	return x, z
end

function Map:Neighbors(x, z)
	local out = {}
	local cell = self.Cells[x][z]
	for _, d in ipairs(DIR_ORDER) do
		if cell[d] then
			table.insert(out, { x + DIRS[d].dx, z + DIRS[d].dz })
		end
	end
	return out
end

function Map:_index(x, z)
	return (z - 1) * self.Width + x
end

-- BFS: расстояния (в клетках) от точки до всех клеток
function Map:Distances(sx, sz)
	local dist = {}
	dist[self:_index(sx, sz)] = 0
	local queue, head = { { sx, sz } }, 1
	while head <= #queue do
		local cur = queue[head]
		head += 1
		local base = dist[self:_index(cur[1], cur[2])]
		for _, n in ipairs(self:Neighbors(cur[1], cur[2])) do
			local i = self:_index(n[1], n[2])
			if dist[i] == nil then
				dist[i] = base + 1
				table.insert(queue, n)
			end
		end
	end
	return dist
end

function Map:DistanceBetween(ax, az, bx, bz)
	return self:Distances(ax, az)[self:_index(bx, bz)] or math.huge
end

-- Путь из клеток от (sx,sz) до (tx,tz), включая обе
function Map:FindPath(sx, sz, tx, tz)
	local prev = {}
	local startI = self:_index(sx, sz)
	local targetI = self:_index(tx, tz)
	prev[startI] = false
	local queue, head = { { sx, sz } }, 1
	while head <= #queue do
		local cur = queue[head]
		head += 1
		if self:_index(cur[1], cur[2]) == targetI then
			break
		end
		for _, n in ipairs(self:Neighbors(cur[1], cur[2])) do
			local i = self:_index(n[1], n[2])
			if prev[i] == nil then
				prev[i] = cur
				table.insert(queue, n)
			end
		end
	end
	if prev[targetI] == nil then
		return { { sx, sz } }
	end
	local path = {}
	local node = { tx, tz }
	while node do
		table.insert(path, 1, node)
		node = prev[self:_index(node[1], node[2])]
	end
	return path
end

function Map:RandomCell(rng)
	return rng:NextInteger(1, self.Width), rng:NextInteger(1, self.Height)
end

-- Точка у стены клетки (для шкафчиков, генератора и т.п.)
function Map:WallSpot(x, z, dir, inset, y)
	local center = self:CellToWorld(x, z, y or 0)
	local d = DIRS[dir]
	local offset = self.CellSize / 2 - self.WallThickness / 2 - inset
	local pos = center + Vector3.new(d.dx * offset, 0, d.dz * offset)
	-- смотрим в центр клетки
	return CFrame.lookAt(pos, Vector3.new(center.X, pos.Y, center.Z))
end

function Map:ClosedSides(x, z)
	local out = {}
	for _, d in ipairs(DIR_ORDER) do
		if not self.Cells[x][z][d] then
			table.insert(out, d)
		end
	end
	return out
end

function Map:Destroy()
	if self.Folder then
		self.Folder:Destroy()
		self.Folder = nil
	end
end

---------------------------------------------------------------------
-- Декор
---------------------------------------------------------------------
local function buildNest(parent, center, rng, cellSize, wallHeight)
	local count = rng:NextInteger(2, 5)
	for _ = 1, count do
		local size = rng:NextNumber(1.5, 4.5)
		local onCeiling = rng:NextNumber() < 0.4
		local spread = cellSize * 0.35
		local pos = center
			+ Vector3.new(
				rng:NextNumber(-spread, spread),
				onCeiling and (wallHeight - size * 0.3) or (size * 0.3),
				rng:NextNumber(-spread, spread)
			)
		newPart({
			Name = "Nest",
			Shape = Enum.PartType.Ball,
			Size = Vector3.new(size, size, size),
			Position = pos,
			Color = rng:NextNumber() < 0.5 and COLORS.Nest or COLORS.NestDark,
			Material = Enum.Material.Slate,
			CanCollide = false,
			CastShadow = true,
		}, parent)
	end
	-- «соты» — плоские шестигранники на полу
	if rng:NextNumber() < 0.5 then
		local hex = newPart({
			Name = "Comb",
			Shape = Enum.PartType.Cylinder,
			Size = Vector3.new(0.2, 6, 6),
			CFrame = CFrame.new(center + Vector3.new(rng:NextNumber(-6, 6), 0.1, rng:NextNumber(-6, 6)))
				* CFrame.Angles(0, 0, math.rad(90)),
			Color = Color3.fromRGB(120, 90, 30),
			Material = Enum.Material.Glass,
			Transparency = 0.2,
			CanCollide = false,
		}, parent)
		hex.Reflectance = 0.1
	end
end

local function buildLamp(parent, pos)
	local lamp = newPart({
		Name = "Lamp",
		Size = Vector3.new(2.4, 0.4, 2.4),
		Position = pos,
		Color = COLORS.Lamp,
		Material = Enum.Material.Neon,
		CanCollide = false,
		CastShadow = false,
	}, parent)
	local light = Instance.new("PointLight")
	light.Range = 22
	light.Brightness = 1.1
	light.Color = COLORS.Lamp
	light.Shadows = true
	light.Parent = lamp
	lamp:SetAttribute("BaseBrightness", light.Brightness)
	return lamp
end

-- Шкафчик-укрытие: полый, со щелью на уровне глаз
local LOCKER_W, LOCKER_H, LOCKER_D, LOCKER_T = 4.6, 8, 3.6, 0.3
local function buildLocker(parent, baseCf)
	local model = Instance.new("Model")
	model.Name = "Locker"
	local cf = baseCf * CFrame.new(0, LOCKER_H / 2, 0)
	local function panel(name, offset, size)
		return newPart({
			Name = name,
			Size = size,
			CFrame = cf * offset,
			Color = COLORS.Locker,
			Material = Enum.Material.DiamondPlate,
		}, model)
	end
	panel("Back", CFrame.new(0, 0, LOCKER_D / 2 - LOCKER_T / 2), Vector3.new(LOCKER_W, LOCKER_H, LOCKER_T))
	panel("Left", CFrame.new(-LOCKER_W / 2 + LOCKER_T / 2, 0, 0), Vector3.new(LOCKER_T, LOCKER_H, LOCKER_D))
	panel("Right", CFrame.new(LOCKER_W / 2 - LOCKER_T / 2, 0, 0), Vector3.new(LOCKER_T, LOCKER_H, LOCKER_D))
	panel("Top", CFrame.new(0, LOCKER_H / 2 - LOCKER_T / 2, 0), Vector3.new(LOCKER_W, LOCKER_T, LOCKER_D))
	-- дверца из двух половин, между ними щель 4.2–4.8 от пола (уровень глаз)
	local doorZ = -LOCKER_D / 2 + LOCKER_T / 2
	local low = panel("DoorLow", CFrame.new(0, -LOCKER_H / 2 + 2.1, doorZ), Vector3.new(LOCKER_W, 4.2, LOCKER_T))
	panel("DoorHigh", CFrame.new(0, -LOCKER_H / 2 + 4.8 + 1.6, doorZ), Vector3.new(LOCKER_W, 3.2, LOCKER_T))
	model.PrimaryPart = low

	-- точка пряток: корень персонажа на высоте 3 над полом, смотрит наружу
	local hidePoint = Instance.new("Attachment")
	hidePoint.Name = "HidePoint"
	hidePoint.Parent = low
	hidePoint.WorldCFrame = baseCf * CFrame.new(0, 3, 0.2)

	local exitPoint = Instance.new("Attachment")
	exitPoint.Name = "ExitPoint"
	exitPoint.Parent = low
	exitPoint.WorldCFrame = baseCf * CFrame.new(0, 3, -4)

	local prompt = newPrompt(low, "Спрятаться", "Шкафчик", 0, 8)
	model.Parent = parent
	return { Model = model, Prompt = prompt, HidePoint = hidePoint, ExitPoint = exitPoint, Occupant = nil }
end

local function buildGenerator(parent, baseCf, required)
	local model = Instance.new("Model")
	model.Name = "Generator"
	local body = newPart({
		Name = "Body",
		Size = Vector3.new(7, 5, 3.4),
		CFrame = baseCf * CFrame.new(0, 2.5, 0),
		Color = COLORS.Generator,
		Material = Enum.Material.Metal,
	}, model)
	model.PrimaryPart = body
	local indicators = {}
	for i = 1, required do
		local x = (i - (required + 1) / 2) * 1.8
		local ind = newPart({
			Name = "Indicator" .. i,
			Size = Vector3.new(1, 1, 0.3),
			CFrame = baseCf * CFrame.new(x, 3.6, -1.85),
			Color = COLORS.Danger,
			Material = Enum.Material.Neon,
			CanCollide = false,
		}, model)
		local l = Instance.new("PointLight")
		l.Range = 6
		l.Brightness = 1.5
		l.Color = COLORS.Danger
		l.Parent = ind
		table.insert(indicators, ind)
	end
	billboard(body, "ГЕНЕРАТОР", Color3.fromRGB(255, 200, 80), Vector3.new(0, 4.5, 0))
	local prompt = newPrompt(body, "Вставить предохранитель", "Генератор", 1, 9)
	model.Parent = parent
	return { Model = model, Prompt = prompt, Indicators = indicators }
end

local function buildLift(parent, center)
	local model = Instance.new("Model")
	model.Name = "Lift"
	local pad = newPart({
		Name = "Pad",
		Size = Vector3.new(11, 0.4, 11),
		Position = center + Vector3.new(0, 0.2, 0),
		Color = Color3.fromRGB(70, 70, 75),
		Material = Enum.Material.DiamondPlate,
	}, model)
	local zone = newPart({
		Name = "ExitZone",
		Size = Vector3.new(9, 6, 9),
		Position = center + Vector3.new(0, 3.4, 0),
		Transparency = 1,
		CanCollide = false,
	}, model)
	local barrier = newPart({
		Name = "Barrier",
		Size = Vector3.new(12, 14, 12),
		Position = center + Vector3.new(0, 7, 0),
		Color = COLORS.Danger,
		Material = Enum.Material.ForceField,
		Transparency = 0,
	}, model)
	local beacon = newPart({
		Name = "Beacon",
		Size = Vector3.new(1.4, 1.4, 1.4),
		Shape = Enum.PartType.Ball,
		Position = center + Vector3.new(0, 14.5, 0),
		Color = COLORS.Danger,
		Material = Enum.Material.Neon,
		CanCollide = false,
	}, model)
	local light = Instance.new("PointLight")
	light.Range = 26
	light.Brightness = 2
	light.Color = COLORS.Danger
	light.Parent = beacon
	local _, label = billboard(pad, "ЛИФТ: НЕТ ПИТАНИЯ", COLORS.Danger, Vector3.new(0, 9, 0))
	model.Parent = parent
	return {
		Model = model,
		Pad = pad,
		Zone = zone,
		Barrier = barrier,
		Beacon = beacon,
		Light = light,
		Label = label,
	}
end

---------------------------------------------------------------------
-- Сборка карты
---------------------------------------------------------------------
function MapBuilder.Build(config, rng)
	local w, h, C = config.MazeWidth, config.MazeHeight, config.CellSize
	local WH, T = config.WallHeight, config.WallThickness
	local origin = config.MazeOrigin

	local self = setmetatable({
		Width = w,
		Height = h,
		CellSize = C,
		WallHeight = WH,
		WallThickness = T,
		Origin = origin,
		Cells = generateMaze(w, h, rng, config.ExtraLoops),
		Lamps = {},
		Lockers = {},
		FuseSpots = {},
		BatterySpots = {},
	}, Map)

	local folder = Instance.new("Folder")
	folder.Name = "HiveMap"
	local geometry = Instance.new("Folder")
	geometry.Name = "Geometry"
	geometry.Parent = folder
	local decor = Instance.new("Folder")
	decor.Name = "Decor"
	decor.Parent = folder
	local interact = Instance.new("Folder")
	interact.Name = "Interactables"
	interact.Parent = folder
	self.Folder = folder
	self.Geometry = geometry
	self.Interactables = interact

	-- пол и потолок
	local sizeX, sizeZ = w * C + T, h * C + T
	local mid = origin + Vector3.new((w - 1) * C / 2, 0, (h - 1) * C / 2)
	newPart({
		Name = "Floor",
		Size = Vector3.new(sizeX, 1, sizeZ),
		Position = mid - Vector3.new(0, 0.5, 0),
		Color = COLORS.Floor,
		Material = Enum.Material.Concrete,
	}, geometry)
	newPart({
		Name = "Ceiling",
		Size = Vector3.new(sizeX, 1, sizeZ),
		Position = mid + Vector3.new(0, WH + 0.5, 0),
		Color = COLORS.Ceiling,
		Material = Enum.Material.Concrete,
		CastShadow = false,
	}, geometry)

	-- стены
	local function wall(pos, size)
		newPart({
			Name = "Wall",
			Size = size,
			Position = pos,
			Color = COLORS.Wall,
			Material = Enum.Material.Concrete,
		}, geometry)
		-- тёмный плинтус для глубины
		newPart({
			Name = "Trim",
			Size = Vector3.new(size.X + 0.2, 1.2, size.Z + 0.2),
			Position = Vector3.new(pos.X, origin.Y + 0.6, pos.Z),
			Color = COLORS.WallTrim,
			Material = Enum.Material.Slate,
			CanCollide = false,
		}, geometry)
	end

	for x = 1, w do
		for z = 1, h do
			local c = self:CellToWorld(x, z, WH / 2)
			local cell = self.Cells[x][z]
			if not cell.E then
				wall(c + Vector3.new(C / 2, 0, 0), Vector3.new(T, WH, C + T))
			end
			if not cell.S then
				wall(c + Vector3.new(0, 0, C / 2), Vector3.new(C + T, WH, T))
			end
			if x == 1 then
				wall(c - Vector3.new(C / 2, 0, 0), Vector3.new(T, WH, C + T))
			end
			if z == 1 then
				wall(c - Vector3.new(0, 0, C / 2), Vector3.new(C + T, WH, T))
			end
		end
	end

	-- ключевые точки
	self.StartCell = { 1, 1 }
	local distFromStart = self:Distances(1, 1)
	local exitX, exitZ, best = w, h, -1
	local deadEnds = {}
	for x = 1, w do
		for z = 1, h do
			local d = distFromStart[self:_index(x, z)] or 0
			if d > best then
				best, exitX, exitZ = d, x, z
			end
			if #self:Neighbors(x, z) == 1 and not (x == 1 and z == 1) then
				table.insert(deadEnds, { x, z, d })
			end
		end
	end
	self.ExitCell = { exitX, exitZ }
	self.MaxDistance = best

	-- монстр появляется в середине пути, не у старта и не у выхода
	local spawnX, spawnZ, spawnScore = exitX, exitZ, -math.huge
	for _ = 1, 40 do
		local x, z = self:RandomCell(rng)
		local d = distFromStart[self:_index(x, z)] or 0
		local score = -math.abs(d - best * 0.6)
		if d >= 4 and not (x == exitX and z == exitZ) and score > spawnScore then
			spawnScore, spawnX, spawnZ = score, x, z
		end
	end
	self.MonsterSpawnCell = { spawnX, spawnZ }

	-- предохранители: в дальних тупиках
	table.sort(deadEnds, function(a, b)
		return a[3] > b[3]
	end)
	local used = {}
	local function markUsed(x, z)
		used[self:_index(x, z)] = true
	end
	markUsed(1, 1)
	markUsed(exitX, exitZ)
	for _, de in ipairs(deadEnds) do
		if #self.FuseSpots >= config.FusesSpawned then
			break
		end
		if de[3] >= 3 and not used[self:_index(de[1], de[2])] then
			markUsed(de[1], de[2])
			table.insert(self.FuseSpots, self:CellToWorld(de[1], de[2], 1.5))
		end
	end
	local guard = 0
	while #self.FuseSpots < config.FusesSpawned and guard < 200 do
		guard += 1
		local x, z = self:RandomCell(rng)
		if not used[self:_index(x, z)] and (distFromStart[self:_index(x, z)] or 0) >= 3 then
			markUsed(x, z)
			table.insert(self.FuseSpots, self:CellToWorld(x, z, 1.5))
		end
	end

	-- батарейки
	guard = 0
	while #self.BatterySpots < config.BatteryCount and guard < 300 do
		guard += 1
		local x, z = self:RandomCell(rng)
		if not used[self:_index(x, z)] then
			markUsed(x, z)
			local jitter = Vector3.new(rng:NextNumber(-6, 6), 0, rng:NextNumber(-6, 6))
			table.insert(self.BatterySpots, self:CellToWorld(x, z, 1) + jitter)
		end
	end

	-- лампы и наросты
	for x = 1, w do
		for z = 1, h do
			local center = self:CellToWorld(x, z, 0)
			if rng:NextNumber() < config.LampChance or (x == 1 and z == 1) then
				table.insert(self.Lamps, buildLamp(decor, center + Vector3.new(0, WH - 0.3, 0)))
			end
			if rng:NextNumber() < config.NestChance and not (x == 1 and z == 1) then
				buildNest(decor, center, rng, C, WH)
			end
		end
	end

	-- шкафчики
	local lockerCells = {}
	guard = 0
	while #self.Lockers < config.LockerCount and guard < 400 do
		guard += 1
		local x, z = self:RandomCell(rng)
		local key = self:_index(x, z)
		if not lockerCells[key] and not (x == exitX and z == exitZ) and not (x == 1 and z == 1) then
			local sides = self:ClosedSides(x, z)
			if #sides > 0 then
				lockerCells[key] = true
				local side = sides[rng:NextInteger(1, #sides)]
				local cf = self:WallSpot(x, z, side, LOCKER_D / 2 + 0.05)
				-- сдвиг вдоль стены, чтобы шкафчики не стояли строго по центру
				cf = cf * CFrame.new(rng:NextNumber(-C * 0.25, C * 0.25), 0, 0)
				table.insert(self.Lockers, buildLocker(interact, cf))
			end
		end
	end

	-- генератор у северной стены стартовой клетки (там всегда граница)
	self.Generator = buildGenerator(interact, self:WallSpot(1, 1, "N", 1.8), config.FusesRequired)
	self.Lift = buildLift(interact, self:CellToWorld(exitX, exitZ, 0))

	-- точки появления игроков
	local startCenter = self:CellToWorld(1, 1, 3)
	self.SpawnPoints = {}
	for i = 0, 7 do
		local a = i / 8 * math.pi * 2
		table.insert(self.SpawnPoints, startCenter + Vector3.new(math.cos(a) * 5, 0, math.sin(a) * 5 + 3))
	end

	folder.Parent = workspace
	return self
end

---------------------------------------------------------------------
-- Лобби
---------------------------------------------------------------------
function MapBuilder.BuildLobby(config)
	local existing = workspace:FindFirstChild("HiveLobby")
	if existing then
		existing:Destroy()
	end
	local folder = Instance.new("Folder")
	folder.Name = "HiveLobby"
	local p = config.LobbyPosition
	local size, height = 64, 18

	newPart({
		Name = "Floor",
		Size = Vector3.new(size, 1, size),
		Position = p - Vector3.new(0, 0.5, 0),
		Color = Color3.fromRGB(45, 40, 34),
		Material = Enum.Material.WoodPlanks,
	}, folder)
	newPart({
		Name = "Ceiling",
		Size = Vector3.new(size, 1, size),
		Position = p + Vector3.new(0, height + 0.5, 0),
		Color = Color3.fromRGB(25, 22, 20),
		Material = Enum.Material.Concrete,
	}, folder)
	for _, d in ipairs({ Vector3.new(1, 0, 0), Vector3.new(-1, 0, 0), Vector3.new(0, 0, 1), Vector3.new(0, 0, -1) }) do
		local isX = d.X ~= 0
		newPart({
			Name = "Wall",
			Size = isX and Vector3.new(1, height, size) or Vector3.new(size, height, 1),
			Position = p + d * (size / 2) + Vector3.new(0, height / 2, 0),
			Color = Color3.fromRGB(58, 52, 44),
			Material = Enum.Material.Brick,
		}, folder)
	end
	for _, off in ipairs({ Vector3.new(-16, 0, -16), Vector3.new(16, 0, -16), Vector3.new(-16, 0, 16), Vector3.new(16, 0, 16) }) do
		local lamp = newPart({
			Name = "Lamp",
			Size = Vector3.new(3, 0.4, 3),
			Position = p + off + Vector3.new(0, height - 0.3, 0),
			Color = COLORS.Lamp,
			Material = Enum.Material.Neon,
			CanCollide = false,
		}, folder)
		local l = Instance.new("PointLight")
		l.Range = 30
		l.Brightness = 1.4
		l.Color = COLORS.Lamp
		l.Parent = lamp
	end

	local spawn = Instance.new("SpawnLocation")
	spawn.Name = "LobbySpawn"
	spawn.Anchored = true
	spawn.Size = Vector3.new(10, 1, 10)
	spawn.Position = p + Vector3.new(0, 0.5, 6)
	spawn.Neutral = true
	spawn.Duration = 0
	spawn.Color = Color3.fromRGB(80, 60, 30)
	spawn.Material = Enum.Material.Slate
	spawn.TopSurface = Enum.SurfaceType.Smooth
	spawn.Parent = folder
	local decal = spawn:FindFirstChildOfClass("Decal")
	if decal then
		decal:Destroy()
	end

	-- табличка с названием и правилами
	local board = newPart({
		Name = "Board",
		Size = Vector3.new(34, 14, 1),
		Position = p + Vector3.new(0, 9, -size / 2 + 1),
		Color = Color3.fromRGB(15, 12, 10),
		Material = Enum.Material.SmoothPlastic,
	}, folder)
	local sg = Instance.new("SurfaceGui")
	sg.Face = Enum.NormalId.Back
	sg.CanvasSize = Vector2.new(1020, 420)
	sg.LightInfluence = 0
	sg.Parent = board
	local title = Instance.new("TextLabel")
	title.BackgroundTransparency = 1
	title.Size = UDim2.new(1, 0, 0, 110)
	title.Font = Enum.Font.GothamBlack
	title.TextScaled = true
	title.TextColor3 = Color3.fromRGB(255, 190, 70)
	title.Text = config.GameName
	title.Parent = sg
	local chapter = Instance.new("TextLabel")
	chapter.BackgroundTransparency = 1
	chapter.Position = UDim2.fromOffset(0, 110)
	chapter.Size = UDim2.new(1, 0, 0, 50)
	chapter.Font = Enum.Font.GothamBold
	chapter.TextScaled = true
	chapter.TextColor3 = Color3.fromRGB(200, 60, 50)
	chapter.Text = config.Chapter
	chapter.Parent = sg
	local rules = Instance.new("TextLabel")
	rules.BackgroundTransparency = 1
	rules.Position = UDim2.fromOffset(30, 175)
	rules.Size = UDim2.new(1, -60, 0, 235)
	rules.Font = Enum.Font.Gotham
	rules.TextWrapped = true
	rules.TextSize = 30
	rules.TextXAlignment = Enum.TextXAlignment.Left
	rules.TextYAlignment = Enum.TextYAlignment.Top
	rules.TextColor3 = Color3.fromRGB(220, 210, 190)
	rules.Text = string.format(
		"• Найдите %d предохранителя и вставьте их в генератор у входа.\n"
			.. "• Запитанный лифт — единственный выход из улья.\n"
			.. "• Рой слышит бег и видит свет фонаря. Прячьтесь в шкафчиках.\n"
			.. "• Держитесь вместе: «связь» с напарником даёт выносливость,\n   а пойманного можно вырвать из кокона.\n"
			.. "F — фонарь · Shift — бег · E — действие",
		config.FusesRequired
	)
	rules.Parent = sg

	folder.Parent = workspace
	return folder, spawn
end

return MapBuilder
