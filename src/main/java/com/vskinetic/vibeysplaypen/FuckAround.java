package com.vskinetic.vibeysplaypen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.valkyrienskies.core.api.events.CollisionEvent;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FuckAround {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ConcurrentHashMap<String, ResourceKey<net.minecraft.world.level.Level>> dimensionKeyCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ResourceKey<net.minecraft.world.level.Level>, Set<BlockPos>> pendingDestructions = new ConcurrentHashMap<>();

    private static ResourceKey<net.minecraft.world.level.Level> getDimensionKey(String dimensionId) {
        return dimensionKeyCache.computeIfAbsent(dimensionId, id -> {
            String[] parts = id.split(":");
            String namespace = parts[parts.length - 2];
            String path = parts[parts.length - 1];
            return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(namespace, path));
        });
    }

    private static void queueDestruction(ResourceKey<net.minecraft.world.level.Level> dimensionKey, BlockPos pos) {
        pendingDestructions
                .computeIfAbsent(dimensionKey, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(pos);
    }

    public static void register() {
        LOGGER.info("[VS Kinetic] Registering collision event...");
        ValkyrienSkiesMod.getApi().getCollisionPersistEvent().on(FuckAround::onCollision);
        LOGGER.info("[VS Kinetic] Collision event registered.");
    }

    public static void onCollision(CollisionEvent event) {
        var physLevel = event.getPhysLevel();
        var shipA = physLevel.getShipById(event.getShipIdA());
        if (shipA == null) return;

        var dimensionKey = getDimensionKey(event.getDimensionId());
        var toModel = shipA.getTransform().getToModel();

        event.getContactPoints().forEach(contactPoint -> {
            var worldPos = contactPoint.getPosition();

            var modelPos = new org.joml.Vector3d();
            toModel.transformPosition(worldPos, modelPos);

            queueDestruction(dimensionKey, new BlockPos(
                    (int) Math.floor(worldPos.x()),
                    (int) Math.floor(worldPos.y()),
                    (int) Math.floor(worldPos.z())
            ));
            queueDestruction(dimensionKey, new BlockPos(
                    (int) Math.floor(modelPos.x()),
                    (int) Math.floor(modelPos.y()),
                    (int) Math.floor(modelPos.z())
            ));
        });
    }

    public static void onServerTick() {
        var server = ValkyrienSkiesMod.getCurrentServer();
        if (server == null) return;

        pendingDestructions.forEach((dimensionKey, positions) -> {
            ServerLevel level = server.getLevel(dimensionKey);
            if (level == null) return;

            positions.forEach(pos -> level.destroyBlock(pos, false));
            positions.clear();
        });
    }
}