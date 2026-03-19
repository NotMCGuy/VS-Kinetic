package com.vskinetic.ship;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.joml.Vector3dc;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.Comparator;
import java.util.UUID;

public final class ShipCommands {
    private ShipCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("vsmd")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("pair")
                                .then(Commands.argument("shipId", LongArgumentType.longArg(0L))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> {
                                                    long shipId = LongArgumentType.getLong(ctx, "shipId");
                                                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                    UUID playerId = player.getUUID();

                                                    ShipBindingRecord record = data(ctx.getSource()).pairShip(shipId, playerId);
                                                    ctx.getSource().sendSuccess(() -> Component.literal("Paired ship " + shipId
                                                            + " with " + player.getGameProfile().getName()
                                                            + " | slug=" + record.slug()), true);
                                                    return 1;
                                                }))))
                        .then(Commands.literal("rename")
                                .then(Commands.argument("shipId", LongArgumentType.longArg(0L))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    long shipId = LongArgumentType.getLong(ctx, "shipId");
                                                    String name = StringArgumentType.getString(ctx, "name").trim();
                                                    if (name.isBlank()) {
                                                        ctx.getSource().sendFailure(Component.literal("Name cannot be blank."));
                                                        return 0;
                                                    }

                                                    ShipBindingRecord record = data(ctx.getSource()).renameShip(shipId, name);
                                                    ctx.getSource().sendSuccess(() -> Component.literal("Renamed ship " + shipId
                                                            + " -> \"" + record.displayName() + "\""
                                                            + " | slug=" + record.slug()), true);
                                                    return 1;
                                                }))))
                        .then(Commands.literal("creator")
                                .then(Commands.argument("shipId", LongArgumentType.longArg(0L))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> {
                                                    long shipId = LongArgumentType.getLong(ctx, "shipId");
                                                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");

                                                    ShipBindingRecord record = data(ctx.getSource()).setCreator(shipId, player.getUUID());
                                                    ctx.getSource().sendSuccess(() -> Component.literal("Set creator for ship " + shipId
                                                            + " -> " + player.getGameProfile().getName()), true);
                                                    return record.createdBy() != null ? 1 : 0;
                                                })))
                                .then(Commands.literal("inferOwner")
                                        .then(Commands.argument("shipId", LongArgumentType.longArg(0L))
                                                .executes(ctx -> {
                                                    long shipId = LongArgumentType.getLong(ctx, "shipId");
                                                    ShipRegistryData store = data(ctx.getSource());
                                                    ShipBindingRecord record = store.getOrCreate(shipId);
                                                    if (record.owner() == null) {
                                                        ctx.getSource().sendFailure(Component.literal("Ship has no owner to infer creator from."));
                                                        return 0;
                                                    }
                                                    if (record.createdBy() != null) {
                                                        ctx.getSource().sendFailure(Component.literal("Ship already has a creator."));
                                                        return 0;
                                                    }

                                                    store.setCreatorIfMissing(shipId, record.owner());
                                                    ctx.getSource().sendSuccess(() -> Component.literal("Creator inferred from owner for ship " + shipId), true);
                                                    return 1;
                                                }))))
                        .then(Commands.literal("crash")
                                .then(Commands.argument("shipId", LongArgumentType.longArg(0L))
                                        .executes(ctx -> {
                                            long shipId = LongArgumentType.getLong(ctx, "shipId");
                                            ShipBindingRecord record = data(ctx.getSource()).markCrashed(shipId);
                                            ctx.getSource().sendSuccess(() -> Component.literal("Ship " + shipId
                                                    + " marked crashed | slug=" + record.slug()), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("recover")
                                .then(Commands.argument("shipId", LongArgumentType.longArg(0L))
                                        .executes(ctx -> {
                                            long shipId = LongArgumentType.getLong(ctx, "shipId");
                                            ShipBindingRecord record = data(ctx.getSource()).recoverShip(shipId);
                                            ctx.getSource().sendSuccess(() -> Component.literal("Ship " + shipId
                                                    + " recovered | slug=" + record.slug()), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("physics")
                                .then(Commands.literal("sample")
                                        .then(Commands.argument("shipId", LongArgumentType.longArg(0L))
                                                .then(Commands.argument("vx", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("vy", DoubleArgumentType.doubleArg())
                                                                .then(Commands.argument("vz", DoubleArgumentType.doubleArg())
                                                                        .then(Commands.argument("mass", DoubleArgumentType.doubleArg(1.0D))
                                                                                .then(Commands.argument("collision", BoolArgumentType.bool())
                                                                                        .executes(ctx -> {
                                                                                            long shipId = LongArgumentType.getLong(ctx, "shipId");
                                                                                            double vx = DoubleArgumentType.getDouble(ctx, "vx");
                                                                                            double vy = DoubleArgumentType.getDouble(ctx, "vy");
                                                                                            double vz = DoubleArgumentType.getDouble(ctx, "vz");
                                                                                            double mass = DoubleArgumentType.getDouble(ctx, "mass");
                                                                                            boolean collision = BoolArgumentType.getBool(ctx, "collision");

                                                                                            ShipCrashHooks.PhysicsSampleOutcome outcome = ShipCrashHooks.onShipPhysicsSampleDetailed(
                                                                                                    ctx.getSource().getServer(),
                                                                                                    shipId,
                                                                                                    ctx.getSource().getServer().getTickCount(),
                                                                                                    new Vec3(vx, vy, vz),
                                                                                                    mass,
                                                                                                    collision
                                                                                            );
                                                                                            ShipBindingRecord record = outcome.record();
                                                                                            CrashPhysicsEngine.CrashResult result = outcome.result();

                                                                                            ctx.getSource().sendSuccess(() -> Component.literal("Physics sample applied | crashed="
                                                                                                    + record.crashed()
                                                                                                    + " | severity=" + result.severity().name().toLowerCase()
                                                                                                    + " | damage=" + String.format("%.2f", result.damage())
                                                                                                    + " | integrity=" + String.format("%.2f", record.structuralIntegrity())
                                                                                                    + " | slug=" + record.slug()), true);
                                                                                            return 1;
                                                                                        })))))))))
                                .then(Commands.literal("sample_here")
                                        .then(Commands.argument("collision", BoolArgumentType.bool())
                                                .executes(ctx -> {
                                                    boolean collision = BoolArgumentType.getBool(ctx, "collision");
                                                    CommandSourceStack source = ctx.getSource();
                                                    ServerLevel level = source.getLevel();
                                                    Vec3 pos = source.getPosition();

                                                    LoadedServerShip ship = VSGameUtilsKt.getShipObjectManagingPos(level, pos.x, pos.y, pos.z);
                                                    if (ship == null) {
                                                        source.sendFailure(Component.literal("No Valkyrien Skies ship found at your current position."));
                                                        return 0;
                                                    }

                                                    Vector3dc velocity = ship.getVelocity();
                                                    ShipCrashHooks.PhysicsSampleOutcome outcome = ShipCrashHooks.onShipPhysicsSampleDetailed(
                                                            source.getServer(),
                                                            ship.getId(),
                                                            source.getServer().getTickCount(),
                                                            new Vec3(velocity.x(), velocity.y(), velocity.z()),
                                                            ship.getInertiaData().getShipMass(),
                                                            collision
                                                    );

                                                    ShipBindingRecord record = outcome.record();
                                                    CrashPhysicsEngine.CrashResult result = outcome.result();
                                                    source.sendSuccess(() -> Component.literal("VS sample applied | shipId=" + ship.getId()
                                                            + " | crashed=" + record.crashed()
                                                            + " | severity=" + result.severity().name().toLowerCase()
                                                            + " | damage=" + String.format("%.2f", result.damage())
                                                            + " | integrity=" + String.format("%.2f", record.structuralIntegrity())
                                                            + " | slug=" + record.slug()), true);
                                                    return 1;
                                                })))
                        .then(Commands.literal("show")
                                .then(Commands.argument("shipId", LongArgumentType.longArg(0L))
                                        .executes(ctx -> {
                                            long shipId = LongArgumentType.getLong(ctx, "shipId");
                                            ShipBindingRecord record = data(ctx.getSource()).getShip(shipId);
                                            if (record == null) {
                                                ctx.getSource().sendFailure(Component.literal("No record for ship " + shipId));
                                                return 0;
                                            }

                                            ctx.getSource().sendSuccess(() -> Component.literal(format(record)), false);
                                            return 1;
                                        })))
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    ShipRegistryData store = data(ctx.getSource());
                                    if (store.allShips().isEmpty()) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("No tracked ships yet."), false);
                                        return 1;
                                    }

                                    ctx.getSource().sendSuccess(() -> Component.literal("Tracked ships:"), false);
                                    store.allShips().stream()
                                            .sorted(Comparator.comparingLong(ShipBindingRecord::shipId))
                                            .limit(50)
                                            .forEach(record -> ctx.getSource().sendSuccess(() -> Component.literal(" - " + format(record)), false));
                                    return 1;
                                }))
        );
    }

    private static ShipRegistryData data(CommandSourceStack source) {
        return ShipRegistryData.get(source.getServer());
    }

    private static String format(ShipBindingRecord record) {
        String owner = record.owner() == null ? "none" : record.owner().toString();
        String creator = record.createdBy() == null ? "unknown" : record.createdBy().toString();
        return "id=" + record.shipId()
                + " name=\"" + record.displayName() + "\""
                + " slug=" + record.slug()
                + " crashed=" + record.crashed()
                + " crashes=" + record.crashCount()
                + " integrity=" + String.format("%.2f", record.structuralIntegrity())
                + " lastSeverity=" + record.lastCrashSeverity()
                + " lastEnergy=" + String.format("%.2f", record.lastImpactEnergy())
                + " peakEnergy=" + String.format("%.2f", record.peakImpactEnergy())
                + " lastCrashTick=" + record.lastCrashTick()
                + " owner=" + owner
                + " creator=" + creator;
    }
}
