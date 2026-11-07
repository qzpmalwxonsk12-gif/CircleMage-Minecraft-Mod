package com.example.circlemage.world;

import com.example.circlemage.CircleMageMod;
import com.example.circlemage.entity.ModEntities;
import com.example.circlemage.entity.WanderingMageEntity;
import com.example.circlemage.item.ModItems;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.structure.Structure;

/**
 * The mage towers (v2.3.0). Generation itself is data-driven (see
 * {@code data/circlemage/worldgen/}); this class handles what data packs can't:
 * <ul>
 *     <li><b>One-time initialisation</b> when a player first walks into a tower's bounds —
 *     tracked in {@link WorldProgressState} so it happens exactly once per tower.</li>
 *     <li><b>Chest loot</b> per the tower spec: full towers roll 1-3 special + 4-7 normal items in
 *     both chests; broken towers roll 1-2 special + 2-4 normal in one chest and normal-only in the
 *     other.</li>
 *     <li><b>The guardian</b>: full and special towers get a circle 1-3 Wandering Mage guard that
 *     turns hostile the moment anyone steps onto the grounds.</li>
 * </ul>
 */
public final class MageTowers {

    public static final RegistryKey<Structure> MAGE_TOWER = key("mage_tower");
    public static final RegistryKey<Structure> BROKEN_TOWER_V1 = key("broken_tower_v1");
    public static final RegistryKey<Structure> BROKEN_TOWER_V2 = key("broken_tower_v2");
    public static final RegistryKey<Structure> MAGE_TOWER_SPECIAL = key("mage_tower_special");

    @SuppressWarnings("unchecked")
    private static final RegistryKey<Structure>[] ALL =
            new RegistryKey[]{MAGE_TOWER, MAGE_TOWER_SPECIAL, BROKEN_TOWER_V1, BROKEN_TOWER_V2};

    private static RegistryKey<Structure> key(String name) {
        return RegistryKey.of(RegistryKeys.STRUCTURE, new Identifier(CircleMageMod.MOD_ID, name));
    }

    private MageTowers() {
    }

    /** Called (throttled) from the server tick: is this player inside an uninitialised tower? */
    public static void playerTick(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        BlockPos pos = player.getBlockPos();
        for (RegistryKey<Structure> structureKey : ALL) {
            StructureStart start = world.getStructureAccessor().getStructureContaining(pos, structureKey);
            if (start.hasChildren()) {
                initializeTower(world, start, structureKey);
                return;
            }
        }
    }

    private static void initializeTower(ServerWorld world, StructureStart start, RegistryKey<Structure> structureKey) {
        BlockBox box = start.getBoundingBox();
        long towerKey = box.getCenter().asLong();
        WorldProgressState progress = WorldProgressState.get(world);
        if (progress.isTowerInitialized(towerKey)) {
            return;
        }
        progress.markTowerInitialized(towerKey);

        // Full towers (intact + special) are rich: both chests roll special loot. Broken towers
        // only treat the first chest found as the "good" one.
        boolean rich = structureKey == MAGE_TOWER || structureKey == MAGE_TOWER_SPECIAL;
        Random random = world.random;
        int chestIndex = 0;
        for (BlockPos p : BlockPos.iterate(box.getMinX(), box.getMinY(), box.getMinZ(),
                box.getMaxX(), box.getMaxY(), box.getMaxZ())) {
            if (world.getBlockEntity(p) instanceof LootableContainerBlockEntity chest) {
                boolean special = rich || chestIndex == 0;
                if (rich) {
                    fillChest(chest, random, special ? 1 : 0, special ? 3 : 0, 4, 7);
                } else {
                    fillChest(chest, random, special ? 1 : 0, special ? 2 : 0, 2, 4);
                }
                chestIndex++;
            }
        }

        if (rich) {
            spawnGuardian(world, box, random);
        }
    }

    /** A circle 1-3 mage posted at the tower's edge; hostile to anyone entering the grounds. */
    private static void spawnGuardian(ServerWorld world, BlockBox box, Random random) {
        BlockPos center = box.getCenter();
        int gx = box.getMaxX() + 2;
        int gz = center.getZ();
        int gy = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, gx, gz);

