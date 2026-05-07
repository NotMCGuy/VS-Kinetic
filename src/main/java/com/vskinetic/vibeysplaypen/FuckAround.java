package com.vskinetic.vibeysplaypen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.events.CollisionEvent;
import org.valkyrienskies.core.api.physics.ContactPoint;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

//after the initial force calculations I coded i gave up a lil and gave it to claude :3
//claude is better than me T-T

public class FuckAround {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final double IMPULSE_MULTIPLIER = 2.0;
    private static final double RESISTANCE_SCALE = 750.0;
    private static final double MIN_IMPULSE = 100_000.0;
    private static final double MAX_IMPULSE = 1_000_000.0;

    private static final double PROPAGATION_ATTENUATION = 0.8;
    private static final int MAX_PROPAGATION_DEPTH = 4;

    // A ship this volume or larger gets full propagation depth and attenuation.
    // 10x10x10 = 1000. Tune down to make propagation kick in for smaller ships.
    private static final double REFERENCE_VOLUME = 1_000.0;

    private static final ConcurrentHashMap<String, ResourceKey<net.minecraft.world.level.Level>> dimensionKeyCache =
            new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<ResourceKey<net.minecraft.world.level.Level>, ConcurrentHashMap<BlockPos, Double>> pendingDestructions =
            new ConcurrentHashMap<>();

