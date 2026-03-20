package com.vskinetic.ship;

import com.vskinetic.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
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

        ShipCrashHooks.PhysicsSampleOutcome outcome = ShipCrashHooks.onShipPhysicsSampleDetailed(
                level.getServer(),
                shipId,
                level.getGameTime(),
                velocity,
                ship.getInertiaData().getShipMass(),
                hadCollisionSignal
        );

        runtimeState.lastVelocity = velocity;

        ShipBindingRecord record = outcome.record();
        CrashPhysicsEngine.CrashResult result = outcome.result();
        if (result.crash()) {
            crashConsequences.applyCrashEffects(level, ship, result, previousVelocity, velocity);
            notifyCrash(level, ship, record, result);
            applyCrashBraking(ship, result);
        }

        applyIntegrityDrag(ship, record);
        warnCriticalIntegrity(level, ship, record, runtimeState);
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

    private static String prettySeverity(CrashPhysicsEngine.CrashSeverity severity) {
        return switch (severity) {
            case SCRAPE -> "scrape";
            case HARD -> "hard";
            case CATASTROPHIC -> "catastrophic";
            case NONE -> "non-event";
        };
    }

    private static final class RuntimeShipState {
        private Vec3 lastVelocity;
        private long lastCriticalWarningTick = Long.MIN_VALUE;
    }
}
