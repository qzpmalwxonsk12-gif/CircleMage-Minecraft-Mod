package com.example.circlemage.network;

import com.example.circlemage.CircleMageMod;
import com.example.circlemage.mana.ManaData;
import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.world.WorldProgressState;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import com.example.circlemage.network.ServerPackets;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class ModNetworking {

    // Client -> Server
    public static final Identifier CHANNEL_TOGGLE = Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "channel_toggle");
    /** Cast a spell. Payload: int spell id (see {@link com.example.circlemage.spell.Spell}). */
    public static final Identifier CAST = Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "cast");
    /** Begin carving a magic array (no payload). */
    public static final Identifier CARVE = Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "carve");
    /** Select the array type to carve. Payload: int ArrayType ordinal. */
    public static final Identifier SELECT_ARRAY = Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "select_array");
    /** Select the passive chant. Payload: int PassiveChant ordinal. */
    public static final Identifier SELECT_CHANT = Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "select_chant");
    /** Combine two spells (active-chant hold + press another). Payload: int spellA id, int spellB id. */
    public static final Identifier COMBINE = Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "combine");
    /** Toggle a transformed spell back to / forward from its base form. Payload: int baseSpellId, boolean revert. */
    public static final Identifier SELECT_FORM = Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "select_form");
    /** Select the active Law. Payload: int Law ordinal. */
    public static final Identifier SELECT_LAW = Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "select_law");
    /** Activate the selected Law's triggered effect (Spatial Rend / Time Stop / Reversal). No payload. */
    public static final Identifier ACTIVATE_LAW = Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "activate_law");
    /** Open the Storage law's extra inventory. No payload. */
    public static final Identifier OPEN_STORAGE = Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "open_storage");
    /** Dash in the player's current movement-input direction. Payload: float movementForward, float movementSideways. */
    public static final Identifier DASH = Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "dash");
    /** Ask the server to (re)send the Mage Journal snapshot. No payload. */
    public static final Identifier REQUEST_JOURNAL = Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "request_journal");
    /** The spellbook UI was closed on the client (drives the one-time "pages missing" beat). No payload. */
    public static final Identifier BOOK_CLOSED = Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "book_closed");

    public static final Identifier BIND_SPELL = Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "bind_spell");

    // Server -> Client
    public static final Identifier MANA_SYNC = Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "mana_sync");
    /** Tell a client to shake its camera. Payload: float intensity (degrees), int duration ticks. */
    public static final Identifier SHAKE = Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "shake");
    /** Trigger the impact-frame (white flash + brief desaturation). Payload: int duration ticks. */
    public static final Identifier IMPACT = Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "impact");
    /** Open the hardcore Law of Life "give up?" screen. No payload. */
    public static final Identifier GIVE_UP = Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "give_up");
    /** Full Mage Journal snapshot — see {@link #sendJournalSync}. */
    public static final Identifier JOURNAL_SYNC = Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "journal_sync");

    public static void registerPayloads() {
        SpellPayload.register(CHANNEL_TOGGLE, true, 0);
        SpellPayload.register(CAST, true, 9);
        SpellPayload.register(CARVE, true, 0);
        SpellPayload.register(SELECT_ARRAY, true, 4);
        SpellPayload.register(SELECT_CHANT, true, 4);
        SpellPayload.register(COMBINE, true, 12);
        SpellPayload.register(SELECT_FORM, true, 5);
        SpellPayload.register(SELECT_LAW, true, 4);
        SpellPayload.register(ACTIVATE_LAW, true, 0);
        SpellPayload.register(OPEN_STORAGE, true, 0);
        SpellPayload.register(DASH, true, 8);
        SpellPayload.register(REQUEST_JOURNAL, true, 0);
        SpellPayload.register(BOOK_CLOSED, true, 0);
        SpellPayload.register(BIND_SPELL, true, 5);
        SpellPayload.register(SHAKE, false, 8);
        SpellPayload.register(IMPACT, false, 4);
        SpellPayload.register(GIVE_UP, false, 0);
        SpellPayload.register(JOURNAL_SYNC, false, 21);
        SpellPayload.register(MANA_SYNC, false, 60);
    }

    private ModNetworking() {
    }

    /**
     * Sends the player's Mage Journal snapshot: circle, key advancement/unlock flags, realm-buff count,
     * and the encounter/boss progression flags. Read in the same order by {@code ClientJournalState}.
     * If you change the payload, update both ends.
     */
    public static void sendJournalSync(ServerPlayer player) {
        ManaData data = ManaPlayer.get(player);
        WorldProgressState progress = WorldProgressState.get(player.level());

        FriendlyByteBuf buf = FriendlyByteBufs.create();
        buf.writeInt(data.getCircle());
        buf.writeBoolean(hasAdvancement(player, CircleMageMod.ANTI_MAGIC_ADVANCEMENT));
        buf.writeBoolean(hasAdvancement(player, CircleMageMod.COMBINATION_ADVANCEMENT));
        buf.writeBoolean(data.isArrayUnlocked());
        buf.writeBoolean(data.isPassiveChantUnlocked());
        buf.writeBoolean(data.isLawsUnlocked());
        buf.writeInt(data.getUnlockedLaws());
        buf.writeInt(data.unlockedBuffCount());
        buf.writeBoolean(data.isEncounteredWanderingMage());
        buf.writeBoolean(progress.isProdigyDefeated());
        buf.writeBoolean(progress.isMasterDefeated());
        buf.writeBoolean(progress.isLichDefeated());

        ServerPackets.send(player, JOURNAL_SYNC, buf);
    }

    private static boolean hasAdvancement(ServerPlayer player, Identifier id) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(id);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /** Shake the camera of every player within {@code range} of the given point. */
    public static void sendShake(ServerLevel world, double x, double y, double z, double range,
                                 float intensity, int ticks) {
        double r2 = range * range;
        for (ServerPlayer player : world.players()) {
            if (player.distanceToSqr(x, y, z) <= r2) {
                FriendlyByteBuf buf = FriendlyByteBufs.create();
                buf.writeFloat(intensity);
                buf.writeInt(ticks);
                ServerPackets.send(player, SHAKE, buf);
            }
        }
    }

    /** Shake nearby cameras with intensity that falls off with distance from the point. */
    public static void sendShakeFalloff(ServerLevel world, double x, double y, double z, double range,
                                        float maxIntensity, int ticks) {
        double r2 = range * range;
        for (ServerPlayer player : world.players()) {
            double d2 = player.distanceToSqr(x, y, z);
            if (d2 > r2) {
                continue;
            }
            float falloff = (float) (1.0 - Math.sqrt(d2) / range);
            float intensity = maxIntensity * falloff * falloff;
            if (intensity <= 0.02f) {
                continue;
            }
            FriendlyByteBuf buf = FriendlyByteBufs.create();
            buf.writeFloat(intensity);
            buf.writeInt(ticks);
            ServerPackets.send(player, SHAKE, buf);
        }
    }

    /** Open the hardcore "give up?" screen on the given player's client. */
    public static void sendGiveUp(ServerPlayer player) {
        ServerPackets.send(player, GIVE_UP, FriendlyByteBufs.create());
    }

    /** Trigger the impact-frame on every player within {@code range}. */
    public static void sendImpact(ServerLevel world, double x, double y, double z, double range, int ticks) {
        double r2 = range * range;
        for (ServerPlayer player : world.players()) {
            if (player.distanceToSqr(x, y, z) <= r2) {
                FriendlyByteBuf buf = FriendlyByteBufs.create();
                buf.writeInt(ticks);
                ServerPackets.send(player, IMPACT, buf);
            }
        }
    }

    /** Push the player's current mana state down to their client for HUD rendering. */
    public static void syncToClient(ServerPlayer player) {
        ManaData data = ManaPlayer.get(player);
        long threshold = com.example.circlemage.mana.Circles.nextThreshold(data.getCircle());

        FriendlyByteBuf buf = FriendlyByteBufs.create();
        buf.writeDouble(data.getCurrentMana());
        buf.writeInt(data.getCircle());
        buf.writeLong(threshold);
        buf.writeBoolean(data.isChanneling());
        buf.writeInt(data.unlockedBuffCount());
        buf.writeBoolean(data.isArrayUnlocked());
        buf.writeInt(data.getSelectedArray());
        buf.writeBoolean(data.isPassiveChantUnlocked());
        buf.writeInt(data.getSelectedChant());
        buf.writeBoolean(data.isRevertSpear());
        buf.writeBoolean(data.isRevertDarkOrb());
        buf.writeBoolean(data.isLawsUnlocked());
        buf.writeInt(data.getSelectedLaw());
        buf.writeInt(data.getUnlockedLaws());
        buf.writeInt(data.getUnlockedSpells());
        buf.writeInt(data.getIntroStage());
        buf.writeInt(data.getBoundSpells());
        buf.writeBoolean(data.hasAntiMagicEpiphany());
        buf.writeBoolean(data.isMagicLimitBroken());

        ServerPackets.send(player, MANA_SYNC, buf);
    }
}
