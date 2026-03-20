package com.vskinetic.ship;

import com.vskinetic.Config;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public class CrashPhysicsEngine {
    private final Map<Long, ShipMotionState> stateByShip = new HashMap<>();

    public CrashResult sample(long shipId, long gameTime, Vec3 velocity, double mass, boolean collisionSignal) {
        ShipMotionState state = stateByShip.computeIfAbsent(shipId, ignored -> new ShipMotionState());

        if (!state.initialized) {
            state.initialized = true;
            state.lastTick = gameTime;
            state.lastVelocity = velocity;
            return CrashResult.noCrash(velocity.length(), 0.0D, 0.0D);
        }

        double speed = velocity.length();
        double deltaV = velocity.subtract(state.lastVelocity).length();
        double previousDownwardSpeed = Math.max(0.0D, -state.lastVelocity.y);
        double clampedMass = Math.max(1.0D, mass);
        double impactEnergy = 0.5D * clampedMass * deltaV * deltaV;
        double crashScore = score(speed, deltaV, impactEnergy);

        boolean collisionGate = !Config.requireCollisionSignal || collisionSignal;
        boolean verticalCollisionImpact = collisionSignal
                && previousDownwardSpeed >= Math.max(1.0D, Config.deckImpactVerticalSpeed * 0.5D);
        boolean enoughSpeed = speed >= Config.minCrashSpeed || verticalCollisionImpact;
        boolean enoughImpulse = deltaV >= Config.minDeltaV || impactEnergy >= Config.minImpactEnergy;
        boolean cooldownReady = gameTime >= state.cooldownUntilTick;

        boolean crash = collisionGate && enoughSpeed && enoughImpulse && cooldownReady;
        CrashSeverity severity = crash ? classifySeverity(crashScore) : CrashSeverity.NONE;
        double damage = crash ? damageFor(severity, crashScore) : 0.0D;
        if (crash) {
            state.cooldownUntilTick = gameTime + Math.max(0, Config.crashCooldownTicks);
        }

        state.lastTick = gameTime;
        state.lastVelocity = velocity;

        return crash
                ? CrashResult.crash(speed, deltaV, impactEnergy, crashScore, severity, damage)
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

        private CrashResult(
                boolean crash,
                double speed,
                double deltaV,
                double impactEnergy,
                double crashScore,
                CrashSeverity severity,
                double damage
        ) {
            this.crash = crash;
            this.speed = speed;
            this.deltaV = deltaV;
            this.impactEnergy = impactEnergy;
            this.crashScore = crashScore;
            this.severity = severity;
            this.damage = damage;
        }

        public static CrashResult crash(
                double speed,
                double deltaV,
                double impactEnergy,
                double crashScore,
                CrashSeverity severity,
                double damage
        ) {
            return new CrashResult(true, speed, deltaV, impactEnergy, crashScore, severity, damage);
        }

        public static CrashResult noCrash(double speed, double deltaV, double impactEnergy) {
            return new CrashResult(false, speed, deltaV, impactEnergy, 0.0D, CrashSeverity.NONE, 0.0D);
        }

        public boolean crash() {
            return crash;
        }

        public double speed() {
            return speed;
        }

        public double deltaV() {
            return deltaV;
        }

        public double impactEnergy() {
            return impactEnergy;
        }

        public double crashScore() {
            return crashScore;
        }

        public CrashSeverity severity() {
            return severity;
        }

        public double damage() {
            return damage;
        }
    }
}