        WanderingMageEntity guard = new WanderingMageEntity(ModEntities.WANDERING_MAGE, world);
        guard.refreshPositionAndAngles(gx + 0.5, gy, gz + 0.5, random.nextFloat() * 360.0f, 0.0f);
        guard.configureForSpawn(1 + random.nextInt(3), false);
        double radius = Math.max(box.getBlockCountX(), box.getBlockCountZ()) / 2.0 + 4.0;
        guard.configureAsGuard(new Vec3d(center.getX() + 0.5, gy, center.getZ() + 0.5), radius);
        world.spawnEntity(guard);
    }

    // --- Loot -------------------------------------------------------------------

    /** Rolls the given counts of special/normal items into random empty slots of the chest. */
    private static void fillChest(LootableContainerBlockEntity chest, Random random,
                                  int specialMin, int specialMax, int normalMin, int normalMax) {
        int specials = specialMax <= 0 ? 0 : specialMin + random.nextInt(specialMax - specialMin + 1);
        int normals = normalMin + random.nextInt(normalMax - normalMin + 1);
        for (int i = 0; i < specials; i++) {
            place(chest, random, rollSpecial(random));
        }
        for (int i = 0; i < normals; i++) {
            place(chest, random, rollNormal(random));
        }
    }

    private static void place(LootableContainerBlockEntity chest, Random random, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        int size = chest.size();
        for (int attempt = 0; attempt < 12; attempt++) {
            int slot = random.nextInt(size);
            if (chest.getStack(slot).isEmpty()) {
                chest.setStack(slot, stack);
                return;
            }
        }
        for (int slot = 0; slot < size; slot++) {
            if (chest.getStack(slot).isEmpty()) {
                chest.setStack(slot, stack);
                return;
            }
        }
    }

    /**
     * The special table (weights sum to 100): glow crystal 5, tomes 20, mana crystal 10, arcane
     * glue 15, heart of the sea 5, amethyst 10, emerald 10, diamond 5, mana liquid 10, blaze rod 5,
     * end rod 5.
     */
    private static ItemStack rollSpecial(Random random) {
        int r = random.nextInt(100);
        if (r < 5) {
            return new ItemStack(ModItems.GLOW_CRYSTAL);
        }
        if (r < 25) {
            Item[] tomes = {ModItems.TOME_FIRE, ModItems.TOME_WATER, ModItems.TOME_EARTH,
                    ModItems.TOME_WIND, ModItems.TOME_MISC};
            return new ItemStack(tomes[random.nextInt(tomes.length)]);
        }
        if (r < 35) {
            return new ItemStack(ModItems.MANA_CRYSTAL);
        }
        if (r < 50) {
            return new ItemStack(ModItems.ARCANE_GLUE);
        }
        if (r < 55) {
            return new ItemStack(Items.HEART_OF_THE_SEA);
        }
        if (r < 65) {
            return new ItemStack(Items.AMETHYST_SHARD, 1 + random.nextInt(3));
        }
        if (r < 75) {
            return new ItemStack(Items.EMERALD, 1 + random.nextInt(2));
        }
        if (r < 80) {
            return new ItemStack(Items.DIAMOND);
        }
        if (r < 90) {
            return new ItemStack(ModItems.MANA_LIQUID);
        }
        if (r < 95) {
            return new ItemStack(Items.BLAZE_ROD);
        }
        return new ItemStack(Items.END_ROD);
    }

    /** The everyday shelf-stock of a mage's tower. */
    private static ItemStack rollNormal(Random random) {
        return switch (random.nextInt(13)) {
            case 0 -> new ItemStack(ModItems.ARCANE_STRING, 2 + random.nextInt(4));
            case 1 -> new ItemStack(ModItems.ARCANE_YARN, 1 + random.nextInt(3));
            case 2 -> new ItemStack(ModItems.MANA_DUST, 1 + random.nextInt(2));
            case 3 -> new ItemStack(ModItems.ARCANE_WOOD, 1 + random.nextInt(4));
            case 4 -> new ItemStack(ModItems.MANA_VIAL);
            case 5 -> new ItemStack(Items.BONE, 1 + random.nextInt(3));
            case 6 -> new ItemStack(Items.GLASS_BOTTLE, 1 + random.nextInt(2));
            case 7 -> new ItemStack(Items.PAPER, 1 + random.nextInt(3));
            case 8 -> new ItemStack(Items.BOOK);
            case 9 -> new ItemStack(Items.TORCH, 2 + random.nextInt(4));
            case 10 -> new ItemStack(Items.STICK, 2 + random.nextInt(3));
            case 11 -> new ItemStack(Items.BREAD, 1 + random.nextInt(2));
            default -> new ItemStack(Items.GLOWSTONE_DUST, 1 + random.nextInt(2));
        };
    }
}
