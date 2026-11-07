package com.example.circlemage.entity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.*;
public final class ModEntities {
    public static final EntityType<MagicMissileEntity> MAGIC_MISSILE = register("magic_missile", MagicMissileEntity::new, MobCategory.MISC, 0.3f, 0.3f, 64, 2);
    public static final EntityType<MagicBarrierEntity> MAGIC_BARRIER = register("magic_barrier", MagicBarrierEntity::new, MobCategory.MISC, 5.0f, 5.0f, 96, 2);
    public static final EntityType<DarkOrbEntity> DARK_ORB = register("dark_orb", DarkOrbEntity::new, MobCategory.MISC, 1.2f, 1.2f, 64, 2);
    public static final EntityType<SpatialRendEntity> SPATIAL_REND = register("spatial_rend", SpatialRendEntity::new, MobCategory.MISC, 2.0f, 10.0f, 128, 1);
    public static final EntityType<WanderingMageEntity> WANDERING_MAGE = register("wandering_mage", WanderingMageEntity::new, MobCategory.MONSTER, 0.6f, 1.95f, 80, 3);
    public static final EntityType<WindBladeEntity> WIND_BLADE = register("wind_blade", WindBladeEntity::new, MobCategory.MISC, 0.4f, 0.4f, 64, 2);
    public static final EntityType<TutorialBookEntity> TUTORIAL_BOOK = register("tutorial_book", TutorialBookEntity::new, MobCategory.MISC, 0.5f, 0.5f, 48, 2);
    public static final EntityType<MasterEntity> MASTER = register("master", MasterEntity::new, MobCategory.MONSTER, 0.6f, 2.0f, 160, 2);
    public static final EntityType<IceSpikeEntity> ICE_SPIKE = register("ice_spike", IceSpikeEntity::new, MobCategory.MISC, .22f, .22f, 80, 2);
    public static final EntityType<ArcaneEffectEntity> ARCANE_EFFECT = register("arcane_effect", ArcaneEffectEntity::new, MobCategory.MISC, .5f, .5f, 128, 3);
    private static <T extends Entity> EntityType<T> register(String name, EntityType.EntityFactory<T> factory, MobCategory category, float width, float height, int range, int interval) {
        Identifier id = Identifier.fromNamespaceAndPath("circlemage", name);
        var type = EntityType.Builder.of(factory, category).sized(width, height)
            .clientTrackingRange((range + 15) / 16).updateInterval(interval)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, id));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id, type);
    }
    public static void register() {}
    private ModEntities() {}
}
