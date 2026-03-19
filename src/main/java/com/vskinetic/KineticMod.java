package com.vskinetic;

import com.vskinetic.ship.ShipCommands;
import com.vskinetic.ship.ShipRuntimeEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;


@Mod(KineticMod.MODID)
public class KineticMod
{

    public static final String MODID = "vskinetic";

    public KineticMod(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ShipRuntimeEvents());

        // Register our mod's ForgeConfigSpec so that Forge creates/loads config/vskinetic.toml
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "vskinetic.toml");
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {

    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event)
    {
        ShipCommands.register(event.getDispatcher());
    }

}
