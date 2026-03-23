package com.vskinetic.ship;

import com.vskinetic.Config;
import net.minecraft.nbt.CompoundTag;

import java.util.Locale;
import java.util.Random;
import java.util.UUID;

public class ShipBindingRecord {
    private static final String KEY_SHIP_ID = "ShipId";
    private static final String KEY_OWNER = "Owner";
    private static final String KEY_HAS_OWNER = "HasOwner";
    private static final String KEY_CREATED_BY = "CreatedBy";
    private static final String KEY_HAS_CREATED_BY = "HasCreatedBy";
    private static final String KEY_DISPLAY_NAME = "DisplayName";
    private static final String KEY_SLUG = "Slug";
    private static final String KEY_CRASHED = "Crashed";
    private static final String KEY_CRASH_COUNT = "CrashCount";
    private static final String KEY_STRUCTURAL_INTEGRITY = "StructuralIntegrity";
    private static final String KEY_LAST_IMPACT_ENERGY = "LastImpactEnergy";
    private static final String KEY_PEAK_IMPACT_ENERGY = "PeakImpactEnergy";
    private static final String KEY_LAST_CRASH_TICK = "LastCrashTick";
    private static final String KEY_LAST_CRASH_SEVERITY = "LastCrashSeverity";
    private static final String KEY_LAST_IMPACT_PART = "LastImpactPart";
    private static final String KEY_ENGINE_HEALTH = "EngineHealth";
    private static final String KEY_LIFT_HEALTH = "LiftHealth";
    private static final String KEY_CONTROL_HEALTH = "ControlHealth";
    private static final String KEY_HULL_HEALTH = "HullHealth";

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

    public ShipBindingRecord(
            long shipId,
            UUID owner,
            UUID createdBy,
            String displayName,
            String slug,
            boolean crashed,
            int crashCount,
            double structuralIntegrity,
            double lastImpactEnergy,
            double peakImpactEnergy,
            long lastCrashTick,
            String lastCrashSeverity,
            String lastImpactPart,
            double engineHealth,
            double liftHealth,
            double controlHealth,
            double hullHealth
    ) {
        this.shipId = shipId;
        this.owner = owner;
        this.createdBy = createdBy;
        this.displayName = displayName;
        this.slug = slug;
        this.crashed = crashed;
        this.crashCount = crashCount;
        this.structuralIntegrity = clampIntegrity(structuralIntegrity);
        this.lastImpactEnergy = Math.max(0.0D, lastImpactEnergy);
        this.peakImpactEnergy = Math.max(0.0D, peakImpactEnergy);
        this.lastCrashTick = lastCrashTick;
        this.lastCrashSeverity = sanitizeSeverity(lastCrashSeverity);
        this.lastImpactPart = sanitizeImpactPart(lastImpactPart);
        this.engineHealth = clampHealth(engineHealth);
        this.liftHealth = clampHealth(liftHealth);
        this.controlHealth = clampHealth(controlHealth);
        this.hullHealth = clampHealth(hullHealth);
    }

    public static ShipBindingRecord createDefault(long shipId) {
        String displayName = ShipNaming.defaultDisplayName(shipId);
        String slug = ShipNaming.slugify(displayName);
        return new ShipBindingRecord(
                shipId,
                null,
                null,
                displayName,
                slug,
                false,
                0,
                100.0D,
                0.0D,
                0.0D,
                -1L,
                "none",
                "auto",
                100.0D,
                100.0D,
                100.0D,
                100.0D
        );
    }

