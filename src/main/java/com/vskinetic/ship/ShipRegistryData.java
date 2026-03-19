package com.vskinetic.ship;

import com.vskinetic.Config;
import com.vskinetic.KineticMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShipRegistryData extends SavedData {
    private static final String DATA_NAME = KineticMod.MODID + "_ship_registry";
    private static final String KEY_RECORDS = "Records";

    private final Map<Long, ShipBindingRecord> records = new HashMap<>();

    public static ShipRegistryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ShipRegistryData::load, ShipRegistryData::new, DATA_NAME);
    }

    private static ShipRegistryData load(CompoundTag root) {
        ShipRegistryData data = new ShipRegistryData();
        ListTag list = root.getList(KEY_RECORDS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ShipBindingRecord record = ShipBindingRecord.fromTag(entry);
            data.records.put(record.shipId(), record);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        for (ShipBindingRecord record : records.values()) {
            list.add(record.toTag());
        }
        root.put(KEY_RECORDS, list);
        return root;
    }

    public ShipBindingRecord getOrCreate(long shipId) {
        return records.computeIfAbsent(shipId, id -> ShipBindingRecord.createDefault(shipId));
    }

    public ShipBindingRecord pairShip(long shipId, UUID playerId) {
        ShipBindingRecord record = getOrCreate(shipId);
        record.setOwner(playerId);
        record.setCreatorIfMissing(playerId);
        setDirty();
        return record;
    }

    public ShipBindingRecord setCreator(long shipId, UUID playerId) {
        ShipBindingRecord record = getOrCreate(shipId);
        record.setCreator(playerId);
        setDirty();
        return record;
    }

    public ShipBindingRecord setCreatorIfMissing(long shipId, UUID playerId) {
        ShipBindingRecord record = getOrCreate(shipId);
        if (record.setCreatorIfMissing(playerId)) {
            setDirty();
        }
        return record;
    }

    public ShipBindingRecord renameShip(long shipId, String displayName) {
        ShipBindingRecord record = getOrCreate(shipId);
        record.rename(displayName);
        setDirty();
        return record;
    }

    public ShipBindingRecord markCrashed(long shipId) {
        ShipBindingRecord record = getOrCreate(shipId);
        record.setCrashed(true);
        setDirty();
        return record;
    }

    public ShipBindingRecord applyCrash(long shipId, long gameTime, CrashPhysicsEngine.CrashResult result) {
        ShipBindingRecord record = getOrCreate(shipId);
        record.applyCrash(result, gameTime);
        setDirty();
        return record;
    }

    public ShipBindingRecord recoverShip(long shipId) {
        ShipBindingRecord record = getOrCreate(shipId);
        record.recover(Config.recoveryIntegrityFloor);
        setDirty();
        return record;
    }

    public ShipBindingRecord getShip(long shipId) {
        return records.get(shipId);
    }

    public Collection<ShipBindingRecord> allShips() {
        return records.values();
    }
}
