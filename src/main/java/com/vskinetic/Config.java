package com.vskinetic;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;


@Mod.EventBusSubscriber(modid = KineticMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.DoubleValue MIN_CRASH_SPEED = BUILDER
            .comment("Minimum linear speed required before collisions can trigger a crash.")
            .defineInRange("minCrashSpeed", 12.0D, 0.0D, 1_000_000.0D);

    private static final ForgeConfigSpec.DoubleValue MIN_DELTA_V = BUILDER
            .comment("Minimum speed change between ticks required for a hard-impact crash.")
            .defineInRange("minDeltaV", 7.5D, 0.0D, 1_000_000.0D);

    private static final ForgeConfigSpec.DoubleValue MIN_IMPACT_ENERGY = BUILDER
            .comment("Minimum impact energy threshold (0.5 * mass * deltaV^2).")
            .defineInRange("minImpactEnergy", 180000.0D, 0.0D, Double.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue CRASH_COOLDOWN_TICKS = BUILDER
            .comment("Cooldown in ticks after a crash trigger to prevent repeated tagging spam.")
            .defineInRange("crashCooldownTicks", 40, 0, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.BooleanValue REQUIRE_COLLISION_SIGNAL = BUILDER
            .comment("If true, crash checks only trigger when collision=true is supplied.")
            .define("requireCollisionSignal", true);

    private static final ForgeConfigSpec.DoubleValue HARD_CRASH_SCORE = BUILDER
            .comment("Crash score threshold where impacts become hard crashes.")
            .defineInRange("hardCrashScore", 1.35D, 1.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue CATASTROPHIC_CRASH_SCORE = BUILDER
            .comment("Crash score threshold where impacts become catastrophic crashes.")
            .defineInRange("catastrophicCrashScore", 2.20D, 1.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue CRASH_DAMAGE_MULTIPLIER = BUILDER
            .comment("Global multiplier applied to crash damage.")
            .defineInRange("crashDamageMultiplier", 1.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue RECOVERY_INTEGRITY_FLOOR = BUILDER
            .comment("When recovering a ship, integrity is restored to at least this value.")
            .defineInRange("recoveryIntegrityFloor", 35.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue PART_DAMAGE_MULTIPLIER = BUILDER
            .comment("Global multiplier applied to subsystem damage during crashes.")
            .defineInRange("partDamageMultiplier", 1.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue PART_FAILURE_HEALTH_THRESHOLD = BUILDER
            .comment("Subsystem health at or below this value is considered failed.")
            .defineInRange("partFailureHealthThreshold", 30.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue CATASTROPHIC_PART_FAILURE_CHANCE = BUILDER
            .comment("Additional chance for immediate subsystem failure on catastrophic impacts.")
            .defineInRange("catastrophicPartFailureChance", 0.35D, 0.0D, 1.0D);

    private static final ForgeConfigSpec.DoubleValue PART_RECOVERY_FLOOR = BUILDER
            .comment("When recovering a ship, each subsystem is restored to at least this value.")
            .defineInRange("partRecoveryFloor", 45.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue SCRAPE_EXPLOSION_DAMPING = BUILDER
            .comment("Bounce damping used for scrape impacts.")
            .defineInRange("scrapeExplosionDamping", 0.80D, 0.0D, 20.0D);

    private static final ForgeConfigSpec.DoubleValue HARD_EXPLOSION_DAMPING = BUILDER
            .comment("Bounce damping used for hard impacts.")
            .defineInRange("hardExplosionDamping", 1.80D, 0.0D, 20.0D);

    private static final ForgeConfigSpec.DoubleValue CATASTROPHIC_EXPLOSION_DAMPING = BUILDER
            .comment("Bounce damping used for catastrophic impacts.")
            .defineInRange("catastrophicExplosionDamping", 3.00D, 0.0D, 20.0D);

    private static final ForgeConfigSpec.DoubleValue PRIMARY_CRUMPLE_SHARE = BUILDER
            .comment("How much subsystem damage goes to the first impacted part.")
            .defineInRange("primaryCrumpleShare", 0.72D, 0.0D, 1.0D);

    private static final ForgeConfigSpec.DoubleValue LOW_INTEGRITY_THRESHOLD = BUILDER
            .comment("Integrity threshold where damaged ships start suffering ongoing drag.")
            .defineInRange("lowIntegrityThreshold", 45.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue CRITICAL_INTEGRITY_THRESHOLD = BUILDER
            .comment("Integrity threshold where damaged ships suffer severe drag after crashes.")
            .defineInRange("criticalIntegrityThreshold", 20.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue LOW_INTEGRITY_DECELERATION = BUILDER
            .comment("Approximate world-space deceleration applied to low-integrity ships each tick.")
            .defineInRange("lowIntegrityDeceleration", 0.35D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue CRITICAL_INTEGRITY_DECELERATION = BUILDER
            .comment("Approximate world-space deceleration applied to critically damaged ships each tick.")
            .defineInRange("criticalIntegrityDeceleration", 1.10D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue CRASH_NOTIFICATION_RADIUS = BUILDER
            .comment("Radius in blocks for crash alerts sent to nearby players.")
            .defineInRange("crashNotificationRadius", 96.0D, 0.0D, 100000.0D);

    private static final ForgeConfigSpec.DoubleValue DECK_IMPACT_VERTICAL_SPEED = BUILDER
            .comment("Minimum downward speed before deck impacts can crumple the ship nose.")
            .defineInRange("deckImpactVerticalSpeed", 8.0D, 0.0D, 1000.0D);

    private static final ForgeConfigSpec.DoubleValue FRONT_CRUMPLE_RADIUS = BUILDER
            .comment("Base radius in blocks for front-of-ship crumple damage.")
            .defineInRange("frontCrumpleRadius", 2.4D, 0.0D, 64.0D);

    private static final ForgeConfigSpec.DoubleValue FRONT_CRUMPLE_DEPTH = BUILDER
            .comment("Base depth in blocks for front-of-ship crumple damage.")
            .defineInRange("frontCrumpleDepth", 4.0D, 0.0D, 128.0D);

    private static final ForgeConfigSpec.IntValue CATASTROPHIC_EXPLOSION_DELAY_TICKS = BUILDER
            .comment("Delay in ticks between catastrophic front impacts and the follow-up explosion.")
            .defineInRange("catastrophicExplosionDelayTicks", 8, 0, 1200);

    private static final ForgeConfigSpec.DoubleValue CATASTROPHIC_EXPLOSION_POWER = BUILDER
            .comment("Minimum explosion power for catastrophic front impacts.")
            .defineInRange("catastrophicExplosionPower", 4.5D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue BELLY_SCRAPE_BLOCKS_PER_MPS = BUILDER
            .comment("How many blocks of belly scrape damage are applied per m/s of horizontal speed.")
            .defineInRange("bellyScrapeBlocksPerMps", 0.35D, 0.0D, 10.0D);

    private static final ForgeConfigSpec.DoubleValue GEAR_COLLAPSE_DESTRUCTION_FACTOR = BUILDER
            .comment("Fraction of bottom-hull blocks destroyed during a gear collapse on HARD crashes (CATASTROPHIC always destroys all).")
            .defineInRange("gearCollapseDestructionFactor", 0.45D, 0.0D, 1.0D);

    private static final ForgeConfigSpec.DoubleValue TERRAIN_BREACH_FACTOR = BUILDER
            .comment("Multiplier for how aggressively crashes carve terrain/walls.")
            .defineInRange("terrainBreachFactor", 1.20D, 0.0D, 5.0D);

    private static final ForgeConfigSpec.DoubleValue SHIP_SIZE_EXPLOSION_SCALE = BUILDER
            .comment("Extra catastrophic explosion scaling contributed by ship size.")
            .defineInRange("shipSizeExplosionScale", 0.65D, 0.0D, 5.0D);

    private static final ForgeConfigSpec.DoubleValue CRASH_BOUNCE_DAMPING = BUILDER
            .comment("Post-impact damping multiplier to reduce bouncing.")
            .defineInRange("crashBounceDamping", 1.75D, 0.0D, 20.0D);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static double minCrashSpeed;
    public static double minDeltaV;
    public static double minImpactEnergy;
    public static int crashCooldownTicks;
    public static boolean requireCollisionSignal;
    public static double hardCrashScore;
    public static double catastrophicCrashScore;
    public static double crashDamageMultiplier;
    public static double recoveryIntegrityFloor;
    public static double partDamageMultiplier;
    public static double partFailureHealthThreshold;
    public static double catastrophicPartFailureChance;
    public static double partRecoveryFloor;
    public static double scrapeExplosionDamping;
    public static double hardExplosionDamping;
    public static double catastrophicExplosionDamping;
    public static double primaryCrumpleShare;
    public static double lowIntegrityThreshold;
    public static double criticalIntegrityThreshold;
    public static double lowIntegrityDeceleration;
    public static double criticalIntegrityDeceleration;
    public static double crashNotificationRadius;
    public static double deckImpactVerticalSpeed;
    public static double frontCrumpleRadius;
    public static double frontCrumpleDepth;
    public static int catastrophicExplosionDelayTicks;
    public static double catastrophicExplosionPower;
    public static double bellyScrapeBlocksPerMps;
    public static double gearCollapseDestructionFactor;
    public static double terrainBreachFactor;
    public static double shipSizeExplosionScale;
    public static double crashBounceDamping;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        minCrashSpeed = MIN_CRASH_SPEED.get();
        minDeltaV = MIN_DELTA_V.get();
        minImpactEnergy = MIN_IMPACT_ENERGY.get();
        crashCooldownTicks = CRASH_COOLDOWN_TICKS.get();
        requireCollisionSignal = REQUIRE_COLLISION_SIGNAL.get();
        hardCrashScore = HARD_CRASH_SCORE.get();
        catastrophicCrashScore = CATASTROPHIC_CRASH_SCORE.get();
        crashDamageMultiplier = CRASH_DAMAGE_MULTIPLIER.get();
        recoveryIntegrityFloor = RECOVERY_INTEGRITY_FLOOR.get();
        partDamageMultiplier = PART_DAMAGE_MULTIPLIER.get();
        partFailureHealthThreshold = PART_FAILURE_HEALTH_THRESHOLD.get();
        catastrophicPartFailureChance = CATASTROPHIC_PART_FAILURE_CHANCE.get();
        partRecoveryFloor = PART_RECOVERY_FLOOR.get();
        scrapeExplosionDamping = SCRAPE_EXPLOSION_DAMPING.get();
        hardExplosionDamping = HARD_EXPLOSION_DAMPING.get();
        catastrophicExplosionDamping = CATASTROPHIC_EXPLOSION_DAMPING.get();
        primaryCrumpleShare = PRIMARY_CRUMPLE_SHARE.get();
        lowIntegrityThreshold = LOW_INTEGRITY_THRESHOLD.get();
        criticalIntegrityThreshold = CRITICAL_INTEGRITY_THRESHOLD.get();
        lowIntegrityDeceleration = LOW_INTEGRITY_DECELERATION.get();
        criticalIntegrityDeceleration = CRITICAL_INTEGRITY_DECELERATION.get();
        crashNotificationRadius = CRASH_NOTIFICATION_RADIUS.get();
        deckImpactVerticalSpeed = DECK_IMPACT_VERTICAL_SPEED.get();
        frontCrumpleRadius = FRONT_CRUMPLE_RADIUS.get();
        frontCrumpleDepth = FRONT_CRUMPLE_DEPTH.get();
        catastrophicExplosionDelayTicks = CATASTROPHIC_EXPLOSION_DELAY_TICKS.get();
        catastrophicExplosionPower = CATASTROPHIC_EXPLOSION_POWER.get();
        bellyScrapeBlocksPerMps = BELLY_SCRAPE_BLOCKS_PER_MPS.get();
        gearCollapseDestructionFactor = GEAR_COLLAPSE_DESTRUCTION_FACTOR.get();
        terrainBreachFactor = TERRAIN_BREACH_FACTOR.get();
        shipSizeExplosionScale = SHIP_SIZE_EXPLOSION_SCALE.get();
        crashBounceDamping = CRASH_BOUNCE_DAMPING.get();
    }
}