    private static ResourceKey<net.minecraft.world.level.Level> getDimensionKey(String dimensionId) {
        return dimensionKeyCache.computeIfAbsent(dimensionId, id -> {
            String[] parts = id.split(":");
            String namespace = parts[parts.length - 2];
            String path = parts[parts.length - 1];
            return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(namespace, path));
        });
    }

    private static void queueDestruction(
            ResourceKey<net.minecraft.world.level.Level> dimensionKey,
            BlockPos pos,
            double impulse
    ) {
        pendingDestructions
                .computeIfAbsent(dimensionKey, k -> new ConcurrentHashMap<>())
                .merge(pos, impulse, Math::max);
    }

    private static double getShipSizeFactor(PhysShip ship) {
        double mass = ship.getMass();
        // log scale so a 100kg ship gets near 0 and a 10,000,000kg ship gets near 1.0
        // without a massive ship being capped too early or a small ship getting any meaningful propagation
        return Math.min(1.0, Math.log10(mass) / 7.0);
    }

    private static void queuePropagation(
            ResourceKey<net.minecraft.world.level.Level> dimensionKey,
            BlockPos origin,
            double originImpulse,
            double sizeFactor
    ) {
        if (sizeFactor <= 0.01) return;

        int maxDepth = (int) Math.round(MAX_PROPAGATION_DEPTH * sizeFactor);
        if (maxDepth == 0) return;

        double effectiveAttenuation = Math.pow(PROPAGATION_ATTENUATION, 1.0 / Math.max(sizeFactor, 0.01));

        record Entry(BlockPos pos, double impulse, int depth) {}

        var queue = new ArrayDeque<Entry>();
        var visited = new HashSet<BlockPos>();
        visited.add(origin);
        queue.add(new Entry(origin, originImpulse, 0));

        while (!queue.isEmpty()) {
            var current = queue.poll();
            if (current.depth() >= maxDepth) continue;

            double attenuated = current.impulse() * effectiveAttenuation;
            if (attenuated < MIN_IMPULSE) continue;

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.pos().relative(dir);
                if (visited.add(neighbor)) {
                    queueDestruction(dimensionKey, neighbor, attenuated);
                    queue.add(new Entry(neighbor, attenuated, current.depth() + 1));
                }
            }
        }
    }

    public static void register() {
        LOGGER.info("[VS Kinetic] Registering collision event...");
        ValkyrienSkiesMod.getApi().getCollisionPersistEvent().on(FuckAround::onCollision);
        LOGGER.info("[VS Kinetic] Collision event registered.");
    }

    public static void onCollision(CollisionEvent event) {
        var physLevel = event.getPhysLevel();

        Ship rawA = physLevel.getShipById(event.getShipIdA());
        Ship rawB = physLevel.getShipById(event.getShipIdB());
        if (rawA == null) return;
        if (!(rawA instanceof PhysShip shipA)) return;

        PhysShip shipB = (rawB instanceof PhysShip ps) ? ps : null;

        var dimensionKey = getDimensionKey(event.getDimensionId());

        var comWorldA = computeComWorld(shipA);
        var comWorldB = (shipB != null) ? computeComWorld(shipB) : null;
        var rotMatrixA = extractRotMatrix(shipA);
        var rotMatrixB = (shipB != null) ? extractRotMatrix(shipB) : null;
        var toModelA = shipA.getTransform().getToModel();

        double sizeFactor = getShipSizeFactor(shipA);

        event.getContactPoints().forEach(contactPoint -> {
            double impulse = calculateImpulse(shipA, shipB, contactPoint, comWorldA, comWorldB, rotMatrixA, rotMatrixB);
            if (impulse < MIN_IMPULSE) return;

            double normalized = (impulse - MIN_IMPULSE) / (MAX_IMPULSE - MIN_IMPULSE);
            double breakChance = Math.min(1.0, normalized * normalized);
            if (Math.random() > breakChance) return;

            var worldPos = contactPoint.getPosition();
            var modelPos = new Vector3d();
            toModelA.transformPosition(worldPos, modelPos);

            BlockPos worldBlock = new BlockPos(
                    (int) Math.floor(worldPos.x()),
                    (int) Math.floor(worldPos.y()),
                    (int) Math.floor(worldPos.z())
            );
            BlockPos modelBlock = new BlockPos(
                    (int) Math.floor(modelPos.x()),
                    (int) Math.floor(modelPos.y()),
                    (int) Math.floor(modelPos.z())
            );

            queueDestruction(dimensionKey, worldBlock, impulse);
            queueDestruction(dimensionKey, modelBlock, impulse);

            queuePropagation(dimensionKey, worldBlock, impulse, sizeFactor);
            queuePropagation(dimensionKey, modelBlock, impulse, sizeFactor);
        });
    }

    private static Vector3d computeComWorld(PhysShip ship) {
        var comModel = ship.getCenterOfMass();
        var comWorld = new Vector3d();
        ship.getTransform().getToWorld().transformPosition(comModel, comWorld);
        return comWorld;
    }

    private static org.joml.Matrix3d extractRotMatrix(PhysShip ship) {
        var toWorld = ship.getTransform().getToWorld();
        return new org.joml.Matrix3d(
                toWorld.m00(), toWorld.m01(), toWorld.m02(),
                toWorld.m10(), toWorld.m11(), toWorld.m12(),
                toWorld.m20(), toWorld.m21(), toWorld.m22()
        );
    }

    private static double calculateImpulse(
            PhysShip shipA,
            @Nullable PhysShip shipB,
            ContactPoint contactPoint,
            Vector3d comWorldA,
            @Nullable Vector3d comWorldB,
            org.joml.Matrix3d rotMatrixA,
            @Nullable org.joml.Matrix3d rotMatrixB
    ) {
        var normal = contactPoint.getNormal();
        var contactPos = contactPoint.getPosition();

        double velA = surfaceVelocityAlongNormal(shipA, contactPos, normal, comWorldA);
        double velB = (shipB != null) ? surfaceVelocityAlongNormal(shipB, contactPos, normal, comWorldB) : 0.0;

        double impactSpeed = Math.abs(velA - velB);

        double invMassA = effectiveMassInverse(shipA, contactPos, normal, comWorldA, rotMatrixA);
        double invMassB = (shipB != null)
                ? effectiveMassInverse(shipB, contactPos, normal, comWorldB, rotMatrixB)
                : 0.0;

        double totalInvMass = invMassA + invMassB;
        if (totalInvMass == 0.0) return 0.0;

        double effMassA = 1.0 / invMassA;
        double effMassB = (shipB != null) ? 1.0 / invMassB : Double.MAX_VALUE;
        double dominantEffMass = (shipB != null) ? Math.max(effMassA, effMassB) : effMassA;

        return dominantEffMass * impactSpeed * IMPULSE_MULTIPLIER;
    }

    private static double surfaceVelocityAlongNormal(
            PhysShip ship,
            Vector3dc contactPos,
            Vector3dc normal,
            Vector3d comWorld
    ) {
        var linVel = ship.getVelocity();
        double linearComponent = linVel.x() * normal.x() + linVel.y() * normal.y() + linVel.z() * normal.z();

        var omega = ship.getAngularVelocity();
        var r = new Vector3d(
                contactPos.x() - comWorld.x(),
                contactPos.y() - comWorld.y(),
                contactPos.z() - comWorld.z()
        );

        var omegaVec = new Vector3d(omega.x(), omega.y(), omega.z());
        var rotationalVel = new Vector3d();
        omegaVec.cross(r, rotationalVel);

        double rotationalComponent = rotationalVel.x() * normal.x() + rotationalVel.y() * normal.y() + rotationalVel.z() * normal.z();

        return linearComponent + rotationalComponent;
    }

    private static double effectiveMassInverse(
            PhysShip ship,
            Vector3dc contactPos,
            Vector3dc normal,
            Vector3d comWorld,
            org.joml.Matrix3d rotMatrix
    ) {
        double invMass = 1.0 / ship.getMass();

        var r = new Vector3d(
                contactPos.x() - comWorld.x(),
                contactPos.y() - comWorld.y(),
                contactPos.z() - comWorld.z()
        );

        var rCrossN = new Vector3d();
        r.cross(normal, rCrossN);

        var rCrossNModel = new Vector3d(rCrossN);
        rotMatrix.transpose(new org.joml.Matrix3d()).transform(rCrossNModel);

        var invInertia = new org.joml.Matrix3d(ship.getMomentOfInertia()).invert();
        var iInvRCrossN = new Vector3d();
        invInertia.transform(rCrossNModel, iInvRCrossN);

        rotMatrix.transform(iInvRCrossN);

        double rotationalTerm = rCrossN.dot(iInvRCrossN);

        return invMass + rotationalTerm;
    }

    public static void onServerTick() {
        var server = ValkyrienSkiesMod.getCurrentServer();
        if (server == null) return;

        pendingDestructions.forEach((dimensionKey, positions) -> {
            ServerLevel level = server.getLevel(dimensionKey);
            if (level == null) return;

            var iterator = positions.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                BlockPos pos = entry.getKey();
                double impulse = entry.getValue();
                iterator.remove();

                BlockState blockState = level.getBlockState(pos);
                if (blockState.isAir()) continue;

                float resistance = blockState.getBlock().getExplosionResistance();
                double blockThreshold = resistance * RESISTANCE_SCALE;

                if (impulse < blockThreshold) continue;

                level.destroyBlock(pos, false);
                LOGGER.info("[VS Kinetic] Block destroyed at {} (resistance={}, impulse={})", pos, resistance, impulse);
            }
        });
    }
}