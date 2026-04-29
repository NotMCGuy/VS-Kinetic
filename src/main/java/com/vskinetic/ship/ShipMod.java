package com.vskinetic.ship;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.vskinetic.Config;
import com.vskinetic.KineticMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ShipMod {

    private ShipMod() {}





    public static final class Naming {
        public static final String CRASH_SUFFIX = "-crashed";

        private static final String[] ADJECTIVES = {
                "iron", "swift", "amber", "onyx", "lunar", "storm", "frost", "hollow"
        };
        private static final String[] NOUNS = {
                "falcon", "comet", "anchor", "spire", "wisp", "raven", "harbor", "atlas"
        };

        private Naming() {}

        public static String codename(long shipId) {
            int a = Math.floorMod(Long.hashCode(shipId), ADJECTIVES.length);
            int n = Math.floorMod((int) (shipId * 31L + 17L), NOUNS.length);
            return ADJECTIVES[a] + " " + NOUNS[n];
        }

        public static String defaultDisplayName(long shipId) {
            return "Mayday " + codename(shipId);
        }

        public static String slugify(String name) {
            String cleaned = name.toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("^-+|-+$", "")
                    .replaceAll("-{2,}", "-");
            return cleaned.isBlank() ? "ship" : cleaned;
        }

        public static String withCrashSuffix(String slug) {
            return slug.endsWith(CRASH_SUFFIX) ? slug : slug + CRASH_SUFFIX;
        }

        public static String withoutCrashSuffix(String slug) {
            return slug.endsWith(CRASH_SUFFIX)
                    ? slug.substring(0, slug.length() - CRASH_SUFFIX.length())
                    : slug;
        }
    }





    public static final class BindingRecord {

        private static final String KEY_SHIP_ID             = "ShipId";
        private static final String KEY_OWNER               = "Owner";
        private static final String KEY_HAS_OWNER           = "HasOwner";
        private static final String KEY_CREATED_BY          = "CreatedBy";
        private static final String KEY_HAS_CREATED_BY      = "HasCreatedBy";
        private static final String KEY_DISPLAY_NAME        = "DisplayName";
        private static final String KEY_SLUG                = "Slug";
        private static final String KEY_CRASHED             = "Crashed";
        private static final String KEY_CRASH_COUNT         = "CrashCount";
        private static final String KEY_STRUCTURAL_INTEGRITY= "StructuralIntegrity";
        private static final String KEY_LAST_IMPACT_ENERGY  = "LastImpactEnergy";
        private static final String KEY_PEAK_IMPACT_ENERGY  = "PeakImpactEnergy";
        private static final String KEY_LAST_CRASH_TICK     = "LastCrashTick";
        private static final String KEY_LAST_CRASH_SEVERITY = "LastCrashSeverity";
        private static final String KEY_LAST_IMPACT_PART    = "LastImpactPart";
        private static final String KEY_ENGINE_HEALTH       = "EngineHealth";
        private static final String KEY_LIFT_HEALTH         = "LiftHealth";
        private static final String KEY_CONTROL_HEALTH      = "ControlHealth";
        private static final String KEY_HULL_HEALTH         = "HullHealth";

        private final long shipId;
        private UUID owner;
        private UUID createdBy;
        private String displayName;
        private String slug;
        private boolean crashed;
        private int crashCount;
        private double structuralIntegrity;
        private double lastImpactEnergy;
        private double peakImpactEnergy;
        private long lastCrashTick;
        private String lastCrashSeverity;
        private String lastImpactPart;
        private double engineHealth;
        private double liftHealth;
        private double controlHealth;
        private double hullHealth;

        public BindingRecord(
                long shipId, UUID owner, UUID createdBy,
                String displayName, String slug,
                boolean crashed, int crashCount,
                double structuralIntegrity,
                double lastImpactEnergy, double peakImpactEnergy,
                long lastCrashTick, String lastCrashSeverity, String lastImpactPart,
                double engineHealth, double liftHealth, double controlHealth, double hullHealth
        ) {
            this.shipId             = shipId;
            this.owner              = owner;
            this.createdBy          = createdBy;
            this.displayName        = displayName;
            this.slug               = slug;
            this.crashed            = crashed;
            this.crashCount         = crashCount;
            this.structuralIntegrity= clampIntegrity(structuralIntegrity);
            this.lastImpactEnergy   = Math.max(0.0D, lastImpactEnergy);
            this.peakImpactEnergy   = Math.max(0.0D, peakImpactEnergy);
            this.lastCrashTick      = lastCrashTick;
            this.lastCrashSeverity  = sanitizeSeverity(lastCrashSeverity);
            this.lastImpactPart     = sanitizeImpactPart(lastImpactPart);
            this.engineHealth       = clampHealth(engineHealth);
            this.liftHealth         = clampHealth(liftHealth);
            this.controlHealth      = clampHealth(controlHealth);
            this.hullHealth         = clampHealth(hullHealth);
        }

        public static BindingRecord createDefault(long shipId) {
            String displayName = Naming.defaultDisplayName(shipId);
            String slug = Naming.slugify(displayName);
            return new BindingRecord(
                    shipId, null, null, displayName, slug,
                    false, 0, 100.0D, 0.0D, 0.0D, -1L,
                    "none", "auto",
                    100.0D, 100.0D, 100.0D, 100.0D
            );
        }

        public static BindingRecord fromTag(CompoundTag tag) {
            long shipId = tag.getLong(KEY_SHIP_ID);
            UUID owner      = tag.getBoolean(KEY_HAS_OWNER)      ? tag.getUUID(KEY_OWNER)      : null;
            UUID createdBy  = tag.getBoolean(KEY_HAS_CREATED_BY) ? tag.getUUID(KEY_CREATED_BY) : null;
            String displayName = tag.getString(KEY_DISPLAY_NAME);
            String slug        = tag.getString(KEY_SLUG);
            boolean crashed    = tag.getBoolean(KEY_CRASHED);
            int crashCount     = tag.getInt(KEY_CRASH_COUNT);
            double structuralIntegrity = tag.contains(KEY_STRUCTURAL_INTEGRITY) ? tag.getDouble(KEY_STRUCTURAL_INTEGRITY) : 100.0D;
            double lastImpactEnergy    = tag.getDouble(KEY_LAST_IMPACT_ENERGY);
            double peakImpactEnergy    = tag.getDouble(KEY_PEAK_IMPACT_ENERGY);
            long lastCrashTick         = tag.contains(KEY_LAST_CRASH_TICK) ? tag.getLong(KEY_LAST_CRASH_TICK) : -1L;
            String lastCrashSeverity   = tag.getString(KEY_LAST_CRASH_SEVERITY);
            String lastImpactPart      = tag.getString(KEY_LAST_IMPACT_PART);
            double engineHealth  = tag.contains(KEY_ENGINE_HEALTH)  ? tag.getDouble(KEY_ENGINE_HEALTH)  : 100.0D;
            double liftHealth    = tag.contains(KEY_LIFT_HEALTH)    ? tag.getDouble(KEY_LIFT_HEALTH)    : 100.0D;
            double controlHealth = tag.contains(KEY_CONTROL_HEALTH) ? tag.getDouble(KEY_CONTROL_HEALTH) : 100.0D;
            double hullHealth    = tag.contains(KEY_HULL_HEALTH)    ? tag.getDouble(KEY_HULL_HEALTH)    : 100.0D;

            if (displayName.isBlank()) displayName = Naming.defaultDisplayName(shipId);
            if (slug.isBlank())        slug        = Naming.slugify(displayName);
            if (crashed)               slug        = Naming.withCrashSuffix(Naming.withoutCrashSuffix(slug));

            return new BindingRecord(
                    shipId, owner, createdBy, displayName, slug,
                    crashed, crashCount, structuralIntegrity,
                    lastImpactEnergy, peakImpactEnergy,
                    lastCrashTick, lastCrashSeverity, lastImpactPart,
                    engineHealth, liftHealth, controlHealth, hullHealth
            );
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong(KEY_SHIP_ID, shipId);
            if (owner != null) {
                tag.putBoolean(KEY_HAS_OWNER, true);
                tag.putUUID(KEY_OWNER, owner);
            } else {
                tag.putBoolean(KEY_HAS_OWNER, false);
            }
            if (createdBy != null) {
                tag.putBoolean(KEY_HAS_CREATED_BY, true);
                tag.putUUID(KEY_CREATED_BY, createdBy);
            } else {
                tag.putBoolean(KEY_HAS_CREATED_BY, false);
            }
            tag.putString(KEY_DISPLAY_NAME, displayName);
            tag.putString(KEY_SLUG, slug);
            tag.putBoolean(KEY_CRASHED, crashed);
            tag.putInt(KEY_CRASH_COUNT, crashCount);
            tag.putDouble(KEY_STRUCTURAL_INTEGRITY, structuralIntegrity);
            tag.putDouble(KEY_LAST_IMPACT_ENERGY, lastImpactEnergy);
            tag.putDouble(KEY_PEAK_IMPACT_ENERGY, peakImpactEnergy);
            tag.putLong(KEY_LAST_CRASH_TICK, lastCrashTick);
            tag.putString(KEY_LAST_CRASH_SEVERITY, lastCrashSeverity);
            tag.putString(KEY_LAST_IMPACT_PART, lastImpactPart);
            tag.putDouble(KEY_ENGINE_HEALTH, engineHealth);
            tag.putDouble(KEY_LIFT_HEALTH, liftHealth);
            tag.putDouble(KEY_CONTROL_HEALTH, controlHealth);
            tag.putDouble(KEY_HULL_HEALTH, hullHealth);
            return tag;
        }



        public long shipId()               { return shipId; }
        public UUID owner()                { return owner; }
        public UUID createdBy()            { return createdBy; }
        public String displayName()        { return displayName; }
        public String slug()               { return slug; }
        public boolean crashed()           { return crashed; }
        public int crashCount()            { return crashCount; }
        public double structuralIntegrity(){ return structuralIntegrity; }
        public double lastImpactEnergy()   { return lastImpactEnergy; }
        public double peakImpactEnergy()   { return peakImpactEnergy; }
        public long lastCrashTick()        { return lastCrashTick; }
        public String lastCrashSeverity()  { return lastCrashSeverity; }
        public String lastImpactPart()     { return lastImpactPart; }
        public double engineHealth()       { return engineHealth; }
        public double liftHealth()         { return liftHealth; }
        public double controlHealth()      { return controlHealth; }
        public double hullHealth()         { return hullHealth; }



        public void setOwner(UUID owner) { this.owner = owner; }
        public void setCreator(UUID creator) { this.createdBy = creator; }

        public boolean setCreatorIfMissing(UUID creator) {
            if (this.createdBy != null || creator == null) return false;
            this.createdBy = creator;
            return true;
        }

        public void rename(String newDisplayName) {
            this.displayName = newDisplayName;
            String baseSlug = Naming.slugify(newDisplayName);
            this.slug = crashed ? Naming.withCrashSuffix(baseSlug) : baseSlug;
        }

        public void setCrashed(boolean crashed) {
            this.crashed = crashed;
            String baseSlug = Naming.withoutCrashSuffix(slug);
            this.slug = crashed ? Naming.withCrashSuffix(baseSlug) : baseSlug;
        }

        public int failedPartCount() {
            int n = 0;
            if (isSubsystemFailed(engineHealth))  n++;
            if (isSubsystemFailed(liftHealth))    n++;
            if (isSubsystemFailed(controlHealth)) n++;
            if (isSubsystemFailed(hullHealth))    n++;
            return n;
        }

        public String failedPartsSummary() {
            StringBuilder sb = new StringBuilder();
            appendFailedPart(sb, "engine",  engineHealth);
            appendFailedPart(sb, "lift",    liftHealth);
            appendFailedPart(sb, "control", controlHealth);
            appendFailedPart(sb, "hull",    hullHealth);
            return sb.length() == 0 ? "none" : sb.toString();
        }

        public void applyCrash(CrashPhysicsEngine.CrashResult result, long gameTime) {
            applyCrash(result, gameTime, CrashPhysicsEngine.ImpactPart.AUTO);
        }

        public void applyCrash(CrashPhysicsEngine.CrashResult result, long gameTime, CrashPhysicsEngine.ImpactPart impactPart) {
            this.crashed           = true;
            this.crashCount++;
            this.lastImpactEnergy  = Math.max(0.0D, result.impactEnergy());
            this.peakImpactEnergy  = Math.max(this.peakImpactEnergy, this.lastImpactEnergy);
            this.lastCrashTick     = gameTime;
            this.lastCrashSeverity = sanitizeSeverity(result.severity().name());
            this.lastImpactPart    = sanitizeImpactPart(impactPart.name());
            this.structuralIntegrity = clampIntegrity(this.structuralIntegrity - Math.max(0.0D, result.damage()));
            applySubsystemDamage(result, gameTime, impactPart);
            this.slug = Naming.withCrashSuffix(Naming.withoutCrashSuffix(slug));
        }

        public void recover(double minimumIntegrity, double partRecoveryFloor) {
            setCrashed(false);
            this.structuralIntegrity = Math.max(this.structuralIntegrity, clampIntegrity(minimumIntegrity));
            double floor = clampHealth(partRecoveryFloor);
            this.engineHealth  = Math.max(this.engineHealth,  floor);
            this.liftHealth    = Math.max(this.liftHealth,    floor);
            this.controlHealth = Math.max(this.controlHealth, floor);
            this.hullHealth    = Math.max(this.hullHealth,    floor);
        }

        private void applySubsystemDamage(
                CrashPhysicsEngine.CrashResult result,
                long gameTime,
                CrashPhysicsEngine.ImpactPart impactPart
        ) {
            double severityScale = switch (result.severity()) {
                case SCRAPE -> 0.45D;
                case HARD   -> 1.0D;
                case CATASTROPHIC -> 1.65D;
                case NONE   -> 0.0D;
            };
            double baseDamage = result.damage() * severityScale * Math.max(0.0D, Config.partDamageMultiplier);
            if (baseDamage <= 0.0D) return;

            Random random = new Random(shipId ^ (gameTime * 31L) ^ (long) (result.crashScore() * 1000.0D) ^ crashCount);
            boolean catastrophic = result.severity() == CrashPhysicsEngine.CrashSeverity.CATASTROPHIC;
            CrashPhysicsEngine.ImpactPart resolved = (impactPart == null || impactPart == CrashPhysicsEngine.ImpactPart.AUTO)
                    ? CrashPhysicsEngine.ImpactPart.HULL : impactPart;

            double primaryShare = Math.max(0.0D, Math.min(1.0D, Config.primaryCrumpleShare));
            double spillShare   = 1.0D - primaryShare;
            double primaryDamage = baseDamage * primaryShare;
            double spillDamage   = baseDamage * spillShare;

            switch (resolved) {
                case ENGINE -> {
                    engineHealth  = applyPartDamage(engineHealth,  primaryDamage,          catastrophic, random, true);
                    controlHealth = applyPartDamage(controlHealth, spillDamage * 0.55D,    catastrophic, random, false);
                    hullHealth    = applyPartDamage(hullHealth,    spillDamage * 0.45D,    catastrophic, random, false);
                }
                case LIFT -> {
                    liftHealth    = applyPartDamage(liftHealth,    primaryDamage,          catastrophic, random, true);
                    hullHealth    = applyPartDamage(hullHealth,    spillDamage * 0.60D,    catastrophic, random, false);
                    controlHealth = applyPartDamage(controlHealth, spillDamage * 0.40D,    catastrophic, random, false);
                }
                case CONTROL -> {
                    controlHealth = applyPartDamage(controlHealth, primaryDamage,          catastrophic, random, true);
                    engineHealth  = applyPartDamage(engineHealth,  spillDamage * 0.50D,    catastrophic, random, false);
                    liftHealth    = applyPartDamage(liftHealth,    spillDamage * 0.50D,    catastrophic, random, false);
                }
                case HULL, AUTO -> {
                    hullHealth    = applyPartDamage(hullHealth,    primaryDamage,          catastrophic, random, true);
                    engineHealth  = applyPartDamage(engineHealth,  spillDamage * 0.34D,    catastrophic, random, false);
                    liftHealth    = applyPartDamage(liftHealth,    spillDamage * 0.33D,    catastrophic, random, false);
                    controlHealth = applyPartDamage(controlHealth, spillDamage * 0.33D,    catastrophic, random, false);
                }
            }
        }

        private static double applyPartDamage(
                double current, double scaled, boolean catastrophic, Random random, boolean primary
        ) {
            double variance = 0.70D + random.nextDouble() * 0.80D;
            double next = clampHealth(current - (scaled * variance));
            if (catastrophic && random.nextDouble() < Config.catastrophicPartFailureChance) {
                double bonus = primary ? 15.0D : 0.0D;
                double forcedFloor = Math.max(
                        0.0D,
                        Config.partFailureHealthThreshold - (5.0D + bonus + random.nextDouble() * 20.0D)
                );
                next = Math.min(next, forcedFloor);
            }
            return next;
        }

        private static String sanitizeSeverity(String value) {
            return (value == null || value.isBlank()) ? "none" : value.toLowerCase(Locale.ROOT);
        }

        private static String sanitizeImpactPart(String value) {
            return (value == null || value.isBlank()) ? "auto" : value.toLowerCase(Locale.ROOT);
        }

        private static double clampIntegrity(double v) { return Math.max(0.0D, Math.min(100.0D, v)); }
        private static double clampHealth(double v)    { return Math.max(0.0D, Math.min(100.0D, v)); }

        private static boolean isSubsystemFailed(double health) {
            return health <= Config.partFailureHealthThreshold;
        }

        private static void appendFailedPart(StringBuilder sb, String name, double health) {
            if (!isSubsystemFailed(health)) return;
            if (sb.length() > 0) sb.append(',');
            sb.append(name);
        }
    }





    public static final class Registry extends SavedData {
        private static final String DATA_NAME  = KineticMod.MODID + "_ship_registry";
        private static final String KEY_RECORDS = "Records";

        private final Map<Long, BindingRecord> records = new HashMap<>();

        public static Registry get(MinecraftServer server) {
            return server.overworld().getDataStorage()
                    .computeIfAbsent(Registry::load, Registry::new, DATA_NAME);
        }

        private static Registry load(CompoundTag root) {
            Registry data = new Registry();
            ListTag list = root.getList(KEY_RECORDS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                BindingRecord record = BindingRecord.fromTag(list.getCompound(i));
                data.records.put(record.shipId(), record);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag root) {
            ListTag list = new ListTag();
            for (BindingRecord record : records.values()) list.add(record.toTag());
            root.put(KEY_RECORDS, list);
            return root;
        }

        public BindingRecord getOrCreate(long shipId) {
            return records.computeIfAbsent(shipId, id -> BindingRecord.createDefault(shipId));
        }

        public BindingRecord pairShip(long shipId, UUID playerId) {
            BindingRecord record = getOrCreate(shipId);
            record.setOwner(playerId);
            record.setCreatorIfMissing(playerId);
            setDirty();
            return record;
        }

        public BindingRecord setCreator(long shipId, UUID playerId) {
            BindingRecord record = getOrCreate(shipId);
            record.setCreator(playerId);
            setDirty();
            return record;
        }

        public BindingRecord setCreatorIfMissing(long shipId, UUID playerId) {
            BindingRecord record = getOrCreate(shipId);
            if (record.setCreatorIfMissing(playerId)) setDirty();
            return record;
        }

        public BindingRecord renameShip(long shipId, String displayName) {
            BindingRecord record = getOrCreate(shipId);
            record.rename(displayName);
            setDirty();
            return record;
        }

        public BindingRecord markCrashed(long shipId) {
            BindingRecord record = getOrCreate(shipId);
            record.setCrashed(true);
            setDirty();
            return record;
        }

        public BindingRecord applyCrash(long shipId, long gameTime, CrashPhysicsEngine.CrashResult result) {
            return applyCrash(shipId, gameTime, result, CrashPhysicsEngine.ImpactPart.AUTO);
        }

        public BindingRecord applyCrash(
                long shipId, long gameTime,
                CrashPhysicsEngine.CrashResult result,
                CrashPhysicsEngine.ImpactPart impactPart
        ) {
            BindingRecord record = getOrCreate(shipId);
            record.applyCrash(result, gameTime, impactPart);
            setDirty();
            return record;
        }

        public BindingRecord recoverShip(long shipId) {
            BindingRecord record = getOrCreate(shipId);
            record.recover(Config.recoveryIntegrityFloor, Config.partRecoveryFloor);
            setDirty();
            return record;
        }

        public BindingRecord getShip(long shipId) { return records.get(shipId); }
        public Collection<BindingRecord> allShips() { return records.values(); }
    }





    public static final class Hooks {
        private static final CrashPhysicsEngine PHYSICS = new CrashPhysicsEngine();

        private Hooks() {}

        public record PhysicsSampleOutcome(BindingRecord record, CrashPhysicsEngine.CrashResult result) {}

        public static BindingRecord onShipCreated(MinecraftServer server, long shipId, UUID creatorId) {
            Registry data = Registry.get(server);
            BindingRecord record = data.getOrCreate(shipId);
            if (creatorId != null) data.setCreatorIfMissing(shipId, creatorId);
            return record;
        }

        public static BindingRecord onShipPhysicsSample(
                MinecraftServer server, long shipId, long gameTime,
                Vec3 linearVelocity, double estimatedMass, boolean hadCollisionSignal
        ) {
            return onShipPhysicsSampleDetailed(
                    server, shipId, gameTime, linearVelocity, estimatedMass, hadCollisionSignal,
                    CrashPhysicsEngine.ImpactPart.AUTO
            ).record();
        }

        public static PhysicsSampleOutcome onShipPhysicsSampleDetailed(
                MinecraftServer server, long shipId, long gameTime,
                Vec3 linearVelocity, double estimatedMass, boolean hadCollisionSignal
        ) {
            return onShipPhysicsSampleDetailed(
                    server, shipId, gameTime, linearVelocity, estimatedMass, hadCollisionSignal,
                    CrashPhysicsEngine.ImpactPart.AUTO
            );
        }

        public static PhysicsSampleOutcome onShipPhysicsSampleDetailed(
                MinecraftServer server, long shipId, long gameTime,
                Vec3 linearVelocity, double estimatedMass, boolean hadCollisionSignal,
                CrashPhysicsEngine.ImpactPart impactPart
        ) {
            CrashPhysicsEngine.CrashResult result = PHYSICS.sample(
                    shipId, gameTime, linearVelocity, estimatedMass, hadCollisionSignal);

            if (!result.crash()) {
                return new PhysicsSampleOutcome(Registry.get(server).getOrCreate(shipId), result);
            }
            BindingRecord record = Registry.get(server).applyCrash(shipId, gameTime, result, impactPart);
            return new PhysicsSampleOutcome(record, result);
        }

        public static BindingRecord onShipCrash(MinecraftServer server, long shipId) {
            return Registry.get(server).markCrashed(shipId);
        }

        public static BindingRecord onShipRecovered(MinecraftServer server, long shipId) {
            PHYSICS.clearShip(shipId);
            return Registry.get(server).recoverShip(shipId);
        }
    }





    public static final class CollisionSignals {
        private static final long TERRAIN_SENTINEL_A = -1L;
        private static final long TERRAIN_SENTINEL_B = Long.MIN_VALUE;

        private static final ConcurrentLinkedQueue<PendingCollision> PENDING = new ConcurrentLinkedQueue<>();
        private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

        private CollisionSignals() {}

        public static void registerVsCollisionEvents() {
            if (!REGISTERED.compareAndSet(false, true)) return;
            Object vsCore = getVsCore();
            if (vsCore == null) return;
            registerEvent(vsCore, "getCollisionStartEvent");
            registerEvent(vsCore, "getCollisionPersistEvent");
        }

        public static Set<Long> drainSignalsForDimension(String dimensionId) {
            Set<Long> signaled = new HashSet<>();
            List<PendingCollision> deferred = new ArrayList<>();

            PendingCollision collision;
            while ((collision = PENDING.poll()) != null) {
                if (collision.dimensionId != null && !collision.dimensionId.equals(dimensionId)) {
                    deferred.add(collision);
                    continue;
                }
                signaled.add(collision.shipIdA);
                if (isRealShipId(collision.shipIdB)) signaled.add(collision.shipIdB);
            }
            for (PendingCollision pending : deferred) PENDING.offer(pending);
            return signaled;
        }

        private static boolean isRealShipId(long shipId) {
            return shipId > 0L && shipId != TERRAIN_SENTINEL_A && shipId != TERRAIN_SENTINEL_B;
        }

        private static Object getVsCore() {
            try {
                Field field = ValkyrienSkiesMod.class.getField("vsCore");
                return field.get(null);
            } catch (ReflectiveOperationException ignored) {}
            try {
                Method method = ValkyrienSkiesMod.class.getMethod("getVsCore");
                return method.invoke(null);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private static void registerEvent(Object vsCore, String accessorName) {
            try {
                Method accessor = vsCore.getClass().getMethod(accessorName);
                Object singleEvent = accessor.invoke(vsCore);
                if (singleEvent == null) return;

                Method onMethod = findOnMethod(singleEvent.getClass());
                if (onMethod == null) return;

                Class<?> listenerType = onMethod.getParameterTypes()[0];
                if (!listenerType.isInterface()) return;

                Object listener = Proxy.newProxyInstance(
                        listenerType.getClassLoader(),
                        new Class<?>[]{listenerType},
                        new CollisionHandler()
                );
                onMethod.invoke(singleEvent, listener);
            } catch (ReflectiveOperationException ignored) {}
        }

        private static Method findOnMethod(Class<?> eventType) {
            for (Method m : eventType.getMethods()) {
                if (m.getName().equals("on") && m.getParameterCount() == 1) return m;
            }
            return null;
        }

        private static void enqueueFromEvent(Object event) {
            if (event == null) return;
            try {
                long shipIdA = ((Number) event.getClass().getMethod("getShipIdA").invoke(event)).longValue();
                long shipIdB = ((Number) event.getClass().getMethod("getShipIdB").invoke(event)).longValue();
                String dimensionId = String.valueOf(event.getClass().getMethod("getDimensionId").invoke(event));
                if (shipIdA <= 0L) return;
                PENDING.offer(new PendingCollision(dimensionId, shipIdA, shipIdB));
            } catch (ReflectiveOperationException | ClassCastException ignored) {}
        }

        private record PendingCollision(String dimensionId, long shipIdA, long shipIdB) {}

        private static final class CollisionHandler implements InvocationHandler {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                if (method.getName().equals("invoke") && args != null && args.length >= 1) {
                    enqueueFromEvent(args[0]);
                }
                return kotlinUnit();
            }

            private Object kotlinUnit() {
                try {
                    Class<?> unitClass = Class.forName("kotlin.Unit");
                    return unitClass.getField("INSTANCE").get(null);
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }
        }
    }





    public static final class CrashConsequences {
        private static final double MIN_IMPULSE_FOR_FRONT_CRUMPLE       = 2.5D;
        private static final double MIN_DECK_IMPULSE_RATIO              = 0.35D;
        private static final double BELLY_SCRAPE_MIN_HORIZONTAL_FRACTION= 0.85D;
        private static final double BELLY_SCRAPE_MIN_DOWNWARD_SPEED     = 3.0D;

        private final List<PendingExplosion> pendingExplosions = new ArrayList<>();

        public void processPendingExplosions(ServerLevel level) {
            Iterator<PendingExplosion> it = pendingExplosions.iterator();
            while (it.hasNext()) {
                PendingExplosion pending = it.next();
                if (!pending.dimension.equals(level.dimension().location().toString())) continue;
                if (level.getGameTime() < pending.triggerTick) continue;

                double ex = pending.worldPosition.x(), ey = pending.worldPosition.y(), ez = pending.worldPosition.z();
                int smokeCount     = (int) Math.max(40.0D, pending.power * 12.0D);
                int explosionCount = (int) Math.max(6.0D,  pending.power * 2.0D);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, ex, ey, ez, smokeCount,     3.0D, 2.0D, 3.0D, 0.06D);
                level.sendParticles(ParticleTypes.EXPLOSION,   ex, ey, ez, explosionCount, 2.0D, 1.5D, 2.0D, 0.10D);
                playAt(level, pending.worldPosition, SoundEvents.IRON_GOLEM_DEATH, 1.5f, 0.5f);
                it.remove();
            }
        }

        public void applyCrashEffects(
                ServerLevel level, LoadedServerShip ship,
                CrashPhysicsEngine.CrashResult result,
                Vec3 previousVelocity, Vec3 currentVelocity
        ) {
            ImpactProfile profile = classifyImpact(ship, result, previousVelocity, currentVelocity);
            if (profile == null) return;

            boolean catastrophic = result.severity() == CrashPhysicsEngine.CrashSeverity.CATASTROPHIC;
            Vector3d soundPos = ship.getTransform().getShipToWorld()
                    .transformPosition(profile.impactPoint, new Vector3d());

            applyWallBreachEffects(level, ship, profile, result, previousVelocity);
            if (profile.deckImpact) excavateUnderbodyCrater(level, ship, result, previousVelocity);

            if (!profile.bellyScrape
                    && (result.severity() == CrashPhysicsEngine.CrashSeverity.HARD
                    || result.severity() == CrashPhysicsEngine.CrashSeverity.CATASTROPHIC)) {
                crumpleFront(level, ship, profile, result);
                playAt(level, soundPos, SoundEvents.ANVIL_LAND,
                        catastrophic ? 1.5f : 1.2f, catastrophic ? 0.5f : 0.8f);
                playAt(level, soundPos,
                        catastrophic ? SoundEvents.IRON_GOLEM_DEATH : SoundEvents.IRON_GOLEM_HURT,
                        catastrophic ? 2.0f : 1.4f, catastrophic ? 0.6f : 0.75f);
                if (catastrophic)
                    playAt(level, soundPos, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.4f);
                level.sendParticles(ParticleTypes.EXPLOSION,    soundPos.x(), soundPos.y(), soundPos.z(), 6,  1.5D, 1.0D, 1.5D, 0.08D);
                level.sendParticles(ParticleTypes.LARGE_SMOKE,  soundPos.x(), soundPos.y(), soundPos.z(), catastrophic ? 25 : 12, 2.0D, 1.5D, 2.0D, 0.05D);
            }

            if (profile.bellyScrape) {
                applyScrapeEffects(level, ship, profile, result, previousVelocity);
                playAt(level, soundPos, SoundEvents.ANVIL_LAND,      1.0f, 0.7f);
                playAt(level, soundPos, SoundEvents.IRON_GOLEM_HURT, 1.5f, 0.6f);
                if (catastrophic)
                    playAt(level, soundPos, SoundEvents.IRON_GOLEM_DEATH, 1.0f, 0.55f);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, soundPos.x(), soundPos.y(), soundPos.z(), catastrophic ? 30 : 15, 3.0D, 0.5D, 3.0D, 0.04D);
            }

            if (profile.deckImpact
                    && (result.severity() == CrashPhysicsEngine.CrashSeverity.HARD
                    || result.severity() == CrashPhysicsEngine.CrashSeverity.CATASTROPHIC)) {
                applyGearCollapseEffects(level, ship, profile, result);
                playAt(level, soundPos, SoundEvents.ANVIL_LAND,
                        catastrophic ? 2.5f : 2.0f, catastrophic ? 0.4f : 0.5f);
                playAt(level, soundPos,
                        catastrophic ? SoundEvents.IRON_GOLEM_DEATH : SoundEvents.IRON_GOLEM_HURT,
                        catastrophic ? 1.5f : 1.2f, catastrophic ? 0.55f : 0.65f);
                level.sendParticles(ParticleTypes.EXPLOSION, soundPos.x(), soundPos.y(), soundPos.z(), 4, 1.0D, 0.5D, 1.0D, 0.05D);
            }

            if (catastrophic && profile.deckImpact && !profile.bellyScrape) {
                queueExplosion(level, ship, profile, result);
            }
        }

        private ImpactProfile classifyImpact(
                LoadedServerShip ship, CrashPhysicsEngine.CrashResult result,
                Vec3 previousVelocity, Vec3 currentVelocity
        ) {
            if (previousVelocity == null) return null;

            Vec3 approachVelocity = previousVelocity.lengthSqr() >= currentVelocity.lengthSqr() ? previousVelocity : currentVelocity;
            if (approachVelocity.lengthSqr() < 1.0E-4D) return null;

            Vec3 impactImpulse = currentVelocity.subtract(previousVelocity);
            if (impactImpulse.length() < MIN_IMPULSE_FOR_FRONT_CRUMPLE) return null;

            double downwardSpeed = Math.max(0.0D, -previousVelocity.y);
            double upwardImpulse = Math.max(0.0D, impactImpulse.y);
            boolean deckImpact = downwardSpeed >= Config.deckImpactVerticalSpeed
                    && upwardImpulse >= Math.max(0.5D, downwardSpeed * MIN_DECK_IMPULSE_RATIO);

            Vector3d travelDirWorld  = vecNormalize(approachVelocity);
            Vector3d impulseDirWorld = vecNormalize(impactImpulse);

            double horizontalSpeed = Math.sqrt(previousVelocity.x * previousVelocity.x + previousVelocity.z * previousVelocity.z);
            double totalSpeed = previousVelocity.length();
            boolean bellyScrape = totalSpeed > 0.1D
                    && (horizontalSpeed / totalSpeed) > BELLY_SCRAPE_MIN_HORIZONTAL_FRACTION
                    && downwardSpeed >= BELLY_SCRAPE_MIN_DOWNWARD_SPEED
                    && upwardImpulse >= downwardSpeed * 0.3D;

            double counterImpulse = -impactImpulse.dot(approachVelocity.normalize());
            if (!deckImpact && !bellyScrape
                    && counterImpulse < Math.max(MIN_IMPULSE_FOR_FRONT_CRUMPLE, result.deltaV() * 0.4D)) {
                return null;
            }

            Vector3d travelDirLocal = ship.getTransform().getWorldToShip()
                    .transformDirection(travelDirWorld, new Vector3d());
            if (travelDirLocal.lengthSquared() < 1.0E-6D) return null;
            travelDirLocal.normalize();

            ShipBounds bounds = getShipBounds(ship);
            Vector3d impactPoint;
            if (bounds != null) {
                impactPoint = supportPoint(bounds, travelDirLocal);
                impactPoint.sub(new Vector3d(travelDirLocal).mul(0.85D));
                if (deckImpact) {
                    Vector3d bottom = supportPoint(bounds, new Vector3d(0.0D, -1.0D, 0.0D));
                    impactPoint = impactPoint.mul(0.65D, new Vector3d()).add(bottom.mul(0.35D, new Vector3d()));
                }
            } else {
                impactPoint = new Vector3d(travelDirLocal).mul(0.75D);
            }

            return new ImpactProfile(impactPoint, travelDirLocal, impulseDirWorld, deckImpact, bellyScrape);
        }

        private void crumpleFront(
                ServerLevel level, LoadedServerShip ship,
                ImpactProfile profile, CrashPhysicsEngine.CrashResult result
        ) {
            double severityScale = switch (result.severity()) {
                case SCRAPE -> 0.70D;
                case HARD   -> 1.0D;
                case CATASTROPHIC -> 1.55D;
                default -> 0.0D;
            };
            if (severityScale <= 0.0D) return;

            double depth   = Config.frontCrumpleDepth  * severityScale * Config.terrainBreachFactor;
            double radius  = Config.frontCrumpleRadius * severityScale * Math.sqrt(Math.max(0.0D, Config.terrainBreachFactor));
            Vector3d backward = new Vector3d(profile.travelDirLocal).negate().normalize();

            BlockPos center = BlockPos.containing(profile.impactPoint.x(), profile.impactPoint.y(), profile.impactPoint.z());
            int extent = (int) Math.ceil(Math.max(depth, radius)) + 1;

            for (int dx = -extent; dx <= extent; dx++) {
                for (int dy = -extent; dy <= extent; dy++) {
                    for (int dz = -extent; dz <= extent; dz++) {
                        BlockPos pos = center.offset(dx, dy, dz);
                        Vector3d sample = new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
                        Vector3d offset = sample.sub(profile.impactPoint, new Vector3d());
                        double longitudinal = offset.dot(backward);
                        if (longitudinal < -0.5D || longitudinal > depth) continue;

                        Vector3d lateral = offset.sub(new Vector3d(backward).mul(longitudinal));
                        double allowedRadius = lerp(radius * 0.45D, radius, clamp01(longitudinal / Math.max(0.001D, depth)));
                        if (lateral.length() > allowedRadius) continue;

                        BlockPos worldPos = localToWorldPos(ship, sample);
                        BlockState state = level.getBlockState(worldPos);
                        if (state.isAir() || state.getDestroySpeed(level, worldPos) < 0.0F) continue;

                        if (result.severity() == CrashPhysicsEngine.CrashSeverity.HARD
                                && lateral.length() > allowedRadius * 0.7D
                                && level.random.nextFloat() < 0.45F) continue;

                        level.destroyBlock(worldPos, false);
                    }
                }
            }
        }

        private void applyScrapeEffects(
                ServerLevel level, LoadedServerShip ship,
                ImpactProfile profile, CrashPhysicsEngine.CrashResult result, Vec3 previousVelocity
        ) {
            ShipBounds bounds = getShipBounds(ship);
            if (bounds == null) return;

            double horizontalSpeed = Math.sqrt(previousVelocity.x * previousVelocity.x + previousVelocity.z * previousVelocity.z);
            Vector3d horiz = new Vector3d(profile.travelDirLocal.x(), 0.0D, profile.travelDirLocal.z());
            if (horiz.lengthSquared() < 1.0E-6D) return;
            horiz.normalize();

            Vector3d perp = horiz.cross(new Vector3d(0.0D, 1.0D, 0.0D), new Vector3d());
            if (perp.lengthSquared() < 1.0E-6D) perp.set(1.0D, 0.0D, 0.0D);
            else perp.normalize();

            double stripLength = Math.min(horizontalSpeed * Config.bellyScrapeBlocksPerMps * Config.terrainBreachFactor, 24.0D);
            double centerX = (bounds.minX() + bounds.maxX()) / 2.0D;
            double centerZ = (bounds.minZ() + bounds.maxZ()) / 2.0D;
            double bottomY = bounds.minY();
            Vector3d startLocal = new Vector3d(centerX, bottomY, centerZ)
                    .sub(new Vector3d(horiz).mul(stripLength * 0.5D));

            boolean catastrophic = result.severity() == CrashPhysicsEngine.CrashSeverity.CATASTROPHIC;
            float fireChance = catastrophic ? 0.12F : 0.06F;

            for (int s = 0; s <= (int) (stripLength + 0.5D); s++) {
                Vector3d sCenter = new Vector3d(startLocal).add(new Vector3d(horiz).mul(s));
                for (int w = -1; w <= 1; w++) {
                    for (int dy = 0; dy <= 1; dy++) {
                        Vector3d sample = new Vector3d(sCenter).add(new Vector3d(perp).mul(w)).add(0.0D, dy, 0.0D);
                        BlockPos pos = localToWorldPos(ship, sample);
                        if (pos.getY() < level.getMinBuildHeight()) continue;
                        BlockState state = level.getBlockState(pos);
                        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) continue;
                        if (result.severity() == CrashPhysicsEngine.CrashSeverity.HARD
                                && dy == 1 && Math.abs(w) == 1 && level.random.nextFloat() < 0.55F) continue;
                        level.destroyBlock(pos, false);
                        if (level.random.nextFloat() < fireChance) {
                            BlockPos firePos = pos.above();
                            if (level.getBlockState(firePos).isAir())
                                level.setBlock(firePos, Blocks.FIRE.defaultBlockState(), 3);
                        }
                    }
                }
            }

            if (catastrophic) {
                Vector3d scrapeEnd = new Vector3d(startLocal).add(new Vector3d(horiz).mul(stripLength));
                Vector3d worldPos = ship.getTransform().getShipToWorld().transformPosition(scrapeEnd, new Vector3d());
                pendingExplosions.add(new PendingExplosion(
                        level.dimension().location().toString(),
                        level.getGameTime() + Math.max(0, Config.catastrophicExplosionDelayTicks),
                        worldPos,
                        (float) Math.max(2.5D, Config.catastrophicExplosionPower * 0.55D)
                ));
            }
        }

        private void applyGearCollapseEffects(
                ServerLevel level, LoadedServerShip ship,
                ImpactProfile profile, CrashPhysicsEngine.CrashResult result
        ) {
            ShipBounds bounds = getShipBounds(ship);
            if (bounds == null) return;

            boolean catastrophic = result.severity() == CrashPhysicsEngine.CrashSeverity.CATASTROPHIC;
            float destroyChance = catastrophic ? 1.0F : (float) Config.gearCollapseDestructionFactor;

            double centerX = (bounds.minX() + bounds.maxX()) / 2.0D;
            double centerZ = (bounds.minZ() + bounds.maxZ()) / 2.0D;
            double bottomY = bounds.minY();
            int halfSizeX = (int) Math.max(1, Math.min(Math.ceil((bounds.maxX() - bounds.minX()) * 0.35D * Config.terrainBreachFactor), 8));
            int halfSizeZ = (int) Math.max(1, Math.min(Math.ceil((bounds.maxZ() - bounds.minZ()) * 0.35D * Config.terrainBreachFactor), 8));

            for (int dx = -halfSizeX; dx <= halfSizeX; dx++) {
                for (int dz = -halfSizeZ; dz <= halfSizeZ; dz++) {
                    double ellipse = (dx * dx / (double)(halfSizeX * halfSizeX))
                            + (dz * dz / (double)(halfSizeZ * halfSizeZ));
                    if (ellipse > 1.0D) continue;
                    for (int dy = 0; dy <= 2; dy++) {
                        BlockPos pos = localToWorldPos(ship, new Vector3d(centerX + dx, bottomY + dy, centerZ + dz));
                        if (pos.getY() < level.getMinBuildHeight()) continue;
                        BlockState state = level.getBlockState(pos);
                        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) continue;
                        if (level.random.nextFloat() < destroyChance) {
                            level.destroyBlock(pos, false);
                            if (catastrophic && level.random.nextFloat() < 0.15F) {
                                BlockPos firePos = pos.above();
                                if (level.getBlockState(firePos).isAir())
                                    level.setBlock(firePos, Blocks.FIRE.defaultBlockState(), 3);
                            }
                        }
                    }
                }
            }
        }

        private void applyWallBreachEffects(
                ServerLevel level, LoadedServerShip ship,
                ImpactProfile profile, CrashPhysicsEngine.CrashResult result, Vec3 previousVelocity
        ) {
            double horizontalSpeed = Math.sqrt(previousVelocity.x * previousVelocity.x + previousVelocity.z * previousVelocity.z);
            if (horizontalSpeed < 2.0D) return;

            double mass = Math.max(1.0D, ship.getInertiaData().getShipMass());
            double momentum = mass * horizontalSpeed;
            double minimumMomentum = result.severity() == CrashPhysicsEngine.CrashSeverity.SCRAPE ? 1800.0D : 1200.0D;
            if (momentum < minimumMomentum) return;

            Vector3d forward = new Vector3d(profile.travelDirLocal.x(), 0.0D, profile.travelDirLocal.z());
            if (forward.lengthSquared() < 1.0E-6D) return;
            forward.normalize();

            Vector3d side = new Vector3d(0.0D, 1.0D, 0.0D).cross(forward, new Vector3d());
            if (side.lengthSquared() < 1.0E-6D) side.set(1.0D, 0.0D, 0.0D);
            else side.normalize();

            double severityScale = switch (result.severity()) {
                case SCRAPE -> 0.85D;
                case HARD   -> 1.25D;
                case CATASTROPHIC -> 1.8D;
                case NONE   -> 0.0D;
            };
            if (severityScale <= 0.0D) return;

            ShipBounds bounds = getShipBounds(ship);
            double sizeX = bounds != null ? Math.max(1.0D, bounds.maxX() - bounds.minX()) : 4.0D;
            double sizeY = bounds != null ? Math.max(1.0D, bounds.maxY() - bounds.minY()) : 3.0D;
            double sizeZ = bounds != null ? Math.max(1.0D, bounds.maxZ() - bounds.minZ()) : 6.0D;
            double lateralSpan = Math.abs(side.x()) * sizeX + Math.abs(side.z()) * sizeZ;
            int halfWidth  = (int) Math.max(1, Math.min(Math.ceil(lateralSpan * 0.5D), 14));
            int halfHeight = (int) Math.max(1, Math.min(Math.ceil(sizeY * 0.5D), 10));
            int depth = (int) Math.max(1, Math.min(
                    Math.ceil((horizontalSpeed * 0.55D + Math.log10(mass + 1.0D) * 1.35D) * severityScale * Config.terrainBreachFactor),
                    16
            ));

            for (int d = 0; d <= depth; d++) {
                for (int w = -halfWidth; w <= halfWidth; w++) {
                    for (int h = -halfHeight; h <= halfHeight; h++) {
                        double ellipse = (w * w / (double)(halfWidth * halfWidth))
                                + (h * h / (double)(halfHeight * halfHeight));
                        if (ellipse > 1.20D) continue;

                        Vector3d sampleLocal = new Vector3d(profile.impactPoint)
                                .add(new Vector3d(forward).mul(d))
                                .add(new Vector3d(side).mul(w))
                                .add(0.0D, h, 0.0D);
                        BlockPos pos = localToWorldPos(ship, sampleLocal);
                        if (pos.getY() < level.getMinBuildHeight()) continue;
                        BlockState state = level.getBlockState(pos);
                        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) continue;

                        double breakPower = momentum / (350.0D + d * 115.0D);
                        if (result.severity() == CrashPhysicsEngine.CrashSeverity.SCRAPE) breakPower *= 0.7D;
                        float hardness = state.getDestroySpeed(level, pos);
                        if (hardness > 0.0F && breakPower < hardness * 2.4D && level.random.nextFloat() < 0.45F) continue;
                        level.destroyBlock(pos, false);
                    }
                }
            }
        }

        private void excavateUnderbodyCrater(
                ServerLevel level, LoadedServerShip ship,
                CrashPhysicsEngine.CrashResult result, Vec3 previousVelocity
        ) {
            ShipBounds bounds = getShipBounds(ship);
            if (bounds == null) return;

            double downwardSpeed = Math.max(0.0D, -previousVelocity.y);
            if (downwardSpeed < 2.5D) return;

            double sizeX = Math.max(1.0D, bounds.maxX() - bounds.minX());
            double sizeZ = Math.max(1.0D, bounds.maxZ() - bounds.minZ());
            int halfX = (int) Math.max(1, Math.min(Math.ceil(sizeX * 0.5D), 12));
            int halfZ = (int) Math.max(1, Math.min(Math.ceil(sizeZ * 0.5D), 12));
            int depth = (int) Math.max(1, Math.min(
                    Math.ceil((0.6D + downwardSpeed * 0.35D) * Config.terrainBreachFactor
                            + (result.severity() == CrashPhysicsEngine.CrashSeverity.CATASTROPHIC ? 2.0D : 0.0D)),
                    7
            ));

            double centerX = (bounds.minX() + bounds.maxX()) * 0.5D;
            double centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5D;
            double baseY   = bounds.minY() - 0.25D;

            for (int dx = -halfX; dx <= halfX; dx++) {
                for (int dz = -halfZ; dz <= halfZ; dz++) {
                    double ellipse = (dx * dx / (double)(halfX * halfX)) + (dz * dz / (double)(halfZ * halfZ));
                    if (ellipse > 1.0D) continue;
                    int localDepth = Math.max(1, (int) Math.ceil(depth * (1.0D - ellipse * 0.55D)));
                    for (int dy = 0; dy <= localDepth; dy++) {
                        BlockPos pos = localToWorldPos(ship, new Vector3d(centerX + dx, baseY - dy, centerZ + dz));
                        if (pos.getY() < level.getMinBuildHeight()) continue;
                        BlockState state = level.getBlockState(pos);
                        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) continue;
                        level.destroyBlock(pos, false);
                    }
                }
            }
        }

        private void queueExplosion(
                ServerLevel level, LoadedServerShip ship,
                ImpactProfile profile, CrashPhysicsEngine.CrashResult result
        ) {
            Vector3d worldPos = ship.getTransform().getShipToWorld().transformPosition(profile.impactPoint, new Vector3d());
            double sizeScale = estimateExplosionScale(ship);
            float power = (float) Math.max(Config.catastrophicExplosionPower, Math.min(
                    16.0D,
                    Config.catastrophicExplosionPower * sizeScale + Math.min(4.5D, result.damage() / 14.0D)
            ));
            pendingExplosions.add(new PendingExplosion(
                    level.dimension().location().toString(),
                    level.getGameTime() + Math.max(0, Config.catastrophicExplosionDelayTicks),
                    worldPos, power
            ));
        }



        private static Vector3d vecNormalize(Vec3 v) {
            Vector3d r = new Vector3d(v.x, v.y, v.z);
            return r.lengthSquared() < 1.0E-8D ? r : r.normalize();
        }

        private static Vector3d vecNormalize(Vector3d v) {
            return v.lengthSquared() < 1.0E-8D ? v : v.normalize();
        }

        private static Vector3d supportPoint(ShipBounds b, Vector3dc dir) {
            return new Vector3d(
                    dir.x() >= 0.0D ? b.maxX() + 0.5D : b.minX() + 0.5D,
                    dir.y() >= 0.0D ? b.maxY() + 0.5D : b.minY() + 0.5D,
                    dir.z() >= 0.0D ? b.maxZ() + 0.5D : b.minZ() + 0.5D
            );
        }

        private static double estimateExplosionScale(LoadedServerShip ship) {
            ShipBounds bounds = getShipBounds(ship);
            if (bounds != null) {
                double sx = Math.max(1.0D, bounds.maxX() - bounds.minX());
                double sy = Math.max(1.0D, bounds.maxY() - bounds.minY());
                double sz = Math.max(1.0D, bounds.maxZ() - bounds.minZ());
                double cbrt = Math.cbrt(sx * sy * sz);
                double norm = clamp01((cbrt - 3.0D) / 16.0D);
                return 1.0D + norm * Config.shipSizeExplosionScale;
            }
            double mass = Math.max(1.0D, ship.getInertiaData().getShipMass());
            double normMass = clamp01((Math.log10(mass + 1.0D) - 2.5D) / 2.5D);
            return 1.0D + normMass * Config.shipSizeExplosionScale;
        }

        private static void playAt(ServerLevel level, Vector3d pos, SoundEvent sound, float volume, float pitch) {
            level.playSound(null, pos.x(), pos.y(), pos.z(), sound, SoundSource.BLOCKS, volume, pitch);
        }

        private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
        private static double clamp01(double v)                   { return Math.max(0.0D, Math.min(1.0D, v)); }

        private static BlockPos localToWorldPos(LoadedServerShip ship, Vector3dc local) {
            Vector3d world = ship.getTransform().getShipToWorld().transformPosition(local, new Vector3d());
            return BlockPos.containing(world.x(), world.y(), world.z());
        }

        private record ImpactProfile(
                Vector3d impactPoint, Vector3d travelDirLocal, Vector3d impulseDirWorld,
                boolean deckImpact, boolean bellyScrape
        ) {}

        private record PendingExplosion(String dimension, long triggerTick, Vector3d worldPosition, float power) {}
    }





    public static final class RuntimeEvents {
        private static final double MIN_DIRECTION_ALIGNMENT    = 0.25D;
        private static final long   CRITICAL_WARNING_INTERVAL  = 200L;

        private final Map<Long, RuntimeShipState> runtimeState = new HashMap<>();
        private final CrashConsequences consequences = new CrashConsequences();

        @SubscribeEvent
        public void onLevelTick(TickEvent.LevelTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;

            consequences.processPendingExplosions(level);
            Set<Long> collisionSignals = CollisionSignals.drainSignalsForDimension(
                    level.dimension().location().toString());

            for (LoadedServerShip ship : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
                processShip(level, ship, collisionSignals);
            }
        }

        private void processShip(ServerLevel level, LoadedServerShip ship, Set<Long> collisionSignals) {
            long shipId = ship.getId();
            RuntimeShipState state = runtimeState.computeIfAbsent(shipId, ignored -> {
                Hooks.onShipCreated(level.getServer(), shipId, null);
                return new RuntimeShipState();
            });

            Vec3 previousVelocity  = state.lastVelocity;
            Vec3 velocity          = toMinecraft(ship.getVelocity());
            boolean inferredCol    = inferCollision(state, velocity);
            boolean hadCollision   = inferredCol || collisionSignals.contains(shipId);
            CrashPhysicsEngine.ImpactPart impactPart = inferImpactPart(ship, previousVelocity, velocity);

            Hooks.PhysicsSampleOutcome outcome = Hooks.onShipPhysicsSampleDetailed(
                    level.getServer(), shipId, level.getGameTime(),
                    velocity, ship.getInertiaData().getShipMass(), hadCollision, impactPart);

            state.lastVelocity = velocity;

            BindingRecord record = outcome.record();
            CrashPhysicsEngine.CrashResult result = outcome.result();
            if (result.crash()) {
                applyWingClipDamage(level, ship, previousVelocity, result, state);
                consequences.applyCrashEffects(level, ship, result, previousVelocity, velocity);
                notifyCrash(level, ship, record, result);
                armPostCrashDamping(state, result);
                armGroundSettling(state, previousVelocity, result);
                applyCrashBraking(ship, result);
            }

            applyPostCrashDamping(ship, state);
            applyGroundSettling(ship, state);
            applyWingInstability(ship, state);
            applyIntegrityDrag(ship, record);
            warnCriticalIntegrity(level, ship, record, state);
        }

        @SubscribeEvent
        public void onBlockBreak(BlockEvent.BreakEvent event) {
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            LoadedServerShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level, event.getPos());
            if (ship == null) return;

            RuntimeShipState state = runtimeState.computeIfAbsent(ship.getId(), ignored -> new RuntimeShipState());
            ShipBounds bounds = getShipBounds(ship);
            if (bounds == null) return;

            Vector3d local = ship.getTransform().getWorldToShip().transformPosition(
                    new Vector3d(event.getPos().getX() + 0.5D, event.getPos().getY() + 0.5D, event.getPos().getZ() + 0.5D),
                    new Vector3d()
            );
            double centerX  = (bounds.minX() + bounds.maxX()) * 0.5D;
            double halfSpan = Math.max(1.0D, (bounds.maxX() - bounds.minX()) * 0.5D);
            double lateral  = (local.x() - centerX) / halfSpan;
            if (Math.abs(lateral) < 0.58D) return;

            double centerY = (bounds.minY() + bounds.maxY()) * 0.5D;
            if (local.y() < centerY - 1.5D) return;

            if (lateral >= 0.0D) state.rightWingDamage = Math.min(100.0D, state.rightWingDamage + 2.2D);
            else                  state.leftWingDamage  = Math.min(100.0D, state.leftWingDamage  + 2.2D);
        }

        private boolean inferCollision(RuntimeShipState state, Vec3 velocity) {
            if (state.lastVelocity == null) return false;
            double previousSpeed = state.lastVelocity.length();
            double currentSpeed  = velocity.length();
            double deltaV        = velocity.subtract(state.lastVelocity).length();
            double speedLoss     = Math.max(0.0D, previousSpeed - currentSpeed);
            double dirAlign      = alignment(state.lastVelocity, velocity);
            return deltaV >= Config.minDeltaV
                    && (speedLoss >= Config.minDeltaV * 0.35D || dirAlign <= MIN_DIRECTION_ALIGNMENT);
        }

        private void notifyCrash(
                ServerLevel level, LoadedServerShip ship,
                BindingRecord record, CrashPhysicsEngine.CrashResult result
        ) {
            Component message = Component.literal("[VS Kinetic] "
                    + record.displayName()
                    + " suffered a " + prettySeverity(result.severity())
                    + " impact. Integrity now " + String.format("%.1f", record.structuralIntegrity()) + "%.");

            Set<ServerPlayer> recipients = new LinkedHashSet<>();
            Vector3dc shipCenter = ship.getTransform().getPositionInWorld();
            double notifDistSq = Config.crashNotificationRadius * Config.crashNotificationRadius;

            for (ServerPlayer player : level.players()) {
                if (isPlayerOnShip(level, ship, player)
                        || player.distanceToSqr(shipCenter.x(), shipCenter.y(), shipCenter.z()) <= notifDistSq) {
                    recipients.add(player);
                }
            }
            addTrackedPlayer(level, recipients, record.owner());
            addTrackedPlayer(level, recipients, record.createdBy());
            for (ServerPlayer r : recipients) r.sendSystemMessage(message);
        }

        private void applyCrashBraking(LoadedServerShip ship, CrashPhysicsEngine.CrashResult result) {
            double extra = switch (result.severity()) {
                case SCRAPE -> 0.25D;
                case HARD   -> 0.75D;
                case CATASTROPHIC -> 1.50D;
                case NONE   -> 0.0D;
            };
            applyBrakingForce(ship, extra);
        }

        private void armPostCrashDamping(RuntimeShipState state, CrashPhysicsEngine.CrashResult result) {
            int ticks = switch (result.severity()) {
                case SCRAPE -> 10; case HARD -> 20; case CATASTROPHIC -> 34; case NONE -> 0;
            };
            double decel = switch (result.severity()) {
                case SCRAPE -> 0.80D; case HARD -> 1.80D; case CATASTROPHIC -> 3.00D; case NONE -> 0.0D;
            };
            state.postCrashDampingTicks = Math.max(state.postCrashDampingTicks, ticks);
            state.postCrashDamping = Math.max(state.postCrashDamping, Math.max(result.bounceDamping(), decel));
        }

        private void applyPostCrashDamping(LoadedServerShip ship, RuntimeShipState state) {
            if (state.postCrashDampingTicks <= 0 || state.postCrashDamping <= 0.0D) return;
            applyBrakingForce(ship, state.postCrashDamping);
            state.postCrashDampingTicks--;
        }

        private void armGroundSettling(
                RuntimeShipState state, Vec3 previousVelocity, CrashPhysicsEngine.CrashResult result
        ) {
            if (previousVelocity == null) return;
            double downwardSpeed = Math.max(0.0D, -previousVelocity.y);
            if (downwardSpeed < 2.2D) return;
            int ticks = switch (result.severity()) {
                case SCRAPE -> 8; case HARD -> 14; case CATASTROPHIC -> 22; case NONE -> 0;
            };
            double strength = switch (result.severity()) {
                case SCRAPE -> 0.30D; case HARD -> 0.55D; case CATASTROPHIC -> 0.95D; case NONE -> 0.0D;
            };
            state.groundSettleTicks    = Math.max(state.groundSettleTicks, ticks);
            state.groundSettleStrength = Math.max(
                    state.groundSettleStrength,
                    strength * (1.0D + Math.min(1.25D, downwardSpeed / 8.0D)) * Math.max(0.01D, result.bounceDamping())
            );
        }

        private void applyGroundSettling(LoadedServerShip ship, RuntimeShipState state) {
            if (state.groundSettleTicks <= 0 || state.groundSettleStrength <= 0.0D) return;
            double mass = Math.max(1.0D, ship.getInertiaData().getShipMass());
            Vector3d force  = new Vector3d(0.0D, -mass * state.groundSettleStrength, 0.0D);
            Vector3d center = new Vector3d(ship.getTransform().getPositionInWorld());
            ValkyrienSkiesMod.getOrCreateGTPA(ship.getChunkClaimDimension()).applyWorldForce(ship.getId(), force, center);
            state.groundSettleTicks--;
            state.groundSettleStrength *= 0.88D;
        }

        private void applyWingClipDamage(
                ServerLevel level, LoadedServerShip ship,
                Vec3 previousVelocity, CrashPhysicsEngine.CrashResult result, RuntimeShipState state
        ) {
            if (previousVelocity == null || previousVelocity.lengthSqr() < 1.0E-4D) return;
            ShipBounds bounds = getShipBounds(ship);
            if (bounds == null) return;

            Vector3d velLocal = ship.getTransform().getWorldToShip().transformDirection(
                    new Vector3d(previousVelocity.x, previousVelocity.y, previousVelocity.z), new Vector3d());
            double horizontalSpeed = Math.sqrt(previousVelocity.x * previousVelocity.x + previousVelocity.z * previousVelocity.z);
            double downwardSpeed   = Math.max(0.0D, -previousVelocity.y);
            if (horizontalSpeed < 3.0D && downwardSpeed < 3.5D) return;

            double sideWeight = Math.abs(velLocal.x()) / Math.max(0.001D,
                    Math.sqrt(velLocal.x() * velLocal.x() + velLocal.z() * velLocal.z()));
            if (sideWeight < 0.25D && downwardSpeed < Config.deckImpactVerticalSpeed * 0.85D) return;

            boolean hitRight = velLocal.x() >= 0.0D;
            double wingDamage = switch (result.severity()) {
                case SCRAPE -> 10.0D; case HARD -> 22.0D; case CATASTROPHIC -> 36.0D; case NONE -> 0.0D;
            };
            wingDamage *= (0.8D + sideWeight * 0.8D);
            if (downwardSpeed > Config.deckImpactVerticalSpeed) wingDamage *= 1.2D;

            if (hitRight) {
                state.rightWingDamage = Math.min(100.0D, state.rightWingDamage + wingDamage);
                severWingSection(level, ship, bounds, true, result);
            } else {
                state.leftWingDamage = Math.min(100.0D, state.leftWingDamage + wingDamage);
                severWingSection(level, ship, bounds, false, result);
            }
        }

        private void applyWingInstability(LoadedServerShip ship, RuntimeShipState state) {
            double left = state.leftWingDamage, right = state.rightWingDamage;
            if (left < 1.0D && right < 1.0D) return;

            Vector3dc velocity = ship.getVelocity();
            double speedSq = velocity.lengthSquared();
            if (speedSq < 0.15D) return;

            Vector3d rightAxis = ship.getTransform().getShipToWorld()
                    .transformDirection(new Vector3d(1.0D, 0.0D, 0.0D), new Vector3d());
            if (rightAxis.lengthSquared() < 1.0E-6D) return;
            rightAxis.normalize();

            Vector3d velocityDir = new Vector3d(velocity).normalize();
            double mass      = Math.max(1.0D, ship.getInertiaData().getShipMass());
            double imbalance = (right - left) / 100.0D;
            double damage    = Math.max(left, right) / 100.0D;
            if (Math.abs(imbalance) < 0.02D && damage < 0.35D) return;

            double base = mass * (0.12D + damage * 0.42D);
            Vector3d force = new Vector3d(velocityDir).mul(-base * (0.6D + Math.abs(imbalance)));
            force.add(0.0D, -base * 0.42D * (0.3D + damage), 0.0D);

            Vector3d center    = new Vector3d(ship.getTransform().getPositionInWorld());
            Vector3d offsetDir = new Vector3d(rightAxis).mul(Math.signum(imbalance == 0.0D ? (right - left) : imbalance));
            Vector3d applyPos  = center.add(offsetDir.mul(3.5D), new Vector3d());
            ValkyrienSkiesMod.getOrCreateGTPA(ship.getChunkClaimDimension()).applyWorldForce(ship.getId(), force, applyPos);

            double speed = Math.sqrt(speedSq);
            double progressive = (speed / 45.0D) * damage * 0.35D;
            if (imbalance >= 0.0D) state.rightWingDamage = Math.min(100.0D, state.rightWingDamage + progressive);
            else                   state.leftWingDamage  = Math.min(100.0D, state.leftWingDamage  + progressive);
        }

        private void severWingSection(
                ServerLevel level, LoadedServerShip ship,
                ShipBounds bounds, boolean rightWing, CrashPhysicsEngine.CrashResult result
        ) {
            double halfSpan = Math.max(1.0D, (bounds.maxX() - bounds.minX()) * 0.5D);
            double centerX  = (bounds.minX() + bounds.maxX()) * 0.5D;
            double centerY  = (bounds.minY() + bounds.maxY()) * 0.5D;
            double centerZ  = (bounds.minZ() + bounds.maxZ()) * 0.5D;
            double wingX    = rightWing ? centerX + halfSpan * 0.88D : centerX - halfSpan * 0.88D;

            int sliceDepth = switch (result.severity()) { case SCRAPE -> 2; case HARD -> 3; case CATASTROPHIC -> 5; default -> 0; };
            int halfHeight = switch (result.severity()) { case SCRAPE -> 1; case HARD -> 2; case CATASTROPHIC -> 3; default -> 0; };
            int halfChord  = switch (result.severity()) { case SCRAPE -> 2; case HARD -> 3; case CATASTROPHIC -> 4; default -> 0; };
            if (sliceDepth <= 0) return;

            int xDir = rightWing ? -1 : 1;
            for (int dx = 0; dx <= sliceDepth; dx++) {
                for (int dy = -halfHeight; dy <= halfHeight; dy++) {
                    for (int dz = -halfChord; dz <= halfChord; dz++) {
                        Vector3d local = new Vector3d(wingX + dx * xDir, centerY + dy, centerZ + dz);
                        BlockPos pos = localToWorldPos(ship, local);
                        if (pos.getY() < level.getMinBuildHeight()) continue;
                        if (level.getBlockState(pos).isAir()) continue;
                        if (level.random.nextFloat() < 0.84F) level.destroyBlock(pos, false);
                    }
                }
            }
        }

        private void applyIntegrityDrag(LoadedServerShip ship, BindingRecord record) {
            if (record.structuralIntegrity() <= Config.criticalIntegrityThreshold) {
                applyBrakingForce(ship, Config.criticalIntegrityDeceleration);
                return;
            }
            if (record.structuralIntegrity() <= Config.lowIntegrityThreshold) {
                applyBrakingForce(ship, Config.lowIntegrityDeceleration);
            }
        }

        private void warnCriticalIntegrity(
                ServerLevel level, LoadedServerShip ship,
                BindingRecord record, RuntimeShipState state
        ) {
            if (record.structuralIntegrity() > Config.criticalIntegrityThreshold) {
                state.lastCriticalWarningTick = Long.MIN_VALUE;
                return;
            }
            long gameTime = level.getGameTime();
            if (gameTime - state.lastCriticalWarningTick < CRITICAL_WARNING_INTERVAL) return;
            state.lastCriticalWarningTick = gameTime;

            Component message = Component.literal("[VS Kinetic] "
                    + record.displayName() + " is critically damaged and bleeding speed.");
            for (ServerPlayer player : level.players()) {
                if (isPlayerOnShip(level, ship, player)) player.sendSystemMessage(message);
            }
        }

        private void applyBrakingForce(LoadedServerShip ship, double deceleration) {
            if (deceleration <= 0.0D) return;
            Vector3dc velocity = ship.getVelocity();
            if (velocity.lengthSquared() < 1.0E-4D) return;
            double mass = Math.max(1.0D, ship.getInertiaData().getShipMass());
            Vector3d brakingForce = new Vector3d(velocity).normalize().mul(-mass * deceleration);
            ValkyrienSkiesMod.getOrCreateGTPA(ship.getChunkClaimDimension()).applyWorldForce(ship.getId(), brakingForce, null);
        }

        private boolean isPlayerOnShip(ServerLevel level, LoadedServerShip ship, ServerPlayer player) {
            LoadedServerShip ps = VSGameUtilsKt.getLoadedShipManagingPos(level, player.blockPosition());
            if (ps != null && ps.getId() == ship.getId()) return true;
            LoadedShip ms = VSGameUtilsKt.getShipMountedTo(player);
            if (ms != null && ms.getId() == ship.getId()) return true;
            if (player instanceof IEntityDraggingInformationProvider drag) {
                Long draggedId = drag.getDraggingInformation().getLastShipStoodOn();
                return drag.getDraggingInformation().isEntityBeingDraggedByAShip()
                        && draggedId != null && draggedId == ship.getId();
            }
            return false;
        }

        private void addTrackedPlayer(ServerLevel level, Set<ServerPlayer> recipients, UUID playerId) {
            if (playerId == null) return;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) recipients.add(player);
        }

        private static double alignment(Vec3 a, Vec3 b) {
            double la = a.length(), lb = b.length();
            if (la < 1.0E-4D || lb < 1.0E-4D) return 1.0D;
            return a.dot(b) / (la * lb);
        }

        private static Vec3 toMinecraft(Vector3dc v) { return new Vec3(v.x(), v.y(), v.z()); }

        private static CrashPhysicsEngine.ImpactPart inferImpactPart(
                LoadedServerShip ship, Vec3 previousVelocity, Vec3 currentVelocity
        ) {
            if (previousVelocity == null) return CrashPhysicsEngine.ImpactPart.AUTO;
            Vec3 approach = previousVelocity.lengthSqr() >= currentVelocity.lengthSqr() ? previousVelocity : currentVelocity;
            if (approach.lengthSqr() < 1.0E-4D) return CrashPhysicsEngine.ImpactPart.AUTO;

            Vector3d local = ship.getTransform().getWorldToShip().transformDirection(
                    new Vector3d(approach.x, approach.y, approach.z), new Vector3d());
            double ax = Math.abs(local.x()), ay = Math.abs(local.y()), az = Math.abs(local.z());

            if (ay >= ax && ay >= az) return local.y < 0.0D ? CrashPhysicsEngine.ImpactPart.LIFT : CrashPhysicsEngine.ImpactPart.HULL;
            if (az >= ax)             return local.z >= 0.0D ? CrashPhysicsEngine.ImpactPart.HULL : CrashPhysicsEngine.ImpactPart.ENGINE;
            return CrashPhysicsEngine.ImpactPart.CONTROL;
        }

        private static String prettySeverity(CrashPhysicsEngine.CrashSeverity s) {
            return switch (s) {
                case SCRAPE -> "scrape"; case HARD -> "hard";
                case CATASTROPHIC -> "catastrophic"; case NONE -> "non-event";
            };
        }

        private static BlockPos localToWorldPos(LoadedServerShip ship, Vector3d local) {
            Vector3d world = ship.getTransform().getShipToWorld().transformPosition(local, new Vector3d());
            return BlockPos.containing(world.x(), world.y(), world.z());
        }

        private static ShipBounds getShipBounds(LoadedServerShip ship) {
            try {
                Object bounds = ship.getClass().getMethod("getShipAABB").invoke(ship);
                if (bounds == null) return null;
                return new ShipBounds(
                        readBound(bounds, "minX"), readBound(bounds, "minY"), readBound(bounds, "minZ"),
                        readBound(bounds, "maxX"), readBound(bounds, "maxY"), readBound(bounds, "maxZ")
                );
            } catch (ReflectiveOperationException | ClassCastException ex) { return null; }
        }

        private static double readBound(Object bounds, String accessor) throws ReflectiveOperationException {
            Class<?> cls = bounds.getClass();
            try { return ((Number) cls.getMethod(accessor).invoke(bounds)).doubleValue(); }
            catch (NoSuchMethodException ignored) {}
            String getter = "get" + Character.toUpperCase(accessor.charAt(0)) + accessor.substring(1);
            try { return ((Number) cls.getMethod(getter).invoke(bounds)).doubleValue(); }
            catch (NoSuchMethodException ignored) {}
            return ((Number) cls.getField(accessor).get(bounds)).doubleValue();
        }

        private static final class RuntimeShipState {
            Vec3 lastVelocity;
            long lastCriticalWarningTick = Long.MIN_VALUE;
            int postCrashDampingTicks;
            double postCrashDamping;
            double leftWingDamage;
            double rightWingDamage;
            int groundSettleTicks;
            double groundSettleStrength;
        }
    }





    public static final class Commands {
        private Commands() {}

        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
            dispatcher.register(
                    net.minecraft.commands.Commands.literal("vsmd")
                            .requires(src -> src.hasPermission(2))
                            .then(net.minecraft.commands.Commands.literal("pair")
                                    .then(net.minecraft.commands.Commands.argument("shipId", LongArgumentType.longArg(0L))
                                            .then(net.minecraft.commands.Commands.argument("player", EntityArgument.player())
                                                    .executes(ctx -> {
                                                        long shipId = LongArgumentType.getLong(ctx, "shipId");
                                                        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                        BindingRecord record = registry(ctx.getSource()).pairShip(shipId, player.getUUID());
                                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                                "Paired ship " + shipId + " with " + player.getGameProfile().getName()
                                                                        + " | slug=" + record.slug()), true);
                                                        return 1;
                                                    }))))
                            .then(net.minecraft.commands.Commands.literal("rename")
                                    .then(net.minecraft.commands.Commands.argument("shipId", LongArgumentType.longArg(0L))
                                            .then(net.minecraft.commands.Commands.argument("name", StringArgumentType.greedyString())
                                                    .executes(ctx -> {
                                                        long shipId = LongArgumentType.getLong(ctx, "shipId");
                                                        String name = StringArgumentType.getString(ctx, "name").trim();
                                                        if (name.isBlank()) {
                                                            ctx.getSource().sendFailure(Component.literal("Name cannot be blank."));
                                                            return 0;
                                                        }
                                                        BindingRecord record = registry(ctx.getSource()).renameShip(shipId, name);
                                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                                "Renamed ship " + shipId + " -> \"" + record.displayName()
                                                                        + "\" | slug=" + record.slug()), true);
                                                        return 1;
                                                    }))))
                            .then(net.minecraft.commands.Commands.literal("creator")
                                    .then(net.minecraft.commands.Commands.argument("shipId", LongArgumentType.longArg(0L))
                                            .then(net.minecraft.commands.Commands.argument("player", EntityArgument.player())
                                                    .executes(ctx -> {
                                                        long shipId = LongArgumentType.getLong(ctx, "shipId");
                                                        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                        BindingRecord record = registry(ctx.getSource()).setCreator(shipId, player.getUUID());
                                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                                "Set creator for ship " + shipId + " -> " + player.getGameProfile().getName()), true);
                                                        return record.createdBy() != null ? 1 : 0;
                                                    })))
                                    .then(net.minecraft.commands.Commands.literal("inferOwner")
                                            .then(net.minecraft.commands.Commands.argument("shipId", LongArgumentType.longArg(0L))
                                                    .executes(ctx -> {
                                                        long shipId = LongArgumentType.getLong(ctx, "shipId");
                                                        Registry store = registry(ctx.getSource());
                                                        BindingRecord record = store.getOrCreate(shipId);
                                                        if (record.owner() == null) {
                                                            ctx.getSource().sendFailure(Component.literal("Ship has no owner to infer creator from."));
                                                            return 0;
                                                        }
                                                        if (record.createdBy() != null) {
                                                            ctx.getSource().sendFailure(Component.literal("Ship already has a creator."));
                                                            return 0;
                                                        }
                                                        store.setCreatorIfMissing(shipId, record.owner());
                                                        ctx.getSource().sendSuccess(() -> Component.literal("Creator inferred from owner for ship " + shipId), true);
                                                        return 1;
                                                    }))))
                            .then(net.minecraft.commands.Commands.literal("crash")
                                    .then(net.minecraft.commands.Commands.argument("shipId", LongArgumentType.longArg(0L))
                                            .executes(ctx -> {
                                                long shipId = LongArgumentType.getLong(ctx, "shipId");
                                                BindingRecord record = registry(ctx.getSource()).markCrashed(shipId);
                                                ctx.getSource().sendSuccess(() -> Component.literal(
                                                        "Ship " + shipId + " marked crashed | slug=" + record.slug()), true);
                                                return 1;
                                            })))
                            .then(net.minecraft.commands.Commands.literal("recover")
                                    .then(net.minecraft.commands.Commands.argument("shipId", LongArgumentType.longArg(0L))
                                            .executes(ctx -> {
                                                long shipId = LongArgumentType.getLong(ctx, "shipId");
                                                BindingRecord record = registry(ctx.getSource()).recoverShip(shipId);
                                                ctx.getSource().sendSuccess(() -> Component.literal(
                                                        "Ship " + shipId + " recovered | slug=" + record.slug()), true);
                                                return 1;
                                            })))
                            .then(net.minecraft.commands.Commands.literal("physics")
                                    .then(net.minecraft.commands.Commands.literal("sample")
                                            .then(net.minecraft.commands.Commands.argument("shipId", LongArgumentType.longArg(0L))
                                                    .then(net.minecraft.commands.Commands.argument("vx", DoubleArgumentType.doubleArg())
                                                            .then(net.minecraft.commands.Commands.argument("vy", DoubleArgumentType.doubleArg())
                                                                    .then(net.minecraft.commands.Commands.argument("vz", DoubleArgumentType.doubleArg())
                                                                            .then(net.minecraft.commands.Commands.argument("mass", DoubleArgumentType.doubleArg(1.0D))
                                                                                    .then(net.minecraft.commands.Commands.argument("collision", BoolArgumentType.bool())
                                                                                            .executes(ctx -> {
                                                                                                long shipId = LongArgumentType.getLong(ctx, "shipId");
                                                                                                double vx = DoubleArgumentType.getDouble(ctx, "vx");
                                                                                                double vy = DoubleArgumentType.getDouble(ctx, "vy");
                                                                                                double vz = DoubleArgumentType.getDouble(ctx, "vz");
                                                                                                double mass = DoubleArgumentType.getDouble(ctx, "mass");
                                                                                                boolean collision = BoolArgumentType.getBool(ctx, "collision");
                                                                                                return applyManualSample(ctx.getSource(), shipId,
                                                                                                        new Vec3(vx, vy, vz), mass, collision,
                                                                                                        CrashPhysicsEngine.ImpactPart.AUTO);
                                                                                            })
                                                                                            .then(net.minecraft.commands.Commands.argument("impactPart", StringArgumentType.word())
                                                                                                    .executes(ctx -> {
                                                                                                        long shipId = LongArgumentType.getLong(ctx, "shipId");
                                                                                                        double vx = DoubleArgumentType.getDouble(ctx, "vx");
                                                                                                        double vy = DoubleArgumentType.getDouble(ctx, "vy");
                                                                                                        double vz = DoubleArgumentType.getDouble(ctx, "vz");
                                                                                                        double mass = DoubleArgumentType.getDouble(ctx, "mass");
                                                                                                        boolean collision = BoolArgumentType.getBool(ctx, "collision");
                                                                                                        CrashPhysicsEngine.ImpactPart part = CrashPhysicsEngine.ImpactPart.parseOrAuto(
                                                                                                                StringArgumentType.getString(ctx, "impactPart"));
                                                                                                        return applyManualSample(ctx.getSource(), shipId,
                                                                                                                new Vec3(vx, vy, vz), mass, collision, part);
                                                                                                    }))))))))
                                            .then(net.minecraft.commands.Commands.literal("sample_here")
                                                    .then(net.minecraft.commands.Commands.argument("collision", BoolArgumentType.bool())
                                                            .executes(ctx -> {
                                                                boolean collision = BoolArgumentType.getBool(ctx, "collision");
                                                                CommandSourceStack src = ctx.getSource();
                                                                ServerLevel level = src.getLevel();
                                                                Vec3 pos = src.getPosition();
                                                                LoadedServerShip ship = VSGameUtilsKt.getShipObjectManagingPos(level, pos.x, pos.y, pos.z);
                                                                if (ship == null) {
                                                                    src.sendFailure(Component.literal("No VS ship at your position."));
                                                                    return 0;
                                                                }
                                                                Vector3dc vel = ship.getVelocity();
                                                                return applyManualSample(src, ship.getId(),
                                                                        new Vec3(vel.x(), vel.y(), vel.z()),
                                                                        ship.getInertiaData().getShipMass(), collision,
                                                                        CrashPhysicsEngine.ImpactPart.AUTO);
                                                            })
                                                            .then(net.minecraft.commands.Commands.argument("impactPart", StringArgumentType.word())
                                                                    .executes(ctx -> {
                                                                        boolean collision = BoolArgumentType.getBool(ctx, "collision");
                                                                        CommandSourceStack src = ctx.getSource();
                                                                        ServerLevel level = src.getLevel();
                                                                        Vec3 pos = src.getPosition();
                                                                        CrashPhysicsEngine.ImpactPart part = CrashPhysicsEngine.ImpactPart.parseOrAuto(
                                                                                StringArgumentType.getString(ctx, "impactPart"));
                                                                        LoadedServerShip ship = VSGameUtilsKt.getShipObjectManagingPos(level, pos.x, pos.y, pos.z);
                                                                        if (ship == null) {
                                                                            src.sendFailure(Component.literal("No VS ship at your position."));
                                                                            return 0;
                                                                        }
                                                                        Vector3dc vel = ship.getVelocity();
                                                                        return applyManualSample(src, ship.getId(),
                                                                                new Vec3(vel.x(), vel.y(), vel.z()),
                                                                                ship.getInertiaData().getShipMass(), collision, part);
                                                                    })))))
                                    .then(net.minecraft.commands.Commands.literal("show")
                                            .then(net.minecraft.commands.Commands.argument("shipId", LongArgumentType.longArg(0L))
                                                    .executes(ctx -> {
                                                        long shipId = LongArgumentType.getLong(ctx, "shipId");
                                                        BindingRecord record = registry(ctx.getSource()).getShip(shipId);
                                                        if (record == null) {
                                                            ctx.getSource().sendFailure(Component.literal("No record for ship " + shipId));
                                                            return 0;
                                                        }
                                                        ctx.getSource().sendSuccess(() -> Component.literal(format(record)), false);
                                                        return 1;
                                                    })))
                                    .then(net.minecraft.commands.Commands.literal("list")
                                            .executes(ctx -> {
                                                Registry store = registry(ctx.getSource());
                                                if (store.allShips().isEmpty()) {
                                                    ctx.getSource().sendSuccess(() -> Component.literal("No tracked ships yet."), false);
                                                    return 1;
                                                }
                                                ctx.getSource().sendSuccess(() -> Component.literal("Tracked ships:"), false);
                                                store.allShips().stream()
                                                        .sorted(Comparator.comparingLong(BindingRecord::shipId))
                                                        .limit(50)
                                                        .forEach(r -> ctx.getSource().sendSuccess(
                                                                () -> Component.literal(" - " + format(r)), false));
                                                return 1;
                                            }))
                            ));
        }

        private static Registry registry(CommandSourceStack src) {
            return Registry.get(src.getServer());
        }

        private static int applyManualSample(
                CommandSourceStack src, long shipId, Vec3 velocity,
                double mass, boolean collision, CrashPhysicsEngine.ImpactPart impactPart
        ) {
            Hooks.PhysicsSampleOutcome outcome = Hooks.onShipPhysicsSampleDetailed(
                    src.getServer(), shipId, src.getServer().getTickCount(),
                    velocity, mass, collision, impactPart);
            BindingRecord record = outcome.record();
            CrashPhysicsEngine.CrashResult result = outcome.result();

            src.sendSuccess(() -> Component.literal("Physics sample applied"
                    + " | shipId=" + shipId
                    + " | impactPart=" + impactPart.name().toLowerCase()
                    + " | crashed=" + record.crashed()
                    + " | severity=" + result.severity().name().toLowerCase()
                    + " | damage=" + String.format("%.2f", result.damage())
                    + " | damping=" + String.format("%.2f", result.bounceDamping())
                    + " | integrity=" + String.format("%.2f", record.structuralIntegrity())
                    + " | failedParts=" + record.failedPartsSummary()
                    + " | slug=" + record.slug()), true);
            return 1;
        }

        private static String format(BindingRecord r) {
            return "id=" + r.shipId()
                    + " name=\"" + r.displayName() + "\""
                    + " slug=" + r.slug()
                    + " crashed=" + r.crashed()
                    + " crashes=" + r.crashCount()
                    + " integrity=" + String.format("%.2f", r.structuralIntegrity())
                    + " lastSeverity=" + r.lastCrashSeverity()
                    + " lastImpactPart=" + r.lastImpactPart()
                    + " lastEnergy=" + String.format("%.2f", r.lastImpactEnergy())
                    + " peakEnergy=" + String.format("%.2f", r.peakImpactEnergy())
                    + " lastCrashTick=" + r.lastCrashTick()
                    + " engine=" + String.format("%.2f", r.engineHealth())
                    + " lift=" + String.format("%.2f", r.liftHealth())
                    + " control=" + String.format("%.2f", r.controlHealth())
                    + " hull=" + String.format("%.2f", r.hullHealth())
                    + " failedCount=" + r.failedPartCount()
                    + " failedParts=" + r.failedPartsSummary()
                    + " owner=" + (r.owner() == null ? "none" : r.owner())
                    + " creator=" + (r.createdBy() == null ? "unknown" : r.createdBy());
        }
    }





    static ShipBounds getShipBounds(LoadedServerShip ship) {
        try {
            Object bounds = ship.getClass().getMethod("getShipAABB").invoke(ship);
            if (bounds == null) return null;
            return new ShipBounds(
                    readBound(bounds, "minX"), readBound(bounds, "minY"), readBound(bounds, "minZ"),
                    readBound(bounds, "maxX"), readBound(bounds, "maxY"), readBound(bounds, "maxZ")
            );
        } catch (ReflectiveOperationException | ClassCastException ex) { return null; }
    }

    static double readBound(Object bounds, String accessor) throws ReflectiveOperationException {
        Class<?> cls = bounds.getClass();
        try { return ((Number) cls.getMethod(accessor).invoke(bounds)).doubleValue(); }
        catch (NoSuchMethodException ignored) {}
        String getter = "get" + Character.toUpperCase(accessor.charAt(0)) + accessor.substring(1);
        try { return ((Number) cls.getMethod(getter).invoke(bounds)).doubleValue(); }
        catch (NoSuchMethodException ignored) {}
        return ((Number) cls.getField(accessor).get(bounds)).doubleValue();
    }

    record ShipBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}
}