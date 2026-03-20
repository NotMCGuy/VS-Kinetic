package com.vskinetic.ship;

import com.vskinetic.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.LoadedServerShip;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ShipCrashConsequences {
    private static final double MIN_IMPULSE_FOR_FRONT_CRUMPLE = 2.5D;
    private static final double MIN_DECK_IMPULSE_RATIO = 0.35D;
    private static final double BELLY_SCRAPE_MIN_HORIZONTAL_FRACTION = 0.85D;
    private static final double BELLY_SCRAPE_MIN_DOWNWARD_SPEED = 3.0D;

    private final List<PendingExplosion> pendingExplosions = new ArrayList<>();

    public void processPendingExplosions(ServerLevel level) {
        Iterator<PendingExplosion> iterator = pendingExplosions.iterator();
        while (iterator.hasNext()) {
            PendingExplosion pending = iterator.next();
            if (!pending.dimension.equals(level.dimension().location().toString())) {
                continue;
            }
            if (level.getGameTime() < pending.triggerTick) {
                continue;
            }

            double ex = pending.worldPosition.x(), ey = pending.worldPosition.y(), ez = pending.worldPosition.z();
            level.sendParticles(ParticleTypes.LARGE_SMOKE, ex, ey, ez, 60, 3.0D, 2.0D, 3.0D, 0.06D);
            level.sendParticles(ParticleTypes.EXPLOSION, ex, ey, ez, 10, 2.0D, 1.5D, 2.0D, 0.10D);
            playAt(level, pending.worldPosition, SoundEvents.IRON_GOLEM_DEATH, 1.5f, 0.5f);
            level.explode(null, null, null, ex, ey, ez, pending.power, true, Level.ExplosionInteraction.BLOCK);
            iterator.remove();
        }
    }

    public void applyCrashEffects(
            ServerLevel level,
            LoadedServerShip ship,
            CrashPhysicsEngine.CrashResult result,
            Vec3 previousVelocity,
            Vec3 currentVelocity
    ) {
        ImpactProfile profile = classifyImpact(ship, result, previousVelocity, currentVelocity);
        if (profile == null) {
            return;
        }

    boolean catastrophic = result.severity() == CrashPhysicsEngine.CrashSeverity.CATASTROPHIC;
    // All sounds and particles use the visual world-space impact position
    Vector3d soundPos = ship.getTransform().getShipToWorld()
        .transformPosition(profile.impactPoint, new Vector3d());

    applyWallBreachEffects(level, ship, profile, result, previousVelocity);
    if (profile.deckImpact) {
        excavateUnderbodyCrater(level, ship, result, previousVelocity);
    }

    // Front crumple: nose hits an obstacle — only for non-belly impacts
    if (!profile.bellyScrape
        && (result.severity() == CrashPhysicsEngine.CrashSeverity.HARD
            || result.severity() == CrashPhysicsEngine.CrashSeverity.CATASTROPHIC)) {
        crumpleFront(level, ship, profile, result);
        // Thud + structural groan scaled to severity
        playAt(level, soundPos, SoundEvents.ANVIL_LAND, catastrophic ? 1.5f : 1.2f, catastrophic ? 0.5f : 0.8f);
        playAt(level, soundPos,
            catastrophic ? SoundEvents.IRON_GOLEM_DEATH : SoundEvents.IRON_GOLEM_HURT,
            catastrophic ? 2.0f : 1.4f,
            catastrophic ? 0.6f : 0.75f);
        if (catastrophic) {
        // Resonant crack for violent nose slams
        playAt(level, soundPos, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.4f);
        }
        level.sendParticles(ParticleTypes.EXPLOSION,
            soundPos.x(), soundPos.y(), soundPos.z(), 6, 1.5D, 1.0D, 1.5D, 0.08D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
            soundPos.x(), soundPos.y(), soundPos.z(), catastrophic ? 25 : 12, 2.0D, 1.5D, 2.0D, 0.05D);
    }

    // Belly scrape: ship slides along surface on its underside
    if (profile.bellyScrape) {
        applyScrapeEffects(level, ship, profile, result, previousVelocity);
        // Lower-pitched thud (hitting ground sideways) + metallic grinding groan
        playAt(level, soundPos, SoundEvents.ANVIL_LAND, 1.0f, 0.7f);
        playAt(level, soundPos, SoundEvents.IRON_GOLEM_HURT, 1.5f, 0.6f);
        if (catastrophic) {
        playAt(level, soundPos, SoundEvents.IRON_GOLEM_DEATH, 1.0f, 0.55f);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
            soundPos.x(), soundPos.y(), soundPos.z(), catastrophic ? 30 : 15, 3.0D, 0.5D, 3.0D, 0.04D);
    }

    // Gear collapse: high vertical impact compresses the bottom hull zone
    if (profile.deckImpact
        && (result.severity() == CrashPhysicsEngine.CrashSeverity.HARD
            || result.severity() == CrashPhysicsEngine.CrashSeverity.CATASTROPHIC)) {
        applyGearCollapseEffects(level, ship, profile, result);
        // Deep thud — the ship's full weight slamming down
        playAt(level, soundPos, SoundEvents.ANVIL_LAND, catastrophic ? 2.5f : 2.0f, catastrophic ? 0.4f : 0.5f);
        playAt(level, soundPos,
            catastrophic ? SoundEvents.IRON_GOLEM_DEATH : SoundEvents.IRON_GOLEM_HURT,
            catastrophic ? 1.5f : 1.2f,
            catastrophic ? 0.55f : 0.65f);
        level.sendParticles(ParticleTypes.EXPLOSION,
            soundPos.x(), soundPos.y(), soundPos.z(), 4, 1.0D, 0.5D, 1.0D, 0.05D);
    }

    // Catastrophic explosion: steep deck slam (not a shallow belly scrape)
    if (catastrophic && profile.deckImpact && !profile.bellyScrape) {
        queueExplosion(level, ship, profile, result);
    }
    }

    private ImpactProfile classifyImpact(
            LoadedServerShip ship,
            CrashPhysicsEngine.CrashResult result,
            Vec3 previousVelocity,
            Vec3 currentVelocity
    ) {
        if (previousVelocity == null) {
            return null;
        }

        Vec3 approachVelocity = previousVelocity.lengthSqr() >= currentVelocity.lengthSqr() ? previousVelocity : currentVelocity;
        if (approachVelocity.lengthSqr() < 1.0E-4D) {
            return null;
        }

        Vec3 impactImpulse = currentVelocity.subtract(previousVelocity);
        if (impactImpulse.length() < MIN_IMPULSE_FOR_FRONT_CRUMPLE) {
            return null;
        }

        double downwardSpeed = Math.max(0.0D, -previousVelocity.y);
        double upwardImpulse = Math.max(0.0D, impactImpulse.y);
        boolean deckImpact = downwardSpeed >= Config.deckImpactVerticalSpeed
                && upwardImpulse >= Math.max(0.5D, downwardSpeed * MIN_DECK_IMPULSE_RATIO);

        Vector3d travelDirWorld = normalize(approachVelocity);
        Vector3d impulseDirWorld = normalize(impactImpulse);

        double horizontalSpeed = Math.sqrt(
                previousVelocity.x * previousVelocity.x + previousVelocity.z * previousVelocity.z);
        double totalSpeed = previousVelocity.length();
        boolean bellyScrape = totalSpeed > 0.1D
                && (horizontalSpeed / totalSpeed) > BELLY_SCRAPE_MIN_HORIZONTAL_FRACTION
                && downwardSpeed >= BELLY_SCRAPE_MIN_DOWNWARD_SPEED
                && upwardImpulse >= downwardSpeed * 0.3D;

        double counterImpulse = -impactImpulse.dot(approachVelocity.normalize());
        if (!deckImpact && !bellyScrape
                && counterImpulse < Math.max(MIN_IMPULSE_FOR_FRONT_CRUMPLE, result.deltaV() * 0.4D)) {
            return null;
        }

        Vector3d travelDirLocal = ship.getTransform().getWorldToShip().transformDirection(travelDirWorld, new Vector3d());
        if (travelDirLocal.lengthSquared() < 1.0E-6D) {
            return null;
        }
        travelDirLocal.normalize();

        ShipBounds bounds = getShipBounds(ship);
        Vector3d impactPoint;
        if (bounds != null) {
            impactPoint = supportPoint(bounds, travelDirLocal);
            impactPoint.sub(new Vector3d(travelDirLocal).mul(0.85D));
            if (deckImpact) {
                Vector3d bottomPoint = supportPoint(bounds, new Vector3d(0.0D, -1.0D, 0.0D));
                impactPoint = impactPoint.mul(0.65D, new Vector3d()).add(bottomPoint.mul(0.35D, new Vector3d()));
            }
        } else {
            // Fallback: keep effects active even if ship AABB introspection is unavailable.
            impactPoint = new Vector3d(travelDirLocal).mul(0.75D);
        }

        return new ImpactProfile(impactPoint, travelDirLocal, impulseDirWorld, deckImpact, bellyScrape);
    }

    private void crumpleFront(
            ServerLevel level,
            LoadedServerShip ship,
            ImpactProfile profile,
            CrashPhysicsEngine.CrashResult result
    ) {
        double severityScale = switch (result.severity()) {
            case SCRAPE -> 0.70D;
            case HARD -> 1.0D;
            case CATASTROPHIC -> 1.55D;
            default -> 0.0D;
        };
        if (severityScale <= 0.0D) {
            return;
        }

        double depth = Config.frontCrumpleDepth * severityScale * Config.terrainBreachFactor;
        double radius = Config.frontCrumpleRadius * severityScale * Math.sqrt(Math.max(0.0D, Config.terrainBreachFactor));
        Vector3d backward = new Vector3d(profile.travelDirLocal).negate().normalize();

        BlockPos center = BlockPos.containing(profile.impactPoint.x(), profile.impactPoint.y(), profile.impactPoint.z());
        int extent = (int) Math.ceil(Math.max(depth, radius)) + 1;

        for (int dx = -extent; dx <= extent; dx++) {
            for (int dy = -extent; dy <= extent; dy++) {
                for (int dz = -extent; dz <= extent; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    Vector3d sample = new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
                    Vector3d offset = sample.sub(profile.impactPoint, new Vector3d());
                    double longitudinal = offset.dot(backward);
                    if (longitudinal < -0.5D || longitudinal > depth) {
                        continue;
                    }

                    Vector3d lateral = offset.sub(new Vector3d(backward).mul(longitudinal));
                    double allowedRadius = lerp(radius * 0.45D, radius, clamp01(longitudinal / Math.max(0.001D, depth)));
                    if (lateral.length() > allowedRadius) {
                        continue;
                    }

                    BlockPos worldPos = localToWorldPos(ship, sample);
                    BlockState state = level.getBlockState(worldPos);
                    if (state.isAir() || state.getDestroySpeed(level, worldPos) < 0.0F) {
                        continue;
                    }

                    if (result.severity() == CrashPhysicsEngine.CrashSeverity.HARD
                            && lateral.length() > allowedRadius * 0.7D
                            && level.random.nextFloat() < 0.45F) {
                        continue;
                    }

                    level.destroyBlock(worldPos, false);
                }
            }
        }
    }

    private void applyScrapeEffects(
            ServerLevel level,
            LoadedServerShip ship,
            ImpactProfile profile,
            CrashPhysicsEngine.CrashResult result,
            Vec3 previousVelocity
    ) {
        ShipBounds bounds = getShipBounds(ship);
        if (bounds == null) {
            return;
        }

        double horizontalSpeed = Math.sqrt(
                previousVelocity.x * previousVelocity.x + previousVelocity.z * previousVelocity.z);

        // Project travel direction onto the ship's XZ plane (horizontal in ship space)
        Vector3d horiz = new Vector3d(profile.travelDirLocal.x(), 0.0D, profile.travelDirLocal.z());
        if (horiz.lengthSquared() < 1.0E-6D) {
            return;
        }
        horiz.normalize();

        // Perpendicular to forward in the XZ plane: horiz x (0,1,0)
        Vector3d perp = horiz.cross(new Vector3d(0.0D, 1.0D, 0.0D), new Vector3d());
        if (perp.lengthSquared() < 1.0E-6D) {
            perp.set(1.0D, 0.0D, 0.0D);
        } else {
            perp.normalize();
        }

        double stripLength = Math.min(horizontalSpeed * Config.bellyScrapeBlocksPerMps * Config.terrainBreachFactor, 24.0D);
        double centerX = (bounds.minX() + bounds.maxX()) / 2.0D;
        double centerZ = (bounds.minZ() + bounds.maxZ()) / 2.0D;
        double bottomY = bounds.minY();

        // Strip centered on ship bottom along travel direction
        Vector3d startLocal = new Vector3d(centerX, bottomY, centerZ)
                .sub(new Vector3d(horiz).mul(stripLength * 0.5D));

        boolean catastrophic = result.severity() == CrashPhysicsEngine.CrashSeverity.CATASTROPHIC;
        float fireChance = catastrophic ? 0.12F : 0.06F;

        for (int s = 0; s <= (int) (stripLength + 0.5D); s++) {
            Vector3d sCenter = new Vector3d(startLocal).add(new Vector3d(horiz).mul(s));
            for (int w = -1; w <= 1; w++) {
                for (int dy = 0; dy <= 1; dy++) {
                    Vector3d sample = new Vector3d(sCenter)
                            .add(new Vector3d(perp).mul(w))
                            .add(0.0D, dy, 0.0D);
                    BlockPos pos = localToWorldPos(ship, sample);
                    if (pos.getY() < level.getMinBuildHeight()) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
                        continue;
                    }
                    // HARD: spare upper outer-edge blocks probabilistically
                    if (result.severity() == CrashPhysicsEngine.CrashSeverity.HARD
                            && dy == 1 && Math.abs(w) == 1
                            && level.random.nextFloat() < 0.55F) {
                        continue;
                    }
                    level.destroyBlock(pos, false);
                    if (level.random.nextFloat() < fireChance) {
                        BlockPos firePos = pos.above();
                        if (level.getBlockState(firePos).isAir()) {
                            level.setBlock(firePos, Blocks.FIRE.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }

        // Small trailing fireball for catastrophic belly scrapes
        if (catastrophic) {
            Vector3d scrapeEndLocal = new Vector3d(startLocal).add(new Vector3d(horiz).mul(stripLength));
            Vector3d worldPos = ship.getTransform().getShipToWorld()
                    .transformPosition(scrapeEndLocal, new Vector3d());
            pendingExplosions.add(new PendingExplosion(
                    level.dimension().location().toString(),
                    level.getGameTime() + Math.max(0, Config.catastrophicExplosionDelayTicks),
                    worldPos,
                    (float) Math.max(2.5D, Config.catastrophicExplosionPower * 0.55D)
            ));
        }
    }

    private void applyGearCollapseEffects(
            ServerLevel level,
            LoadedServerShip ship,
            ImpactProfile profile,
            CrashPhysicsEngine.CrashResult result
    ) {
        ShipBounds bounds = getShipBounds(ship);
        if (bounds == null) {
            return;
        }

        boolean catastrophic = result.severity() == CrashPhysicsEngine.CrashSeverity.CATASTROPHIC;
        float destroyChance = catastrophic ? 1.0F : (float) Config.gearCollapseDestructionFactor;

        double centerX = (bounds.minX() + bounds.maxX()) / 2.0D;
        double centerZ = (bounds.minZ() + bounds.maxZ()) / 2.0D;
        double bottomY = bounds.minY();

        int halfSizeX = (int) Math.max(1, Math.min(Math.ceil((bounds.maxX() - bounds.minX()) * 0.35D * Config.terrainBreachFactor), 8));
        int halfSizeZ = (int) Math.max(1, Math.min(Math.ceil((bounds.maxZ() - bounds.minZ()) * 0.35D * Config.terrainBreachFactor), 8));

        for (int dx = -halfSizeX; dx <= halfSizeX; dx++) {
            for (int dz = -halfSizeZ; dz <= halfSizeZ; dz++) {
                // Elliptical footprint
                double ellipse = (dx * dx / (double) (halfSizeX * halfSizeX))
                        + (dz * dz / (double) (halfSizeZ * halfSizeZ));
                if (ellipse > 1.0D) {
                    continue;
                }
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos pos = localToWorldPos(ship, new Vector3d(centerX + dx, bottomY + dy, centerZ + dz));
                    if (pos.getY() < level.getMinBuildHeight()) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
                        continue;
                    }
                    if (level.random.nextFloat() < destroyChance) {
                        level.destroyBlock(pos, false);
                        if (catastrophic && level.random.nextFloat() < 0.15F) {
                            BlockPos firePos = pos.above();
                            if (level.getBlockState(firePos).isAir()) {
                                level.setBlock(firePos, Blocks.FIRE.defaultBlockState(), 3);
                            }
                        }
                    }
                }
            }
        }
    }

    private void applyWallBreachEffects(
            ServerLevel level,
            LoadedServerShip ship,
            ImpactProfile profile,
            CrashPhysicsEngine.CrashResult result,
            Vec3 previousVelocity
    ) {
        double horizontalSpeed = Math.sqrt(previousVelocity.x * previousVelocity.x + previousVelocity.z * previousVelocity.z);
        if (horizontalSpeed < 2.0D) {
            return;
        }

        double mass = Math.max(1.0D, ship.getInertiaData().getShipMass());
        double momentum = mass * horizontalSpeed;
        double minimumMomentum = result.severity() == CrashPhysicsEngine.CrashSeverity.SCRAPE ? 1800.0D : 1200.0D;
        if (momentum < minimumMomentum) {
            return;
        }

        Vector3d forward = new Vector3d(profile.travelDirLocal.x(), 0.0D, profile.travelDirLocal.z());
        if (forward.lengthSquared() < 1.0E-6D) {
            return;
        }
        forward.normalize();

        Vector3d side = new Vector3d(0.0D, 1.0D, 0.0D).cross(forward, new Vector3d());
        if (side.lengthSquared() < 1.0E-6D) {
            side.set(1.0D, 0.0D, 0.0D);
        } else {
            side.normalize();
        }

        double severityScale = switch (result.severity()) {
            case SCRAPE -> 0.85D;
            case HARD -> 1.25D;
            case CATASTROPHIC -> 1.8D;
            case NONE -> 0.0D;
        };
        if (severityScale <= 0.0D) {
            return;
        }

        ShipBounds bounds = getShipBounds(ship);
        double sizeX = bounds != null ? Math.max(1.0D, bounds.maxX() - bounds.minX()) : 4.0D;
        double sizeY = bounds != null ? Math.max(1.0D, bounds.maxY() - bounds.minY()) : 3.0D;
        double sizeZ = bounds != null ? Math.max(1.0D, bounds.maxZ() - bounds.minZ()) : 6.0D;
        double lateralSpan = Math.abs(side.x()) * sizeX + Math.abs(side.z()) * sizeZ;
        int halfWidth = (int) Math.max(1, Math.min(Math.ceil(lateralSpan * 0.5D), 14));
        int halfHeight = (int) Math.max(1, Math.min(Math.ceil(sizeY * 0.5D), 10));
        int depth = (int) Math.max(1, Math.min(
                Math.ceil((horizontalSpeed * 0.55D + Math.log10(mass + 1.0D) * 1.35D) * severityScale * Config.terrainBreachFactor),
                16
        ));

        for (int d = 0; d <= depth; d++) {
            for (int w = -halfWidth; w <= halfWidth; w++) {
                for (int h = -halfHeight; h <= halfHeight; h++) {
                    double ellipse = (w * w / (double) (halfWidth * halfWidth))
                            + (h * h / (double) (halfHeight * halfHeight));
                    if (ellipse > 1.20D) {
                        continue;
                    }

                    Vector3d sampleLocal = new Vector3d(profile.impactPoint)
                            .add(new Vector3d(forward).mul(d))
                            .add(new Vector3d(side).mul(w))
                            .add(0.0D, h, 0.0D);
                    BlockPos pos = localToWorldPos(ship, sampleLocal);
                    if (pos.getY() < level.getMinBuildHeight()) {
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
                        continue;
                    }

                    double breakPower = momentum / (350.0D + d * 115.0D);
                    if (result.severity() == CrashPhysicsEngine.CrashSeverity.SCRAPE) {
                        breakPower *= 0.7D;
                    }
                    float hardness = state.getDestroySpeed(level, pos);
                    if (hardness > 0.0F && breakPower < hardness * 2.4D && level.random.nextFloat() < 0.45F) {
                        continue;
                    }

                    level.destroyBlock(pos, false);
                }
            }
        }
    }

    private void excavateUnderbodyCrater(
            ServerLevel level,
            LoadedServerShip ship,
            CrashPhysicsEngine.CrashResult result,
            Vec3 previousVelocity
    ) {
        ShipBounds bounds = getShipBounds(ship);
        if (bounds == null) {
            return;
        }

        double downwardSpeed = Math.max(0.0D, -previousVelocity.y);
        if (downwardSpeed < 2.5D) {
            return;
        }

        double sizeX = Math.max(1.0D, bounds.maxX() - bounds.minX());
        double sizeZ = Math.max(1.0D, bounds.maxZ() - bounds.minZ());
        int halfX = (int) Math.max(1, Math.min(Math.ceil(sizeX * 0.5D), 12));
        int halfZ = (int) Math.max(1, Math.min(Math.ceil(sizeZ * 0.5D), 12));
        int depth = (int) Math.max(1, Math.min(
                Math.ceil((0.6D + downwardSpeed * 0.35D) * Config.terrainBreachFactor
                        + (result.severity() == CrashPhysicsEngine.CrashSeverity.CATASTROPHIC ? 2.0D : 0.0D)),
                7
        ));

        double centerX = (bounds.minX() + bounds.maxX()) * 0.5D;
        double centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5D;
        double baseY = bounds.minY() - 0.25D;

        for (int dx = -halfX; dx <= halfX; dx++) {
            for (int dz = -halfZ; dz <= halfZ; dz++) {
                double ellipse = (dx * dx / (double) (halfX * halfX))
                        + (dz * dz / (double) (halfZ * halfZ));
                if (ellipse > 1.0D) {
                    continue;
                }
                int localDepth = Math.max(1, (int) Math.ceil(depth * (1.0D - ellipse * 0.55D)));
                for (int dy = 0; dy <= localDepth; dy++) {
                    Vector3d local = new Vector3d(centerX + dx, baseY - dy, centerZ + dz);
                    BlockPos pos = localToWorldPos(ship, local);
                    if (pos.getY() < level.getMinBuildHeight()) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
                        continue;
                    }
                    level.destroyBlock(pos, false);
                }
            }
        }
    }

    private void queueExplosion(
            ServerLevel level,
            LoadedServerShip ship,
            ImpactProfile profile,
            CrashPhysicsEngine.CrashResult result
    ) {
        Vector3d worldPosition = ship.getTransform().getShipToWorld().transformPosition(profile.impactPoint, new Vector3d());
        double sizeScale = estimateShipExplosionScale(ship);
        float power = (float) Math.max(Config.catastrophicExplosionPower, Math.min(
                16.0D,
                Config.catastrophicExplosionPower * sizeScale + Math.min(4.5D, result.damage() / 14.0D)
        ));
        pendingExplosions.add(new PendingExplosion(
                level.dimension().location().toString(),
                level.getGameTime() + Math.max(0, Config.catastrophicExplosionDelayTicks),
                worldPosition,
                power
        ));
    }

    private static Vector3d normalize(Vec3 vector) {
        return normalize(new Vector3d(vector.x, vector.y, vector.z));
    }

    private static Vector3d normalize(Vector3d vector) {
        if (vector.lengthSquared() < 1.0E-8D) {
            return vector;
        }
        return vector.normalize();
    }

    private static Vector3d supportPoint(ShipBounds bounds, Vector3dc direction) {
        return new Vector3d(
                direction.x() >= 0.0D ? bounds.maxX() + 0.5D : bounds.minX() + 0.5D,
                direction.y() >= 0.0D ? bounds.maxY() + 0.5D : bounds.minY() + 0.5D,
                direction.z() >= 0.0D ? bounds.maxZ() + 0.5D : bounds.minZ() + 0.5D
        );
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
            // Try bean-style getters and public fields for API/version compatibility.
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

    private static double lerp(double start, double end, double delta) {
        return start + (end - start) * delta;
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static BlockPos localToWorldPos(LoadedServerShip ship, Vector3dc localPos) {
        Vector3d world = ship.getTransform().getShipToWorld().transformPosition(localPos, new Vector3d());
        return BlockPos.containing(world.x(), world.y(), world.z());
    }

    private static double estimateShipExplosionScale(LoadedServerShip ship) {
        ShipBounds bounds = getShipBounds(ship);
        if (bounds != null) {
            double sizeX = Math.max(1.0D, bounds.maxX() - bounds.minX());
            double sizeY = Math.max(1.0D, bounds.maxY() - bounds.minY());
            double sizeZ = Math.max(1.0D, bounds.maxZ() - bounds.minZ());
            double cubeRootVolume = Math.cbrt(sizeX * sizeY * sizeZ);
            double normalized = clamp01((cubeRootVolume - 3.0D) / 16.0D);
            return 1.0D + normalized * Config.shipSizeExplosionScale;
        }

        double mass = Math.max(1.0D, ship.getInertiaData().getShipMass());
        double normalizedMass = clamp01((Math.log10(mass + 1.0D) - 2.5D) / 2.5D);
        return 1.0D + normalizedMass * Config.shipSizeExplosionScale;
    }

    private static void playAt(ServerLevel level, Vector3d pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x(), pos.y(), pos.z(), sound, SoundSource.BLOCKS, volume, pitch);
    }

    private record ImpactProfile(
            Vector3d impactPoint,
            Vector3d travelDirLocal,
            Vector3d impulseDirWorld,
            boolean deckImpact,
            boolean bellyScrape
    ) {
    }

    private record PendingExplosion(String dimension, long triggerTick, Vector3d worldPosition, float power) {
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