    public static ShipBindingRecord fromTag(CompoundTag tag) {
        long shipId = tag.getLong(KEY_SHIP_ID);
        UUID owner = tag.getBoolean(KEY_HAS_OWNER) ? tag.getUUID(KEY_OWNER) : null;
        UUID createdBy = tag.getBoolean(KEY_HAS_CREATED_BY) ? tag.getUUID(KEY_CREATED_BY) : null;
        String displayName = tag.getString(KEY_DISPLAY_NAME);
        String slug = tag.getString(KEY_SLUG);
        boolean crashed = tag.getBoolean(KEY_CRASHED);
        int crashCount = tag.getInt(KEY_CRASH_COUNT);
        double structuralIntegrity = tag.contains(KEY_STRUCTURAL_INTEGRITY) ? tag.getDouble(KEY_STRUCTURAL_INTEGRITY) : 100.0D;
        double lastImpactEnergy = tag.getDouble(KEY_LAST_IMPACT_ENERGY);
        double peakImpactEnergy = tag.getDouble(KEY_PEAK_IMPACT_ENERGY);
        long lastCrashTick = tag.contains(KEY_LAST_CRASH_TICK) ? tag.getLong(KEY_LAST_CRASH_TICK) : -1L;
        String lastCrashSeverity = tag.getString(KEY_LAST_CRASH_SEVERITY);
        String lastImpactPart = tag.getString(KEY_LAST_IMPACT_PART);
        double engineHealth = tag.contains(KEY_ENGINE_HEALTH) ? tag.getDouble(KEY_ENGINE_HEALTH) : 100.0D;
        double liftHealth = tag.contains(KEY_LIFT_HEALTH) ? tag.getDouble(KEY_LIFT_HEALTH) : 100.0D;
        double controlHealth = tag.contains(KEY_CONTROL_HEALTH) ? tag.getDouble(KEY_CONTROL_HEALTH) : 100.0D;
        double hullHealth = tag.contains(KEY_HULL_HEALTH) ? tag.getDouble(KEY_HULL_HEALTH) : 100.0D;

        if (displayName.isBlank()) {
            displayName = ShipNaming.defaultDisplayName(shipId);
        }
        if (slug.isBlank()) {
            slug = ShipNaming.slugify(displayName);
        }
        if (crashed) {
            slug = ShipNaming.withCrashSuffix(ShipNaming.withoutCrashSuffix(slug));
        }

        return new ShipBindingRecord(
                shipId,
                owner,
                createdBy,
                displayName,
                slug,
                crashed,
                crashCount,
                structuralIntegrity,
                lastImpactEnergy,
                peakImpactEnergy,
                lastCrashTick,
                lastCrashSeverity,
                lastImpactPart,
                engineHealth,
                liftHealth,
                controlHealth,
                hullHealth
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

    public long shipId() {
        return shipId;
    }

    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public void setCreator(UUID creator) {
        this.createdBy = creator;
    }

    public boolean setCreatorIfMissing(UUID creator) {
        if (this.createdBy != null || creator == null) {
            return false;
        }
        this.createdBy = creator;
        return true;
    }

    public String displayName() {
        return displayName;
    }

    public void rename(String newDisplayName) {
        this.displayName = newDisplayName;
        String baseSlug = ShipNaming.slugify(newDisplayName);
        this.slug = crashed ? ShipNaming.withCrashSuffix(baseSlug) : baseSlug;
    }

    public String slug() {
        return slug;
    }

    public boolean crashed() {
        return crashed;
    }

    public void setCrashed(boolean crashed) {
        this.crashed = crashed;
        String baseSlug = ShipNaming.withoutCrashSuffix(slug);
        this.slug = crashed ? ShipNaming.withCrashSuffix(baseSlug) : baseSlug;
    }

    public int crashCount() {
        return crashCount;
    }

    public double structuralIntegrity() {
        return structuralIntegrity;
    }

    public double lastImpactEnergy() {
        return lastImpactEnergy;
    }

    public double peakImpactEnergy() {
        return peakImpactEnergy;
    }

    public long lastCrashTick() {
        return lastCrashTick;
    }

    public String lastCrashSeverity() {
        return lastCrashSeverity;
    }

    public String lastImpactPart() {
        return lastImpactPart;
    }

    public double engineHealth() {
        return engineHealth;
    }

    public double liftHealth() {
        return liftHealth;
    }

    public double controlHealth() {
        return controlHealth;
    }

    public double hullHealth() {
        return hullHealth;
    }

    public int failedPartCount() {
        int failures = 0;
        if (isSubsystemFailed(engineHealth)) {
            failures++;
        }
        if (isSubsystemFailed(liftHealth)) {
            failures++;
        }
        if (isSubsystemFailed(controlHealth)) {
            failures++;
        }
        if (isSubsystemFailed(hullHealth)) {
            failures++;
        }
        return failures;
    }

    public String failedPartsSummary() {
        StringBuilder sb = new StringBuilder();
        appendFailedPart(sb, "engine", engineHealth);
        appendFailedPart(sb, "lift", liftHealth);
        appendFailedPart(sb, "control", controlHealth);
        appendFailedPart(sb, "hull", hullHealth);
        return sb.length() == 0 ? "none" : sb.toString();
    }

    public void applyCrash(CrashPhysicsEngine.CrashResult result, long gameTime) {
        applyCrash(result, gameTime, ImpactPart.AUTO);
    }

    public void applyCrash(CrashPhysicsEngine.CrashResult result, long gameTime, ImpactPart impactPart) {
        this.crashed = true;
        this.crashCount++;
        this.lastImpactEnergy = Math.max(0.0D, result.impactEnergy());
        this.peakImpactEnergy = Math.max(this.peakImpactEnergy, this.lastImpactEnergy);
        this.lastCrashTick = gameTime;
        this.lastCrashSeverity = sanitizeSeverity(result.severity().name());
        this.lastImpactPart = sanitizeImpactPart(impactPart.name());
        this.structuralIntegrity = clampIntegrity(this.structuralIntegrity - Math.max(0.0D, result.damage()));
        applySubsystemDamage(result, gameTime, impactPart);

        String baseSlug = ShipNaming.withoutCrashSuffix(slug);
        this.slug = ShipNaming.withCrashSuffix(baseSlug);
    }

    public void recover(double minimumIntegrity, double partRecoveryFloor) {
        setCrashed(false);
        this.structuralIntegrity = Math.max(this.structuralIntegrity, clampIntegrity(minimumIntegrity));
        double repairedPartFloor = clampHealth(partRecoveryFloor);
        this.engineHealth = Math.max(this.engineHealth, repairedPartFloor);
        this.liftHealth = Math.max(this.liftHealth, repairedPartFloor);
        this.controlHealth = Math.max(this.controlHealth, repairedPartFloor);
        this.hullHealth = Math.max(this.hullHealth, repairedPartFloor);
    }

    private void applySubsystemDamage(CrashPhysicsEngine.CrashResult result, long gameTime, ImpactPart impactPart) {
        double severityScale = switch (result.severity()) {
            case SCRAPE -> 0.45D;
            case HARD -> 1.0D;
            case CATASTROPHIC -> 1.65D;
            case NONE -> 0.0D;
        };
        double baseDamage = result.damage() * severityScale * Math.max(0.0D, Config.partDamageMultiplier);
        if (baseDamage <= 0.0D) {
            return;
        }

        Random random = new Random(shipId ^ (gameTime * 31L) ^ (long) (result.crashScore() * 1000.0D) ^ crashCount);
        boolean catastrophic = result.severity() == CrashPhysicsEngine.CrashSeverity.CATASTROPHIC;
        ImpactPart resolvedImpactPart = impactPart == null ? ImpactPart.AUTO : impactPart;
        if (resolvedImpactPart == ImpactPart.AUTO) {
            resolvedImpactPart = ImpactPart.HULL;
        }

        double primaryShare = Math.max(0.0D, Math.min(1.0D, Config.primaryCrumpleShare));
        double spillShare = 1.0D - primaryShare;
        double primaryDamage = baseDamage * primaryShare;
        double spillDamage = baseDamage * spillShare;

        switch (resolvedImpactPart) {
            case ENGINE -> {
                engineHealth = applySubsystemDamage(engineHealth, primaryDamage, catastrophic, random, true);
                controlHealth = applySubsystemDamage(controlHealth, spillDamage * 0.55D, catastrophic, random, false);
                hullHealth = applySubsystemDamage(hullHealth, spillDamage * 0.45D, catastrophic, random, false);
            }
            case LIFT -> {
                liftHealth = applySubsystemDamage(liftHealth, primaryDamage, catastrophic, random, true);
                hullHealth = applySubsystemDamage(hullHealth, spillDamage * 0.60D, catastrophic, random, false);
                controlHealth = applySubsystemDamage(controlHealth, spillDamage * 0.40D, catastrophic, random, false);
            }
            case CONTROL -> {
                controlHealth = applySubsystemDamage(controlHealth, primaryDamage, catastrophic, random, true);
                engineHealth = applySubsystemDamage(engineHealth, spillDamage * 0.50D, catastrophic, random, false);
                liftHealth = applySubsystemDamage(liftHealth, spillDamage * 0.50D, catastrophic, random, false);
            }
            case HULL, AUTO -> {
                hullHealth = applySubsystemDamage(hullHealth, primaryDamage, catastrophic, random, true);
                engineHealth = applySubsystemDamage(engineHealth, spillDamage * 0.34D, catastrophic, random, false);
                liftHealth = applySubsystemDamage(liftHealth, spillDamage * 0.33D, catastrophic, random, false);
                controlHealth = applySubsystemDamage(controlHealth, spillDamage * 0.33D, catastrophic, random, false);
            }
        }
    }

    private static double applySubsystemDamage(
            double currentHealth,
            double scaledDamage,
            boolean catastrophic,
            Random random,
            boolean primaryImpactPart
    ) {
        double variance = 0.70D + random.nextDouble() * 0.80D;
        double nextHealth = clampHealth(currentHealth - (scaledDamage * variance));

        if (catastrophic && random.nextDouble() < Config.catastrophicPartFailureChance) {
            double primaryBonus = primaryImpactPart ? 15.0D : 0.0D;
            double forcedFailureHealth = Math.max(
                    0.0D,
                    Config.partFailureHealthThreshold - (5.0D + primaryBonus + random.nextDouble() * 20.0D)
            );
            nextHealth = Math.min(nextHealth, forcedFailureHealth);
        }
        return nextHealth;
    }

    private static String sanitizeSeverity(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String sanitizeImpactPart(String value) {
        if (value == null || value.isBlank()) {
            return "auto";
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static double clampIntegrity(double integrity) {
        return Math.max(0.0D, Math.min(100.0D, integrity));
    }

    private static double clampHealth(double health) {
        return Math.max(0.0D, Math.min(100.0D, health));
    }

    private static boolean isSubsystemFailed(double health) {
        return health <= Config.partFailureHealthThreshold;
    }

    private static void appendFailedPart(StringBuilder sb, String partName, double health) {
        if (!isSubsystemFailed(health)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(',');
        }
        sb.append(partName);
    }
}
