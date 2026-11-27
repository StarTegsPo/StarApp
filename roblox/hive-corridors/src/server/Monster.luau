-- «Рой» — существо из тысяч насекомых. Блуждает по улью, слышит бег,
-- видит свет фонарей и гонится по прямой, если заметил игрока.

local Monster = {}
Monster.__index = Monster

local function buildModel(config, cf)
	local model = Instance.new("Model")
	model.Name = "HiveMonster"

	local core = Instance.new("Part")
	core.Name = "Core"
	core.Shape = Enum.PartType.Ball
	core.Size = Vector3.new(4.5, 4.5, 4.5)
	core.Color = Color3.fromRGB(12, 10, 8)
	core.Material = Enum.Material.Slate
	core.Transparency = 0.25
	core.Anchored = true
	core.CanCollide = false
	core.CanQuery = false
	core.CanTouch = false
	core.CastShadow = false
	core.CFrame = cf
	core.Parent = model
	model.PrimaryPart = core

	-- глаза: несколько красных точек в разных местах «облака»
	local eyeOffsets = {
		Vector3.new(-0.7, 0.6, -2.1),
		Vector3.new(0.7, 0.6, -2.1),
		Vector3.new(-1.4, -0.2, -1.7),
		Vector3.new(1.3, 1.3, -1.6),
		Vector3.new(0, -0.9, -2.0),
	}
	for i, off in ipairs(eyeOffsets) do
		local eye = Instance.new("Part")
		eye.Name = "Eye" .. i
		eye.Shape = Enum.PartType.Ball
		local s = i <= 2 and 0.55 or 0.3
		eye.Size = Vector3.new(s, s, s)
		eye.Color = Color3.fromRGB(255, 30, 20)
		eye.Material = Enum.Material.Neon
		eye.Anchored = true
		eye.CanCollide = false
		eye.CanQuery = false
		eye.CanTouch = false
		eye.CFrame = cf * CFrame.new(off)
		eye.Parent = model
	end

	-- рой насекомых
	local swarm = Instance.new("ParticleEmitter")
	swarm.Name = "Swarm"
	swarm.Rate = 160
	swarm.Lifetime = NumberRange.new(0.6, 1.6)
	swarm.Speed = NumberRange.new(2, 9)
	swarm.SpreadAngle = Vector2.new(180, 180)
	swarm.Drag = 2
	swarm.RotSpeed = NumberRange.new(-360, 360)
	swarm.Rotation = NumberRange.new(0, 360)
	swarm.Size = NumberSequence.new({
		NumberSequenceKeypoint.new(0, 0.35),
		NumberSequenceKeypoint.new(1, 0.1),
	})
	swarm.Color = ColorSequence.new(Color3.fromRGB(20, 16, 10), Color3.fromRGB(70, 50, 20))
	swarm.LightEmission = 0
	swarm.Transparency = NumberSequence.new({
		NumberSequenceKeypoint.new(0, 0),
		NumberSequenceKeypoint.new(0.8, 0.2),
		NumberSequenceKeypoint.new(1, 1),
	})
	swarm.Acceleration = Vector3.new(0, 1, 0)
	swarm.Parent = core

	local trail = Instance.new("ParticleEmitter")
	trail.Name = "Haze"
	trail.Rate = 25
	trail.Lifetime = NumberRange.new(1.5, 2.5)
	trail.Speed = NumberRange.new(0.5, 1.5)
	trail.SpreadAngle = Vector2.new(180, 180)
	trail.Size = NumberSequence.new({
		NumberSequenceKeypoint.new(0, 2.5),
		NumberSequenceKeypoint.new(1, 5),
	})
	trail.Color = ColorSequence.new(Color3.fromRGB(10, 8, 6))
	trail.Transparency = NumberSequence.new({
		NumberSequenceKeypoint.new(0, 0.6),
		NumberSequenceKeypoint.new(1, 1),
	})
	trail.Parent = core

	local glow = Instance.new("PointLight")
	glow.Name = "Glow"
	glow.Range = 12
	glow.Brightness = 1.4
	glow.Color = Color3.fromRGB(255, 40, 30)
	glow.Parent = core

	if config.Sounds.Buzz ~= "" then
		local buzz = Instance.new("Sound")
		buzz.Name = "Buzz"
		buzz.SoundId = config.Sounds.Buzz
		buzz.Looped = true
		buzz.Volume = 0.8
		buzz.RollOffMaxDistance = 90
		buzz.RollOffMinDistance = 8
		buzz.Parent = core
		buzz:Play()
	end

	return model
