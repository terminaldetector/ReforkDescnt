-- SCK_SCHEMA: ordered field descriptors for the 4 weapon editors.
-- type ∈ {int, float, string, text, bool, enum}
-- Used by SCK_BuildSchemaPanel() (ui_schema.lua).
-- Independent of DRMD — standalone SCK schemas.

SCK_SCHEMA = SCK_SCHEMA or {}

SCK_SCHEMA.weapon = {
	-- section: Identity
	{ key="PrintName",   label="Print Name",    type="string", default="Custom Weapon",      section="Identity" },
	{ key="ClassName",   label="Class Name",    type="string", default="weapon_d6_custom",   section="Identity" },
	{ key="Category",    label="Category",      type="enum",   default="Primary",
	  choices={"Primary","Secondary","Heavy","Utility"}, section="Identity" },
	{ key="WeaponType",  label="Weapon Type",   type="enum",   default="projectile",
	  choices={"projectile","hitscan","beam","deployable"}, section="Identity" },
	{ key="Description", label="Description",   type="text",   default="",                   section="Identity" },
	-- section: Ammo
	{ key="AmmoType",    label="Ammo Type",     type="enum",   default="energy",
	  choices={"energy","bullet","rocket","plasma","none"}, section="Ammo" },
	{ key="MaxAmmo",     label="Max Ammo",      type="int",    min=1,   max=9999, decimals=0, default=100, section="Ammo" },
	-- section: Timing
	{ key="FireRate",    label="Fire Rate (s)", type="float",  min=0.01,max=10,   decimals=3, default=0.5,  section="Timing" },
	{ key="Cooldown",    label="Cooldown (s)",  type="float",  min=0,   max=30,   decimals=2, default=0,    section="Timing" },
	-- section: Combat
	{ key="Damage",      label="Direct Damage", type="float",  min=0,   max=9999, decimals=1, default=50,   section="Combat" },
	{ key="SplashRadius",label="Splash Radius", type="float",  min=0,   max=2000, decimals=0, default=0,    section="Combat" },
	-- section: Energy
	{ key="EnergyCost",  label="Energy / Shot", type="float",  min=0,   max=500,  decimals=1, default=10,   section="Energy" },
	-- section: Misc
	{ key="Weight",      label="Weight",        type="float",  min=0,   max=100,  decimals=1, default=1,    section="Misc" },
	{ key="AIPriority",  label="AI Priority",   type="int",    min=0,   max=10,   decimals=0, default=5,    section="Misc" },
}

SCK_SCHEMA.projectile = {
	-- section: Entity
	{ key="EntityClass",      label="Entity Class",      type="string", default="prop_physics",                          section="Entity" },
	{ key="Model",            label="Model Path",        type="string", default="models/weapons/w_crossbow_bolt.mdl",    section="Entity" },
	{ key="MaterialOverride", label="Material Override", type="string", default="",                                      section="Entity" },
	{ key="Scale",            label="Scale",             type="float",  min=0.01,max=20,    decimals=2, default=1,       section="Entity" },
	{ key="Mass",             label="Mass (kg)",         type="float",  min=0.1, max=5000,  decimals=1, default=5,       section="Entity" },
	-- section: Physics
	{ key="Gravity",          label="Gravity",           type="bool",   default=false,                                   section="Physics" },
	{ key="Drag",             label="Drag",              type="bool",   default=false,                                   section="Physics" },
	{ key="InitialVelocity",  label="Initial Velocity",  type="float",  min=1,   max=50000, decimals=0, default=3000,    section="Physics" },
	{ key="Acceleration",     label="Acceleration",      type="float",  min=-5000,max=50000,decimals=0, default=0,       section="Physics" },
	{ key="MaxVelocity",      label="Max Velocity",      type="float",  min=1,   max=50000, decimals=0, default=5000,    section="Physics" },
	{ key="Lifetime",         label="Lifetime (s)",      type="float",  min=0.1, max=60,    decimals=1, default=5,       section="Physics" },
	-- section: Homing
	{ key="Homing",           label="Homing",            type="bool",   default=false,                                   section="Homing" },
	{ key="TurnRate",         label="Turn Rate",         type="float",  min=0,   max=20,    decimals=2, default=4.5,     section="Homing" },
	-- section: Explosion
	{ key="ExplosionRadius",  label="Explosion Radius",  type="float",  min=0,   max=2000,  decimals=0, default=0,       section="Explosion" },
	{ key="ExplosionDamage",  label="Explosion Damage",  type="float",  min=0,   max=9999,  decimals=1, default=0,       section="Explosion" },
	-- section: FX
	{ key="Trail",            label="Trail",             type="bool",   default=true,                                    section="FX" },
	{ key="Light",            label="Light Emitter",     type="bool",   default=false,                                   section="FX" },
	{ key="ImpactEffect",     label="Impact Effect",     type="string", default="",                                      section="FX" },
	-- section: Penetration
	{ key="Ricochet",         label="Ricochet",          type="bool",   default=false,                                   section="Penetration" },
	{ key="PierceCount",      label="Pierce Count",      type="int",    min=0,   max=10,    decimals=0, default=0,       section="Penetration" },
	-- section: Cluster
	{ key="ClusterCount",     label="Cluster Count",     type="int",    min=0,   max=200,   decimals=0, default=0,       section="Cluster" },
	{ key="ClusterSpread",    label="Cluster Spread °",  type="float",  min=0,   max=90,    decimals=1, default=15,      section="Cluster" },
	{ key="ClusterDelay",     label="Cluster Delay (s)", type="float",  min=0,   max=5,     decimals=2, default=0,       section="Cluster" },
	{ key="SubmunitionEntity",label="Submunition Entity",type="string", default="",                                      section="Cluster" },
	-- section: Spawn (режим огня и точка появления снаряда)
	{ key="FireMode",         label="Fire Mode",         type="enum",   default="single",
	  choices={"single","burst","volley"}, section="Spawn" },
	{ key="BurstCount",       label="Burst Count",       type="int",    min=2,   max=20,    decimals=0, default=3,       section="Spawn" },
	{ key="BurstDelay",       label="Burst Delay (s)",   type="float",  min=0.02,max=1,     decimals=2, default=0.08,    section="Spawn" },
	{ key="VolleyCount",      label="Volley Count",      type="int",    min=2,   max=12,    decimals=0, default=3,       section="Spawn" },
	{ key="VolleySpread",     label="Volley Spread °",   type="float",  min=0,   max=45,    decimals=1, default=6,       section="Spawn" },
	{ key="MuzzleIndex",      label="Muzzle Index (0 = eye)", type="int", min=0, max=8,     decimals=0, default=0,       section="Spawn" },
}

