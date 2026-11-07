package com.example.circlemage.spell;

import com.example.circlemage.CircleMageMod;
import com.example.circlemage.mana.ManaData;
import com.example.circlemage.network.ModNetworking;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Central logic for unlocking Laws and tracking their unlock conditions.
 *
 * <p>Discovering Laws (the 0.01%/s roll or the command) unlocks Love of the World and Law of Creation.
 * Other Laws unlock on conditions: Space laws on comprehending the void (taking void damage), Life on
 * 5 deaths, Causality on being struck by every hostile mob. Gravity/Black Hole/Time are command-only.
 */
public final class LawUnlocks {

    /** Hostile mobs that must each strike the mage to unlock the Law of Causality. */
    public static final Set<String> REQUIRED_HOSTILES = Set.of(
            "minecraft:zombie", "minecraft:husk", "minecraft:drowned",
            "minecraft:skeleton", "minecraft:stray", "minecraft:wither_skeleton",
            "minecraft:creeper", "minecraft:spider", "minecraft:cave_spider",
            "minecraft:enderman", "minecraft:witch", "minecraft:slime",
            "minecraft:silverfish", "minecraft:endermite", "minecraft:phantom",
            "minecraft:pillager", "minecraft:vindicator", "minecraft:evoker", "minecraft:vex",
            "minecraft:ravager", "minecraft:blaze", "minecraft:ghast", "minecraft:magma_cube",
            "minecraft:hoglin", "minecraft:zoglin", "minecraft:piglin_brute", "minecraft:guardian");

    private LawUnlocks() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, path);
    }

    /** The advancement granted when a given Law is unlocked (null = no advancement). */
    public static Identifier advancementFor(Law law) {
        return switch (law) {
            case LIFE -> id("law_life");
            case SPATIAL_REND -> id("law_spatial_rend");
            case SPACE_MANIPULATION -> id("law_space_manipulation");
            case STORAGE -> id("law_storage");
            case GRAVITY_AMP -> id("law_gravity_amp");
            case BLACK_HOLE -> id("law_black_hole");
            case TIME_STOP -> id("law_time_stop");
            case TIME_REVERSAL -> id("law_time_reversal");
            case CREATION -> id("law_creation");
            case CAUSALITY -> id("law_causality");
            case LOVE_OF_WORLD -> id("law_love_of_world");
            default -> null;
        };
    }

    private static void grant(ServerPlayer player, Identifier advId) {
        if (advId == null || player.level().getServer() == null) {
            return;
        }
        AdvancementHolder adv = player.level().getServer().getAdvancements().get(advId);
        if (adv == null || player.getAdvancements().getOrStartProgress(adv).isDone()) {
            return;
        }
        for (String criterion : adv.value().criteria().keySet()) {
            player.getAdvancements().award(adv, criterion);
        }
    }

    /** Discovering the Laws: gates the 9th circle and unlocks Love of the World + Law of Creation. */
    public static void unlockBase(ServerPlayer player, ManaData data) {
        boolean wasNew = !data.isLawsUnlocked();
        data.setLawsUnlocked(true);
        grant(player, CircleMageMod.LAWS_ADVANCEMENT);
        if (wasNew) {
            com.example.circlemage.util.Messages.send(player, Component.literal("The Laws have revealed themselves to you! "
                            + "(Love of the World and Creation are now yours; usable at the 9th circle)")
                    .withStyle(ChatFormatting.GOLD), false);
        }
        reevaluate(player, data);
        ModNetworking.syncToClient(player);
    }

    /** Unlocks a single Law (idempotent), granting its advancement and announcing it. */
    public static void unlockLaw(ServerPlayer player, ManaData data, Law law) {
        if (law == Law.NONE || data.isLawUnlocked(law.ordinal())) {
            return;
        }
        data.unlockLawBit(law.ordinal());
        grant(player, advancementFor(law));
        com.example.circlemage.util.Messages.send(player, Component.literal("Law unlocked: " + law.displayName).withStyle(ChatFormatting.GOLD), false);
        ModNetworking.syncToClient(player);
    }

    /** Command path: unlock the base + every Law. */
    public static void unlockAll(ServerPlayer player, ManaData data) {
        data.setLawsUnlocked(true);
        grant(player, CircleMageMod.LAWS_ADVANCEMENT);
        for (Law law : Law.values()) {
            if (law != Law.NONE) {
                unlockLaw(player, data, law);
            }
        }
        ModNetworking.syncToClient(player);
    }

    /** Re-checks the condition-based Laws and unlocks any newly eligible. */
    public static void reevaluate(ServerPlayer player, ManaData data) {
        if (!data.isLawsUnlocked()) {
            return;
        }
        unlockLaw(player, data, Law.LOVE_OF_WORLD);
        unlockLaw(player, data, Law.CREATION);
        if (data.isVoidComprehended()) {
            unlockLaw(player, data, Law.SPATIAL_REND);
            unlockLaw(player, data, Law.SPACE_MANIPULATION);
            unlockLaw(player, data, Law.STORAGE);
        }
        if (data.getDeathCount() >= 5) {
            unlockLaw(player, data, Law.LIFE);
        }
        if (data.getHostilesHit().containsAll(REQUIRED_HOSTILES)) {
            unlockLaw(player, data, Law.CAUSALITY);
        }
        // Gravity Amplification, Black Hole, and the Time laws are command-only.
    }

    public static void recordVoidComprehension(ServerPlayer player, ManaData data) {
        if (!data.isVoidComprehended()) {
            data.setVoidComprehended(true);
            reevaluate(player, data);
        }
    }

    public static void recordHostileHit(ServerPlayer player, ManaData data, Entity attacker) {
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()).toString();
        if (REQUIRED_HOSTILES.contains(typeId) && data.getHostilesHit().add(typeId)) {
            reevaluate(player, data);
        }
    }

    public static void recordDeath(ServerPlayer player, ManaData data) {
        data.incrementDeathCount();
        reevaluate(player, data);
    }
}
