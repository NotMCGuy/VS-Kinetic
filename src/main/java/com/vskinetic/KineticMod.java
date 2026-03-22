package com.vskinetic;

import com.mojang.logging.LogUtils;
import com.vskinetic.ship.ShipCommands;
import com.vskinetic.ship.ShipCollisionSignals;
import com.vskinetic.ship.ShipRuntimeEvents;
import com.vskinetic.vibeysplaypen.FuckAround;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;


@Mod(KineticMod.MODID)
public class KineticMod {

    public static final String MODID = "vskinetic";
    private static final Logger LOGGER = LogUtils.getLogger();

    public KineticMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ShipRuntimeEvents());
        MinecraftForge.EVENT_BUS.addListener(KineticMod::onServerTick);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "vskinetic.toml");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[VS Kinetic] commonSetup called!");
        ShipCollisionSignals.registerVsCollisionEvents();
        FuckAround.register();
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            FuckAround.drainLogs();
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ShipCommands.register(event.getDispatcher());
    }
}