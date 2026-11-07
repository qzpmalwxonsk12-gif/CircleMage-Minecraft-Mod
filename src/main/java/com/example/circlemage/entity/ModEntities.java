package com.example.circlemage.entity;

import com.example.circlemage.CircleMageMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {

    public static final EntityType<MagicMissileEntity> MAGIC_MISSILE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(CircleMageMod.MOD_ID, "magic_missile"),
            FabricEntityTypeBuilder.<MagicMissileEntity>create(SpawnGroup.MISC, MagicMissileEntity::new)
                    .dimensions(EntityDimensions.fixed(0.3f, 0.3f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build()
    );

    public static final EntityType<MagicBarrierEntity> MAGIC_BARRIER = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(CircleMageMod.MOD_ID, "magic_barrier"),
            FabricEntityTypeBuilder.<MagicBarrierEntity>create(SpawnGroup.MISC, MagicBarrierEntity::new)
                    .dimensions(EntityDimensions.fixed(5.0f, 5.0f))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(2)
                    .build()
    );

    public static final EntityType<DarkOrbEntity> DARK_ORB = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(CircleMageMod.MOD_ID, "dark_orb"),
            FabricEntityTypeBuilder.<DarkOrbEntity>create(SpawnGroup.MISC, DarkOrbEntity::new)
                    .dimensions(EntityDimensions.fixed(1.2f, 1.2f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build()
    );

    public static final EntityType<SpatialRendEntity> SPATIAL_REND = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(CircleMageMod.MOD_ID, "spatial_rend"),
            FabricEntityTypeBuilder.<SpatialRendEntity>create(SpawnGroup.MISC, SpatialRendEntity::new)
                    .dimensions(EntityDimensions.fixed(2.0f, 10.0f))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(1)
                    .build()
    );

    public static final EntityType<WanderingMageEntity> WANDERING_MAGE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(CircleMageMod.MOD_ID, "wandering_mage"),
            FabricEntityTypeBuilder.<WanderingMageEntity>create(SpawnGroup.MONSTER, WanderingMageEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .trackRangeBlocks(80)
                    .trackedUpdateRate(3)
                    .build()
    );

    public static final EntityType<WindBladeEntity> WIND_BLADE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(CircleMageMod.MOD_ID, "wind_blade"),
            FabricEntityTypeBuilder.<WindBladeEntity>create(SpawnGroup.MISC, WindBladeEntity::new)
                    .dimensions(EntityDimensions.fixed(0.4f, 0.4f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build()
    );

    public static final EntityType<TutorialBookEntity> TUTORIAL_BOOK = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(CircleMageMod.MOD_ID, "tutorial_book"),
            FabricEntityTypeBuilder.<TutorialBookEntity>create(SpawnGroup.MISC, TutorialBookEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
                    .trackRangeBlocks(48)
                    .trackedUpdateRate(2)
                    .build()
    );

    public static final EntityType<MasterEntity> MASTER = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(CircleMageMod.MOD_ID, "master"),
            FabricEntityTypeBuilder.<MasterEntity>create(SpawnGroup.MONSTER, MasterEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 2.0f))
                    .trackRangeBlocks(160)
                    .trackedUpdateRate(2)
                    .build()
    );

    private ModEntities() {
    }

    public static void register() {
        // Class-load triggers registration above.
    }
}
