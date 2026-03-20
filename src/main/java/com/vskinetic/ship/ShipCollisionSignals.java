package com.vskinetic.ship;

import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ShipCollisionSignals {
    private static final long TERRAIN_SENTINEL_A = -1L;
    private static final long TERRAIN_SENTINEL_B = Long.MIN_VALUE;

    private static final ConcurrentLinkedQueue<PendingCollision> PENDING = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private ShipCollisionSignals() {
    }

    public static void registerVsCollisionEvents() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }

        Object vsCore = getVsCore();
        if (vsCore == null) {
            return;
        }

        registerEvent(vsCore, "getCollisionStartEvent");
        registerEvent(vsCore, "getCollisionPersistEvent");
    }

    public static Set<Long> drainSignalsForDimension(String dimensionId) {
        Set<Long> signaledShips = new HashSet<>();
        List<PendingCollision> deferred = new ArrayList<>();

        PendingCollision collision;
        while ((collision = PENDING.poll()) != null) {
            if (collision.dimensionId != null && !collision.dimensionId.equals(dimensionId)) {
                deferred.add(collision);
                continue;
            }

            signaledShips.add(collision.shipIdA);
            if (isRealShipId(collision.shipIdB)) {
                signaledShips.add(collision.shipIdB);
            }
        }

        for (PendingCollision pending : deferred) {
            PENDING.offer(pending);
        }

        return signaledShips;
    }

    private static boolean isRealShipId(long shipId) {
        return shipId > 0L && shipId != TERRAIN_SENTINEL_A && shipId != TERRAIN_SENTINEL_B;
    }

    private static Object getVsCore() {
        try {
            Field field = ValkyrienSkiesMod.class.getField("vsCore");
            return field.get(null);
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Method method = ValkyrienSkiesMod.class.getMethod("getVsCore");
            return method.invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static void registerEvent(Object vsCore, String accessorName) {
        try {
            Method accessor = vsCore.getClass().getMethod(accessorName);
            Object singleEvent = accessor.invoke(vsCore);
            if (singleEvent == null) {
                return;
            }

            Method onMethod = findOnMethod(singleEvent.getClass());
            if (onMethod == null) {
                return;
            }

            Class<?> listenerType = onMethod.getParameterTypes()[0];
            if (!listenerType.isInterface()) {
                return;
            }

            Object listener = Proxy.newProxyInstance(
                    listenerType.getClassLoader(),
                    new Class<?>[]{listenerType},
                    new CollisionInvocationHandler()
            );
            onMethod.invoke(singleEvent, listener);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Method findOnMethod(Class<?> eventType) {
        for (Method method : eventType.getMethods()) {
            if (!method.getName().equals("on")) {
                continue;
            }
            if (method.getParameterCount() == 1) {
                return method;
            }
        }
        return null;
    }

    private static void enqueueFromEvent(Object event) {
        if (event == null) {
            return;
        }

        try {
            long shipIdA = ((Number) event.getClass().getMethod("getShipIdA").invoke(event)).longValue();
            long shipIdB = ((Number) event.getClass().getMethod("getShipIdB").invoke(event)).longValue();
            String dimensionId = String.valueOf(event.getClass().getMethod("getDimensionId").invoke(event));
            if (shipIdA <= 0L) {
                return;
            }
            PENDING.offer(new PendingCollision(dimensionId, shipIdA, shipIdB));
        } catch (ReflectiveOperationException | ClassCastException ignored) {
        }
    }

    private record PendingCollision(String dimensionId, long shipIdA, long shipIdB) {
    }

    private static final class CollisionInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getName().equals("invoke") && args != null && args.length >= 1) {
                enqueueFromEvent(args[0]);
            }
            return kotlinUnitInstance();
        }

        private Object kotlinUnitInstance() {
            try {
                Class<?> unitClass = Class.forName("kotlin.Unit");
                return unitClass.getField("INSTANCE").get(null);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
    }
}
