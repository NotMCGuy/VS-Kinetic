package com.vskinetic.ship;

import com.vskinetic.Config;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CrashPhysicsEngine {
    private final Map<Long, ShipMotionState> stateByShip = new HashMap<>();

    public enum ImpactPart {
        AUTO,
        HULL,
        ENGINE,
        LIFT,
        CONTROL;

        public static ImpactPart parseOrAuto(String value) {
            if (value == null || value.isBlank()) {
                return AUTO;
            }
            try {
                return ImpactPart.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return AUTO;
            }
        }
    }

    public CrashResult sample(long shipId, long gameTime, Vec3 velocity, double mass, boolean collisionSignal) {
        ShipMotionState state = stateByShip.computeIfAbsent(shipId, ignored -> new ShipMotionState());

        if (!state.initialized) {
            state.initialized = true;
            state.lastTick = gameTime;
            state.lastVelocity = velocity;
            return CrashResult.noCrash(velocity.length(), 0.0D, 0.0D);
        }

        double speed = velocity.length();
        double previousSpeed = state.lastVelocity.length();
        double effectiveSpeed = Math.max(speed, previousSpeed);
        double deltaV = velocity.subtract(state.lastVelocity).length();
        double previousDownwardSpeed = Math.max(0.0D, -state.lastVelocity.y);
        double clampedMass = Math.max(1.0D, mass);
        double momentum = clampedMass * effectiveSpeed;
        double impactEnergy = 0.5D * clampedMass * deltaV * deltaV;
        double crashScore = score(speed, deltaV, impactEnergy);

        boolean collisionGate = !Config.requireCollisionSignal || collisionSignal;
        boolean verticalCollisionImpact = collisionSignal
                && previousDownwardSpeed >= Math.max(1.0D, Config.deckImpactVerticalSpeed * 0.5D);
        boolean wallRamImpact = collisionSignal
                && effectiveSpeed >= 3.0D
                && momentum >= 1800.0D;
        boolean enoughSpeed = speed >= Config.minCrashSpeed || verticalCollisionImpact || wallRamImpact;
        boolean enoughImpulse = deltaV >= Config.minDeltaV || impactEnergy >= Config.minImpactEnergy;
        boolean cooldownReady = gameTime >= state.cooldownUntilTick;

        boolean crash = collisionGate && enoughSpeed && enoughImpulse && cooldownReady;
        CrashSeverity severity = crash ? classifySeverity(crashScore) : CrashSeverity.NONE;
        double damage = crash ? damageFor(severity, crashScore) : 0.0D;
        double bounceDamping = dampingFor(severity);
        if (crash) {
            state.cooldownUntilTick = gameTime + Math.max(0, Config.crashCooldownTicks);
        }

        state.lastTick = gameTime;
        state.lastVelocity = velocity;

        return crash
                ? CrashResult.crash(speed, deltaV, impactEnergy, crashScore, severity, damage, bounceDamping)
                : CrashResult.noCrash(speed, deltaV, impactEnergy);
    }

    public void clearShip(long shipId) {
        stateByShip.remove(shipId);
    }

    private static double score(double speed, double deltaV, double impactEnergy) {
        double speedRatio = speed / Math.max(0.001D, Config.minCrashSpeed);
        double impulseRatio = Math.max(
                deltaV / Math.max(0.001D, Config.minDeltaV),
                impactEnergy / Math.max(0.001D, Config.minImpactEnergy)
        );
        return (speedRatio * 0.45D) + (impulseRatio * 0.55D);
    }

    private static CrashSeverity classifySeverity(double crashScore) {
        if (crashScore >= Config.catastrophicCrashScore) {
            return CrashSeverity.CATASTROPHIC;
        }
        if (crashScore >= Config.hardCrashScore) {
            return CrashSeverity.HARD;
        }
        return CrashSeverity.SCRAPE;
    }

    private static double damageFor(CrashSeverity severity, double crashScore) {
        double scaled = switch (severity) {
            case SCRAPE -> 4.0D + Math.max(0.0D, crashScore - 1.0D) * 5.0D;
            case HARD -> 12.0D + Math.max(0.0D, crashScore - Config.hardCrashScore) * 12.0D;
            case CATASTROPHIC -> 32.0D + Math.max(0.0D, crashScore - Config.catastrophicCrashScore) * 18.0D;
            case NONE -> 0.0D;
        };
        return Math.min(100.0D, scaled * Config.crashDamageMultiplier);
    }

    private static double dampingFor(CrashSeverity severity) {
        return switch (severity) {
            case SCRAPE -> Config.scrapeExplosionDamping;
            case HARD -> Config.hardExplosionDamping;
            case CATASTROPHIC -> Config.catastrophicExplosionDamping;
            case NONE -> 0.0D;
        };
    }

    private static class ShipMotionState {
        private boolean initialized;
        private long lastTick;
        private long cooldownUntilTick;
        private Vec3 lastVelocity = Vec3.ZERO;
    }

    public enum CrashSeverity {
        NONE,
        SCRAPE,
        HARD,
        CATASTROPHIC
    }

    public static class CrashResult {
        private final boolean crash;
        private final double speed;
        private final double deltaV;
        private final double impactEnergy;
        private final double crashScore;
        private final CrashSeverity severity;
        private final double damage;
        private final double bounceDamping;

        private CrashResult(
                boolean crash,
                double speed,
                double deltaV,
                double impactEnergy,
                double crashScore,
                CrashSeverity severity,
                double damage,
                double bounceDamping
        ) {
            this.crash = crash;
            this.speed = speed;
            this.deltaV = deltaV;
            this.impactEnergy = impactEnergy;
            this.crashScore = crashScore;
            this.severity = severity;
            this.damage = damage;
            this.bounceDamping = bounceDamping;
        }

        public static CrashResult crash(
                double speed,
                double deltaV,
                double impactEnergy,
                double crashScore,
                CrashSeverity severity,
                double damage,
                double bounceDamping
        ) {
            return new CrashResult(true, speed, deltaV, impactEnergy, crashScore, severity, damage, bounceDamping);
        }

        public static CrashResult noCrash(double speed, double deltaV, double impactEnergy) {
            return new CrashResult(false, speed, deltaV, impactEnergy, 0.0D, CrashSeverity.NONE, 0.0D, 0.0D);
        }

        public boolean crash()         { return crash; }
        public double speed()          { return speed; }
        public double deltaV()         { return deltaV; }
        public double impactEnergy()   { return impactEnergy; }
        public double crashScore()     { return crashScore; }
        public CrashSeverity severity(){ return severity; }
        public double damage()         { return damage; }
        public double bounceDamping()  { return bounceDamping; }
    }
}