package com.vskinetic.ship;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class ShipCrashHooks {
    private static final CrashPhysicsEngine PHYSICS = new CrashPhysicsEngine();

    private ShipCrashHooks() {
    }

    public static ShipBindingRecord onShipCreated(MinecraftServer server, long shipId, UUID creatorId) {
        ShipRegistryData data = ShipRegistryData.get(server);
        ShipBindingRecord record = data.getOrCreate(shipId);
        if (creatorId != null) {
            data.setCreatorIfMissing(shipId, creatorId);
        }
        return record;
    }

    public static ShipBindingRecord onShipPhysicsSample(
            MinecraftServer server,
            long shipId,
            long gameTime,
            Vec3 linearVelocity,
            double estimatedMass,
            boolean hadCollisionSignal
    ) {
        PhysicsSampleOutcome outcome = onShipPhysicsSampleDetailed(
                server,
                shipId,
                gameTime,
                linearVelocity,
                estimatedMass,
                hadCollisionSignal
        );
        return outcome.record();
    }

    public static PhysicsSampleOutcome onShipPhysicsSampleDetailed(
            MinecraftServer server,
            long shipId,
            long gameTime,
            Vec3 linearVelocity,
            double estimatedMass,
            boolean hadCollisionSignal
    ) {
        CrashPhysicsEngine.CrashResult result = PHYSICS.sample(
                shipId,
                gameTime,
                linearVelocity,
                estimatedMass,
                hadCollisionSignal
        );

        if (!result.crash()) {
            return new PhysicsSampleOutcome(ShipRegistryData.get(server).getOrCreate(shipId), result);
        }

        ShipBindingRecord record = ShipRegistryData.get(server).applyCrash(shipId, gameTime, result);
        return new PhysicsSampleOutcome(record, result);
    }

    public static ShipBindingRecord onShipCrash(MinecraftServer server, long shipId) {
        return ShipRegistryData.get(server).markCrashed(shipId);
    }

    public static ShipBindingRecord onShipRecovered(MinecraftServer server, long shipId) {
        PHYSICS.clearShip(shipId);
        return ShipRegistryData.get(server).recoverShip(shipId);
    }

    public record PhysicsSampleOutcome(ShipBindingRecord record, CrashPhysicsEngine.CrashResult result) {
    }
}
