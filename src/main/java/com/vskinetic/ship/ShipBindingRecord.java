package com.vskinetic.ship;

import net.minecraft.nbt.CompoundTag;

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
            String lastCrashSeverity
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
    }

    public static ShipBindingRecord createDefault(long shipId) {
        String displayName = ShipNaming.defaultDisplayName(shipId);
        String slug = ShipNaming.slugify(displayName);
        return new ShipBindingRecord(shipId, null, null, displayName, slug, false, 0, 100.0D, 0.0D, 0.0D, -1L, "none");
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
                lastCrashSeverity
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

    public void applyCrash(CrashPhysicsEngine.CrashResult result, long gameTime) {
        this.crashed = true;
        this.crashCount++;
        this.lastImpactEnergy = Math.max(0.0D, result.impactEnergy());
        this.peakImpactEnergy = Math.max(this.peakImpactEnergy, this.lastImpactEnergy);
        this.lastCrashTick = gameTime;
        this.lastCrashSeverity = sanitizeSeverity(result.severity().name());
        this.structuralIntegrity = clampIntegrity(this.structuralIntegrity - Math.max(0.0D, result.damage()));

        String baseSlug = ShipNaming.withoutCrashSuffix(slug);
        this.slug = ShipNaming.withCrashSuffix(baseSlug);
    }

    public void recover(double minimumIntegrity) {
        setCrashed(false);
        this.structuralIntegrity = Math.max(this.structuralIntegrity, clampIntegrity(minimumIntegrity));
    }

    private static String sanitizeSeverity(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        return value.toLowerCase();
    }

    private static double clampIntegrity(double integrity) {
        return Math.max(0.0D, Math.min(100.0D, integrity));
    }
}
