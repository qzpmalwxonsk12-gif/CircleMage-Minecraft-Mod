package com.example.circlemage.spell;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.ai.attributes.*;
public final class RevampStatusEffects {
    private static class Effect extends MobEffect {
        Effect(MobEffectCategory category, int color) { super(category, color); }
    }
    public static final Holder<MobEffect> FROSTED = register("frosted", new Effect(MobEffectCategory.HARMFUL, 0x9FDBEF)
        .addAttributeModifier(Attributes.MOVEMENT_SPEED, Identifier.fromNamespaceAndPath("circlemage", "frosted"), -.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    public static final Holder<MobEffect> ROOTED = register("rooted", new Effect(MobEffectCategory.HARMFUL, 0x4E8153)
        .addAttributeModifier(Attributes.MOVEMENT_SPEED, Identifier.fromNamespaceAndPath("circlemage", "rooted"), -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    public static final Holder<MobEffect> SOOTHING = register("soothing_water", new Effect(MobEffectCategory.BENEFICIAL, 0x58C7ED));
    private static Holder<MobEffect> register(String name, MobEffect effect) {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Identifier.fromNamespaceAndPath("circlemage", name), effect);
    }
    public static void register() {}
    private RevampStatusEffects() {}
}
