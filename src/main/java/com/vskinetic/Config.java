package com.vskinetic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    private static final ForgeConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);

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

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;
    public static double minCrashSpeed;
    public static double minDeltaV;
    public static double minImpactEnergy;
    public static int crashCooldownTicks;
    public static boolean requireCollisionSignal;
    public static double hardCrashScore;
    public static double catastrophicCrashScore;
    public static double crashDamageMultiplier;
    public static double recoveryIntegrityFloor;

    private static boolean validateItemName(final Object obj)
    {
        return obj instanceof final String itemName && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        logDirtBlock = LOG_DIRT_BLOCK.get();
        magicNumber = MAGIC_NUMBER.get();
        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();

        // convert the list of strings into a set of items
        items = ITEM_STRINGS.get().stream()
                .map(itemName -> ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName)))
                .collect(Collectors.toSet());

        minCrashSpeed = MIN_CRASH_SPEED.get();
        minDeltaV = MIN_DELTA_V.get();
        minImpactEnergy = MIN_IMPACT_ENERGY.get();
        crashCooldownTicks = CRASH_COOLDOWN_TICKS.get();
        requireCollisionSignal = REQUIRE_COLLISION_SIGNAL.get();
        hardCrashScore = HARD_CRASH_SCORE.get();
        catastrophicCrashScore = CATASTROPHIC_CRASH_SCORE.get();
        crashDamageMultiplier = CRASH_DAMAGE_MULTIPLIER.get();
        recoveryIntegrityFloor = RECOVERY_INTEGRITY_FLOOR.get();
    }
}