SCK_SCHEMA.flak = {
	-- section: Fragments
	{ key="FragmentCount",    label="Fragment Count",    type="int",    min=1,   max=500,   decimals=0, default=20,      section="Fragments" },
	{ key="SpreadAngle",      label="Spread Angle °",    type="float",  min=0,   max=180,   decimals=1, default=30,      section="Fragments" },
	{ key="Randomness",       label="Randomness",        type="float",  min=0,   max=1,     decimals=2, default=0.3,     section="Fragments" },
	{ key="FragmentType",     label="Fragment Type",     type="enum",   default="kinetic",
	  choices={"kinetic","explosive","energy","exotic"}, section="Fragments" },
	{ key="FragmentSpeed",    label="Fragment Speed",    type="float",  min=100, max=20000, decimals=0, default=2000,    section="Fragments" },
	{ key="FragmentSize",     label="Fragment Size",     type="float",  min=0.1, max=10,    decimals=2, default=1,       section="Fragments" },
	{ key="FragmentDamage",   label="Fragment Damage",   type="float",  min=0,   max=9999,  decimals=1, default=10,      section="Fragments" },
	-- section: Detonation
	{ key="DetonateTime",     label="Detonate Time (s)", type="float",  min=0,   max=30,    decimals=2, default=0,       section="Detonation" },
	{ key="DetonateDistance", label="Detonate Distance", type="float",  min=0,   max=5000,  decimals=0, default=0,       section="Detonation" },
}

SCK_SCHEMA.guidance = {
	-- section: Type
	{ key="GuidanceType",      label="Guidance Type",       type="enum",  default="none",
	  choices={"none","heat","target","beam","lead"}, section="Type" },
	{ key="HeatSeeking",       label="Heat Seeking",         type="bool",  default=false,                                section="Type" },
	{ key="TargetSeeking",     label="Target Seeking",       type="bool",  default=false,                                section="Type" },
	{ key="BeamRiding",        label="Beam Riding",          type="bool",  default=false,                                section="Type" },
	-- section: Tracking
	{ key="LockTime",          label="Lock Time (s)",        type="float", min=0,   max=10,  decimals=2, default=1,      section="Tracking" },
	{ key="TrackingCone",      label="Tracking Cone °",      type="float", min=1,   max=180, decimals=1, default=45,     section="Tracking" },
	{ key="MaxAngularVelocity",label="Max Angular Vel.",      type="float", min=0,   max=360, decimals=1, default=90,     section="Tracking" },
	-- section: Prediction
	{ key="LeadPrediction",    label="Lead Prediction",      type="float", min=0,   max=5,   decimals=2, default=0.8,    section="Prediction" },
	{ key="PredictionStrength",label="Prediction Strength",  type="float", min=0,   max=1,   decimals=2, default=0.5,    section="Prediction" },
	-- section: Memory
	{ key="TargetMemory",      label="Target Memory (s)",    type="float", min=0,   max=30,  decimals=1, default=5,      section="Memory" },
	{ key="LostTargetTimeout", label="Lost Target (s)",      type="float", min=0,   max=30,  decimals=1, default=3,      section="Memory" },
}
