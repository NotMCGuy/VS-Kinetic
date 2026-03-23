package com.vskinetic.ship;

import com.vskinetic.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ShipRuntimeEvents {
    private static final double MIN_DIRECTION_ALIGNMENT = 0.25D;
    private static final long CRITICAL_WARNING_INTERVAL_TICKS = 200L;

    private final Map<Long, RuntimeShipState> runtimeStateByShip = new HashMap<>();
    private final ShipCrashConsequences crashConsequences = new ShipCrashConsequences();

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }

        crashConsequences.processPendingExplosions(level);
        Set<Long> collisionSignals = ShipCollisionSignals.drainSignalsForDimension(level.dimension().location().toString());

        for (LoadedServerShip ship : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            processShip(level, ship, collisionSignals);
        }
    }

    private void processShip(ServerLevel level, LoadedServerShip ship, Set<Long> collisionSignals) {
        long shipId = ship.getId();
        RuntimeShipState runtimeState = runtimeStateByShip.computeIfAbsent(shipId, ignored -> {
            ShipCrashHooks.onShipCreated(level.getServer(), shipId, null);
            return new RuntimeShipState();
        });

        Vec3 previousVelocity = runtimeState.lastVelocity;
        Vec3 velocity = toMinecraft(ship.getVelocity());
        boolean inferredCollision = inferCollision(runtimeState, velocity);
        boolean hadCollisionSignal = inferredCollision || collisionSignals.contains(shipId);
        ImpactPart impactPart = inferImpactPart(ship, previousVelocity, velocity);

        ShipCrashHooks.PhysicsSampleOutcome outcome = ShipCrashHooks.onShipPhysicsSampleDetailed(
                level.getServer(),
                shipId,
                level.getGameTime(),
                velocity,
                ship.getInertiaData().getShipMass(),
                hadCollisionSignal,
                impactPart
        );

        runtimeState.lastVelocity = velocity;

        ShipBindingRecord record = outcome.record();
        CrashPhysicsEngine.CrashResult result = outcome.result();
        if (result.crash()) {
            applyWingClipDamage(level, ship, previousVelocity, result, runtimeState);
            crashConsequences.applyCrashEffects(level, ship, result, previousVelocity, velocity);
            notifyCrash(level, ship, record, result);
            armPostCrashDamping(runtimeState, result);
            armGroundSettling(runtimeState, previousVelocity, result);
            applyCrashBraking(ship, result);
        }

        applyPostCrashDamping(ship, runtimeState);
        applyGroundSettling(ship, runtimeState);
        applyWingInstability(ship, runtimeState);
        applyIntegrityDrag(ship, record);
        warnCriticalIntegrity(level, ship, record, runtimeState);
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        LoadedServerShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level, event.getPos());
        if (ship == null) {
            return;
        }

        RuntimeShipState runtimeState = runtimeStateByShip.computeIfAbsent(ship.getId(), ignored -> new RuntimeShipState());
        ShipBounds bounds = getShipBounds(ship);
        if (bounds == null) {
            return;
        }

        Vector3d local = ship.getTransform().getWorldToShip().transformPosition(
                new Vector3d(event.getPos().getX() + 0.5D, event.getPos().getY() + 0.5D, event.getPos().getZ() + 0.5D),
                new Vector3d()
        );
        double centerX = (bounds.minX() + bounds.maxX()) * 0.5D;
        double halfSpan = Math.max(1.0D, (bounds.maxX() - bounds.minX()) * 0.5D);
        double lateral = (local.x() - centerX) / halfSpan;
        if (Math.abs(lateral) < 0.58D) {
            return;
        }

        double centerY = (bounds.minY() + bounds.maxY()) * 0.5D;
        if (local.y() < centerY - 1.5D) {
            return;
        }

        if (lateral >= 0.0D) {
            runtimeState.rightWingDamage = Math.min(100.0D, runtimeState.rightWingDamage + 2.2D);
        } else {
            runtimeState.leftWingDamage = Math.min(100.0D, runtimeState.leftWingDamage + 2.2D);
        }
    }

    private boolean inferCollision(RuntimeShipState runtimeState, Vec3 velocity) {
        if (runtimeState.lastVelocity == null) {
            return false;
        }

        double previousSpeed = runtimeState.lastVelocity.length();
        double currentSpeed = velocity.length();
        double deltaV = velocity.subtract(runtimeState.lastVelocity).length();
        double speedLoss = Math.max(0.0D, previousSpeed - currentSpeed);
        double directionAlignment = alignment(runtimeState.lastVelocity, velocity);

        return deltaV >= Config.minDeltaV
                && (speedLoss >= Config.minDeltaV * 0.35D || directionAlignment <= MIN_DIRECTION_ALIGNMENT);
    }

    private void notifyCrash(
            ServerLevel level,
            LoadedServerShip ship,
            ShipBindingRecord record,
            CrashPhysicsEngine.CrashResult result
    ) {
        Component message = Component.literal("[VS Kinetic] "
                + record.displayName()
                + " suffered a " + prettySeverity(result.severity())
                + " impact. Integrity now " + String.format("%.1f", record.structuralIntegrity()) + "%.");

        Set<ServerPlayer> recipients = new LinkedHashSet<>();
        Vector3dc shipCenter = ship.getTransform().getPositionInWorld();
        double notificationDistanceSq = Config.crashNotificationRadius * Config.crashNotificationRadius;

        for (ServerPlayer player : level.players()) {
            if (isPlayerOnShip(level, ship, player)
                    || player.distanceToSqr(shipCenter.x(), shipCenter.y(), shipCenter.z()) <= notificationDistanceSq) {
                recipients.add(player);
            }
        }

        addTrackedPlayer(level, recipients, record.owner());
        addTrackedPlayer(level, recipients, record.createdBy());

        for (ServerPlayer recipient : recipients) {
            recipient.sendSystemMessage(message);
        }
    }

    private void applyCrashBraking(LoadedServerShip ship, CrashPhysicsEngine.CrashResult result) {
        double extraDeceleration = switch (result.severity()) {
            case SCRAPE -> 0.25D;
            case HARD -> 0.75D;
            case CATASTROPHIC -> 1.50D;
            case NONE -> 0.0D;
        };
        applyBrakingForce(ship, extraDeceleration);
    }

    private void armPostCrashDamping(RuntimeShipState runtimeState, CrashPhysicsEngine.CrashResult result) {
        int ticks = switch (result.severity()) {
            case SCRAPE -> 10;
            case HARD -> 20;
            case CATASTROPHIC -> 34;
            case NONE -> 0;
        };
        double deceleration = switch (result.severity()) {
            case SCRAPE -> 0.80D;
            case HARD -> 1.80D;
            case CATASTROPHIC -> 3.00D;
            case NONE -> 0.0D;
        };
        runtimeState.postCrashDampingTicks = Math.max(runtimeState.postCrashDampingTicks, ticks);
        runtimeState.postCrashDamping = Math.max(runtimeState.postCrashDamping, Math.max(result.bounceDamping(), deceleration));
    }

    private void applyPostCrashDamping(LoadedServerShip ship, RuntimeShipState runtimeState) {
        if (runtimeState.postCrashDampingTicks <= 0 || runtimeState.postCrashDamping <= 0.0D) {
            return;
        }
        applyBrakingForce(ship, runtimeState.postCrashDamping);
        runtimeState.postCrashDampingTicks--;
    }

    private void armGroundSettling(
            RuntimeShipState runtimeState,
            Vec3 previousVelocity,
            CrashPhysicsEngine.CrashResult result
    ) {
        if (previousVelocity == null) {
            return;
        }
        double downwardSpeed = Math.max(0.0D, -previousVelocity.y);
        if (downwardSpeed < 2.2D) {
            return;
        }

        int ticks = switch (result.severity()) {
            case SCRAPE -> 8;
            case HARD -> 14;
            case CATASTROPHIC -> 22;
            case NONE -> 0;
        };
        double strength = switch (result.severity()) {
            case SCRAPE -> 0.30D;
            case HARD -> 0.55D;
            case CATASTROPHIC -> 0.95D;
            case NONE -> 0.0D;
        };
        runtimeState.groundSettleTicks = Math.max(runtimeState.groundSettleTicks, ticks);
        runtimeState.groundSettleStrength = Math.max(
                runtimeState.groundSettleStrength,
                strength * (1.0D + Math.min(1.25D, downwardSpeed / 8.0D)) * Math.max(0.01D, result.bounceDamping())
        );
    }

    private void applyGroundSettling(LoadedServerShip ship, RuntimeShipState runtimeState) {
        if (runtimeState.groundSettleTicks <= 0 || runtimeState.groundSettleStrength <= 0.0D) {
            return;
        }
        double mass = Math.max(1.0D, ship.getInertiaData().getShipMass());
        Vector3d force = new Vector3d(0.0D, -mass * runtimeState.groundSettleStrength, 0.0D);
        Vector3d center = new Vector3d(ship.getTransform().getPositionInWorld());
        ValkyrienSkiesMod.getOrCreateGTPA(ship.getChunkClaimDimension()).applyWorldForce(ship.getId(), force, center);
        runtimeState.groundSettleTicks--;
        runtimeState.groundSettleStrength *= 0.88D;
    }

    private void applyWingClipDamage(
            ServerLevel level,
            LoadedServerShip ship,
            Vec3 previousVelocity,
            CrashPhysicsEngine.CrashResult result,
            RuntimeShipState runtimeState
    ) {
        if (previousVelocity == null || previousVelocity.lengthSqr() < 1.0E-4D) {
            return;
        }
        ShipBounds bounds = getShipBounds(ship);
        if (bounds == null) {
            return;
        }

        Vector3d velLocal = ship.getTransform().getWorldToShip().transformDirection(
                new Vector3d(previousVelocity.x, previousVelocity.y, previousVelocity.z),
                new Vector3d()
        );
        double horizontalSpeed = Math.sqrt(previousVelocity.x * previousVelocity.x + previousVelocity.z * previousVelocity.z);
        double downwardSpeed = Math.max(0.0D, -previousVelocity.y);
        if (horizontalSpeed < 3.0D && downwardSpeed < 3.5D) {
            return;
        }

        double sideWeight = Math.abs(velLocal.x()) / Math.max(0.001D, Math.sqrt(velLocal.x() * velLocal.x() + velLocal.z() * velLocal.z()));
        if (sideWeight < 0.25D && downwardSpeed < Config.deckImpactVerticalSpeed * 0.85D) {
            return;
        }

        boolean hitRightWing = velLocal.x() >= 0.0D;
        double wingDamage = switch (result.severity()) {
            case SCRAPE -> 10.0D;
            case HARD -> 22.0D;
            case CATASTROPHIC -> 36.0D;
            case NONE -> 0.0D;
        };
        wingDamage *= (0.8D + sideWeight * 0.8D);
        if (downwardSpeed > Config.deckImpactVerticalSpeed) {
            wingDamage *= 1.2D;
        }

        if (hitRightWing) {
            runtimeState.rightWingDamage = Math.min(100.0D, runtimeState.rightWingDamage + wingDamage);
            severWingSection(level, ship, bounds, true, result);
        } else {
            runtimeState.leftWingDamage = Math.min(100.0D, runtimeState.leftWingDamage + wingDamage);
            severWingSection(level, ship, bounds, false, result);
        }
    }

    private void applyWingInstability(LoadedServerShip ship, RuntimeShipState runtimeState) {
        double left = runtimeState.leftWingDamage;
        double right = runtimeState.rightWingDamage;
        if (left < 1.0D && right < 1.0D) {
            return;
        }

        Vector3dc velocity = ship.getVelocity();
        double speedSq = velocity.lengthSquared();
        if (speedSq < 0.15D) {
            return;
        }

        Vector3d rightAxis = ship.getTransform().getShipToWorld()
                .transformDirection(new Vector3d(1.0D, 0.0D, 0.0D), new Vector3d());
        if (rightAxis.lengthSquared() < 1.0E-6D) {
            return;
        }
        rightAxis.normalize();

        Vector3d velocityDir = new Vector3d(velocity).normalize();
        double mass = Math.max(1.0D, ship.getInertiaData().getShipMass());
        double imbalance = (right - left) / 100.0D;
        double damageLevel = Math.max(left, right) / 100.0D;
        if (Math.abs(imbalance) < 0.02D && damageLevel < 0.35D) {
            return;
        }

        double base = mass * (0.12D + damageLevel * 0.42D);
        Vector3d force = new Vector3d(velocityDir).mul(-base * (0.6D + Math.abs(imbalance)));
        force.add(0.0D, -base * 0.42D * (0.3D + damageLevel), 0.0D);

        Vector3d center = new Vector3d(ship.getTransform().getPositionInWorld());
        Vector3d offsetDir = new Vector3d(rightAxis).mul(Math.signum(imbalance == 0.0D ? (right - left) : imbalance));
        Vector3d applyPos = center.add(offsetDir.mul(3.5D), new Vector3d());
        ValkyrienSkiesMod.getOrCreateGTPA(ship.getChunkClaimDimension()).applyWorldForce(ship.getId(), force, applyPos);

        // Ongoing aerodynamic load slowly worsens a damaged wing at speed.
        double speed = Math.sqrt(speedSq);
        double progressive = (speed / 45.0D) * damageLevel * 0.35D;
        if (imbalance >= 0.0D) {
            runtimeState.rightWingDamage = Math.min(100.0D, runtimeState.rightWingDamage + progressive);
        } else {
            runtimeState.leftWingDamage = Math.min(100.0D, runtimeState.leftWingDamage + progressive);
        }
    }

    private void severWingSection(
            ServerLevel level,
            LoadedServerShip ship,
            ShipBounds bounds,
            boolean rightWing,
            CrashPhysicsEngine.CrashResult result
    ) {
        double halfSpan = Math.max(1.0D, (bounds.maxX() - bounds.minX()) * 0.5D);
        double centerX = (bounds.minX() + bounds.maxX()) * 0.5D;
        double centerY = (bounds.minY() + bounds.maxY()) * 0.5D;
        double centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5D;
        double wingX = rightWing ? centerX + halfSpan * 0.88D : centerX - halfSpan * 0.88D;

        int sliceDepth = switch (result.severity()) {
            case SCRAPE -> 2;
            case HARD -> 3;
            case CATASTROPHIC -> 5;
            case NONE -> 0;
        };
        int halfHeight = switch (result.severity()) {
            case SCRAPE -> 1;
            case HARD -> 2;
            case CATASTROPHIC -> 3;
            case NONE -> 0;
        };
        int halfChord = switch (result.severity()) {
            case SCRAPE -> 2;
            case HARD -> 3;
            case CATASTROPHIC -> 4;
            case NONE -> 0;
        };
        if (sliceDepth <= 0) {
            return;
        }

        int xDir = rightWing ? -1 : 1;
        for (int dx = 0; dx <= sliceDepth; dx++) {
            for (int dy = -halfHeight; dy <= halfHeight; dy++) {
                for (int dz = -halfChord; dz <= halfChord; dz++) {
                    Vector3d local = new Vector3d(
                            wingX + dx * xDir,
                            centerY + dy,
                            centerZ + dz
                    );
                    BlockPos pos = localToWorldPos(ship, local);
                    if (pos.getY() < level.getMinBuildHeight()) {
                        continue;
                    }
                    if (level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    if (level.random.nextFloat() < 0.84F) {
                        level.destroyBlock(pos, false);
                    }
                }
            }
        }
    }

    private void applyIntegrityDrag(LoadedServerShip ship, ShipBindingRecord record) {
        if (record.structuralIntegrity() <= Config.criticalIntegrityThreshold) {
            applyBrakingForce(ship, Config.criticalIntegrityDeceleration);
            return;
        }

        if (record.structuralIntegrity() <= Config.lowIntegrityThreshold) {
            applyBrakingForce(ship, Config.lowIntegrityDeceleration);
        }
    }

    private void warnCriticalIntegrity(
            ServerLevel level,
            LoadedServerShip ship,
            ShipBindingRecord record,
            RuntimeShipState runtimeState
    ) {
        if (record.structuralIntegrity() > Config.criticalIntegrityThreshold) {
            runtimeState.lastCriticalWarningTick = Long.MIN_VALUE;
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime - runtimeState.lastCriticalWarningTick < CRITICAL_WARNING_INTERVAL_TICKS) {
            return;
        }

        runtimeState.lastCriticalWarningTick = gameTime;

        Component message = Component.literal("[VS Kinetic] "
                + record.displayName()
                + " is critically damaged and bleeding speed.");

        for (ServerPlayer player : level.players()) {
            if (isPlayerOnShip(level, ship, player)) {
                player.sendSystemMessage(message);
            }
        }
    }

    private void applyBrakingForce(LoadedServerShip ship, double deceleration) {
        if (deceleration <= 0.0D) {
            return;
        }

        Vector3dc velocity = ship.getVelocity();
        if (velocity.lengthSquared() < 1.0E-4D) {
            return;
        }

        double mass = Math.max(1.0D, ship.getInertiaData().getShipMass());
        Vector3d brakingForce = new Vector3d(velocity).normalize().mul(-mass * deceleration);
        ValkyrienSkiesMod.getOrCreateGTPA(ship.getChunkClaimDimension()).applyWorldForce(ship.getId(), brakingForce, null);
    }

    private boolean isPlayerOnShip(ServerLevel level, LoadedServerShip ship, ServerPlayer player) {
        LoadedServerShip playerShip = VSGameUtilsKt.getLoadedShipManagingPos(level, player.blockPosition());
        if (playerShip != null && playerShip.getId() == ship.getId()) {
            return true;
        }

        LoadedShip mountedShip = VSGameUtilsKt.getShipMountedTo(player);
        if (mountedShip != null && mountedShip.getId() == ship.getId()) {
            return true;
        }

        if (player instanceof IEntityDraggingInformationProvider dragProvider) {
            Long draggedShipId = dragProvider.getDraggingInformation().getLastShipStoodOn();
            return dragProvider.getDraggingInformation().isEntityBeingDraggedByAShip()
                    && draggedShipId != null
                    && draggedShipId == ship.getId();
        }

        return false;
    }

    private void addTrackedPlayer(ServerLevel level, Set<ServerPlayer> recipients, UUID playerId) {
        if (playerId == null) {
            return;
        }

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) {
            recipients.add(player);
        }
    }

    private static double alignment(Vec3 first, Vec3 second) {
        double firstLength = first.length();
        double secondLength = second.length();
        if (firstLength < 1.0E-4D || secondLength < 1.0E-4D) {
            return 1.0D;
        }
        return first.dot(second) / (firstLength * secondLength);
    }

    private static Vec3 toMinecraft(Vector3dc vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static ImpactPart inferImpactPart(LoadedServerShip ship, Vec3 previousVelocity, Vec3 currentVelocity) {
        if (previousVelocity == null) {
            return ImpactPart.AUTO;
        }

        Vec3 approachVelocity = previousVelocity.lengthSqr() >= currentVelocity.lengthSqr() ? previousVelocity : currentVelocity;
        if (approachVelocity.lengthSqr() < 1.0E-4D) {
            return ImpactPart.AUTO;
        }

        Vector3d approachLocal = ship.getTransform().getWorldToShip().transformDirection(
                new Vector3d(approachVelocity.x, approachVelocity.y, approachVelocity.z),
                new Vector3d()
        );
        double ax = Math.abs(approachLocal.x());
        double ay = Math.abs(approachLocal.y());
        double az = Math.abs(approachLocal.z());

        if (ay >= ax && ay >= az) {
            return approachLocal.y < 0.0D ? ImpactPart.LIFT : ImpactPart.HULL;
        }
        if (az >= ax) {
            return approachLocal.z >= 0.0D ? ImpactPart.HULL : ImpactPart.ENGINE;
        }
        return ImpactPart.CONTROL;
    }

    private static String prettySeverity(CrashPhysicsEngine.CrashSeverity severity) {
        return switch (severity) {
            case SCRAPE -> "scrape";
            case HARD -> "hard";
            case CATASTROPHIC -> "catastrophic";
            case NONE -> "non-event";
        };
    }

    private static BlockPos localToWorldPos(LoadedServerShip ship, Vector3d localPos) {
        Vector3d world = ship.getTransform().getShipToWorld().transformPosition(localPos, new Vector3d());
        return BlockPos.containing(world.x(), world.y(), world.z());
    }

    private static ShipBounds getShipBounds(LoadedServerShip ship) {
        try {
            Object bounds = ship.getClass().getMethod("getShipAABB").invoke(ship);
            if (bounds == null) {
                return null;
            }
            return new ShipBounds(
                    readBound(bounds, "minX"),
                    readBound(bounds, "minY"),
                    readBound(bounds, "minZ"),
                    readBound(bounds, "maxX"),
                    readBound(bounds, "maxY"),
                    readBound(bounds, "maxZ")
            );
        } catch (ReflectiveOperationException | ClassCastException ex) {
            return null;
        }
    }

    private static double readBound(Object bounds, String accessor) throws ReflectiveOperationException {
        Class<?> boundsClass = bounds.getClass();
        try {
            Object value = boundsClass.getMethod(accessor).invoke(bounds);
            return ((Number) value).doubleValue();
        } catch (NoSuchMethodException ignored) {
        }

        String getter = "get" + Character.toUpperCase(accessor.charAt(0)) + accessor.substring(1);
        try {
            Object value = boundsClass.getMethod(getter).invoke(bounds);
            return ((Number) value).doubleValue();
        } catch (NoSuchMethodException ignored) {
            Object value = boundsClass.getField(accessor).get(bounds);
            return ((Number) value).doubleValue();
        }
    }

    private static final class RuntimeShipState {
        private Vec3 lastVelocity;
        private long lastCriticalWarningTick = Long.MIN_VALUE;
        private int postCrashDampingTicks;
        private double postCrashDamping;
        private double leftWingDamage;
        private double rightWingDamage;
        private int groundSettleTicks;
        private double groundSettleStrength;
    }

    private record ShipBounds(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
    }
}