end

-- hooks:
--   GetTargets() -> {Player} — живые игроки в раунде, не спрятанные и не в коконе
--   OnCatch(player)
--   OnSpotted(player)
function Monster.new(map, config, rng, hooks, parent)
	local self = setmetatable({}, Monster)
	self.Map = map
	self.Config = config
	self.Cfg = config.Monster
	self.Rng = rng
	self.Hooks = hooks
	self.Rage = 0
	self.PowerOn = false
	self.State = "Wander"
	self.Path = {}
	self.PathIndex = 1
	self.Target = nil
	self.LastSeen = 0
	self.LastKnown = nil
	self.Paused = 0
	self.Time = 0
	self.Alive = true

	local sx, sz = map.MonsterSpawnCell[1], map.MonsterSpawnCell[2]
	local spawnPos = map:CellToWorld(sx, sz, self.Cfg.HoverHeight)
	self.Model = buildModel(config, CFrame.new(spawnPos))
	self.Model:SetAttribute("State", self.State)
	self.Model.Parent = parent

	self.RayParams = RaycastParams.new()
	self.RayParams.FilterType = Enum.RaycastFilterType.Include
	self.RayParams.FilterDescendantsInstances = { map.Geometry }

	return self
end

function Monster:GetPosition()
	return self.Model:GetPivot().Position
end

function Monster:SetRage(fusesInserted)
	self.Rage = fusesInserted
end

function Monster:SetPowerOn(on)
	self.PowerOn = on
end

function Monster:_setState(state)
	if self.State ~= state then
		self.State = state
		self.Model:SetAttribute("State", state)
	end
end

function Monster:_speed()
	local cfg = self.Cfg
	local bonus = self.Rage * cfg.SpeedPerFuse + (self.PowerOn and cfg.PowerOnBonus or 0)
	if self.State == "Chase" then
		return cfg.ChaseSpeed + bonus
	elseif self.State == "Investigate" then
		return cfg.InvestigateSpeed + bonus * 0.7
	end
	return cfg.WanderSpeed + bonus * 0.5
end

function Monster:HasLineOfSight(from, to)
	local dir = to - from
	local hit = workspace:Raycast(from, dir, self.RayParams)
	return hit == nil
end

function Monster:_setPathTo(worldPos)
	local pos = self:GetPosition()
	local cx, cz = self.Map:WorldToCell(pos)
	local tx, tz = self.Map:WorldToCell(worldPos)
	local cells = self.Map:FindPath(cx, cz, tx, tz)
	local path = {}
	for _, c in ipairs(cells) do
		table.insert(path, self.Map:CellToWorld(c[1], c[2], 0))
	end
	-- последняя точка — точное место (внутри последней клетки, без стен)
	table.insert(path, worldPos)
	self.Path = path
	self.PathIndex = 1
	self.PathGoalCell = tx * 1000 + tz
end

-- true, когда точка достигнута
function Monster:_moveToward(goal, dt)
	local pos = self:GetPosition()
	local hover = self.Map.Origin.Y + self.Cfg.HoverHeight + math.sin(self.Time * 2.7) * 0.6
	local flatGoal = Vector3.new(goal.X, hover, goal.Z)
	local delta = Vector3.new(flatGoal.X - pos.X, 0, flatGoal.Z - pos.Z)
	local dist = delta.Magnitude
	local newPos
	if dist < 0.05 then
		newPos = Vector3.new(pos.X, hover, pos.Z)
	else
		local step = math.min(dist, self:_speed() * dt)
		newPos = Vector3.new(pos.X, hover, pos.Z) + delta.Unit * step
		dist -= step
	end
	local look = delta.Magnitude > 0.05 and delta.Unit or self.Model:GetPivot().LookVector
	local wobble = CFrame.Angles(math.sin(self.Time * 5) * 0.08, 0, math.cos(self.Time * 4) * 0.08)
	self.Model:PivotTo(CFrame.lookAt(newPos, newPos + look) * wobble)
	return dist < 0.8
end

