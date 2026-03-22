package com.vskinetic.vibeysplaypen;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.valkyrienskies.core.api.events.CollisionEvent;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

import java.util.concurrent.ConcurrentLinkedQueue;

public class FuckAround {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ConcurrentLinkedQueue<String> pendingLogs = new ConcurrentLinkedQueue<>();

    public static void register() {
        LOGGER.info("[VS Kinetic] Registering collision event...");
        ValkyrienSkiesMod.getApi().getCollisionPersistEvent().on(FuckAround::onCollision);
        LOGGER.info("[VS Kinetic] Collision event registered.");
    }

    public static void onCollision(CollisionEvent event) {
        LOGGER.info("[VS Kinetic] onCollision fired!");

        var physLevel = event.getPhysLevel();
        var shipA = physLevel.getShipById(event.getShipIdA());

        if (shipA == null) {
            LOGGER.warn("[VS Kinetic] Could not find ship A for collision event");
            return;
        }

        var toModel = shipA.getTransform().getToModel();

        event.getContactPoints().forEach((contactPoint) -> {
            var worldPos = contactPoint.getPosition();

            var modelPos = new org.joml.Vector3d();
            toModel.transformPosition(worldPos, modelPos);

            LOGGER.info("[VS Kinetic] Contact at (world): " + worldPos);
            LOGGER.info("[VS Kinetic] Contact at (model): " + modelPos);
        });
    }

    public static void drainLogs() {
        String msg;
        while ((msg = pendingLogs.poll()) != null) {
            LOGGER.info(msg);
        }
    }
}