function Monster:_followPath(dt)
	local wp = self.Path[self.PathIndex]
	if not wp then
		return true
	end
	if self:_moveToward(wp, dt) then
		self.PathIndex += 1
	end
	return self.PathIndex > #self.Path
end

local function rootOf(plr)
	local char = plr.Character
	return char and char:FindFirstChild("HumanoidRootPart")
end

function Monster:_chooseWanderGoal(targets)
	local rng = self.Rng
	if #targets > 0 and rng:NextNumber() < self.Cfg.HuntChance then
		local root = rootOf(targets[rng:NextInteger(1, #targets)])
		if root then
			self:_setPathTo(root.Position)
			return
		end
	end
	local x, z = self.Map:RandomCell(rng)
	self:_setPathTo(self.Map:CellToWorld(x, z, 0))
end

function Monster:Step(dt)
	if not self.Alive then
		return
	end
	self.Time += dt
	if self.Paused > 0 then
		self.Paused -= dt
		self:_moveToward(self:GetPosition(), dt) -- просто парит на месте
		return
	end

	local cfg = self.Cfg
	local pos = self:GetPosition()
	local targets = self.Hooks.GetTargets()

	-- 1. ловля: кто-то вплотную
	for _, plr in ipairs(targets) do
		local root = rootOf(plr)
		if root then
			local flat = Vector3.new(root.Position.X - pos.X, 0, root.Position.Z - pos.Z).Magnitude
			if flat < cfg.CatchDistance and math.abs(root.Position.Y - pos.Y) < 8 then
				self.Hooks.OnCatch(plr)
				self.Paused = 3
				self.Target = nil
				self.Path = {}
				self:_setState("Wander")
				return
			end
		end
	end

	-- 2. зрение
	local seen, seenRoot, seenDist = nil, nil, math.huge
	for _, plr in ipairs(targets) do
		local root = rootOf(plr)
		if root then
			local d = (root.Position - pos).Magnitude
			local range = cfg.SightRange + (plr:GetAttribute("FlashlightOn") and cfg.FlashlightSightBonus or 0)
			if d < range and d < seenDist and self:HasLineOfSight(pos, root.Position) then
				seen, seenRoot, seenDist = plr, root, d
			end
		end
	end

	if seen then
		if self.Target ~= seen or self.State ~= "Chase" then
			self.Hooks.OnSpotted(seen)
		end
		self.Target = seen
		self.LastSeen = self.Time
		self.LastKnown = seenRoot.Position
		self:_setState("Chase")
		self.Path = {}
		self:_moveToward(seenRoot.Position, dt)
		return
	end

	-- 3. погоня по последнему известному месту
	if self.State == "Chase" then
		local stillValid = self.Target and table.find(targets, self.Target) ~= nil
		if not stillValid or self.Time - self.LastSeen > cfg.LoseTargetTime then
			self.Target = nil
			self.Path = {}
			self:_setState("Wander")
			self.Paused = 1.2 -- «принюхивается»
			return
		end
		if #self.Path == 0 then
			self:_setPathTo(self.LastKnown)
		end
		if self:_followPath(dt) then
			-- дошёл, никого: продолжим идти в сторону, куда убежал игрок
			local root = rootOf(self.Target)
			if root then
				self:_setPathTo(root.Position)
			end
		end
		return
	end

	-- 4. слух: бегущие игроки
	for _, plr in ipairs(targets) do
		local root = rootOf(plr)
		if root and plr:GetAttribute("Sprinting") and (root.Position - pos).Magnitude < cfg.HearSprintRange then
			local tx, tz = self.Map:WorldToCell(root.Position)
			if self.State ~= "Investigate" or self.PathGoalCell ~= tx * 1000 + tz then
				self:_setPathTo(root.Position)
			end
			self:_setState("Investigate")
			break
		end
	end

	-- 5. движение
	if self.State == "Investigate" then
		if self:_followPath(dt) then
			self:_setState("Wander")
			self.Path = {}
			self.Paused = 1.5
		end
		return
	end

	if #self.Path == 0 or self.PathIndex > #self.Path then
		self:_chooseWanderGoal(targets)
	end
	self:_followPath(dt)
end

function Monster:Destroy()
	self.Alive = false
	if self.Model then
		self.Model:Destroy()
	end
end

return Monster
