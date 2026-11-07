package com.example.circlemage.client;

import com.example.circlemage.network.ModNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * v2 casting controls. The primary interface is the <b>spell selector</b>: hold the wheel modifier
 * (Left Alt) and scroll — or use the next/previous keybinds — to highlight a spell, then press the
 * single <b>Cast</b> key (R) to fire it.
 *
 * <p>Charging no longer needs right-click: from the 5th circle, <i>holding</i> the Cast key (or a
 * quick-cast key) charges any chantable spell — release under half a second for a normal cast,
 * longer for an enhanced one, three full seconds for a fully-charged one. Combining is either
 * pressing a second spell's quick key mid-charge, or scrolling the selector to the second spell
 * mid-charge and releasing.</p>
 *
 * <p>Every v1 per-spell quick key still exists (Magic Missile's is unbound by default since R now
 * casts the selected spell, which starts as the bolt).</p>
 */
public final class ModKeybinds {

    public static final String CATEGORY = "category.circlemage";

    // Spell ids — must match com.example.circlemage.spell.Spell.
    private static final int SPELL_MAGIC_MISSILE = 0;
    private static final int SPELL_FIREBALL = 1;
    private static final int SPELL_AREA_SLOW = 2;
    private static final int SPELL_CLEAR_MAGIC = 3;
    private static final int SPELL_WALL_OF_WIND = 4;
    private static final int SPELL_THUNDER = 5;
    private static final int SPELL_BARRIER = 6;
    private static final int SPELL_DASH = 9;
    private static final int SPELL_WIND_BLADE = 10;

    /** Anti-magic must be charged by holding its key for 0.75 seconds (15 client ticks). */
    private static final int CLEAR_HOLD_TICKS = 15;
    /** Carving takes 2 seconds (40 client ticks). */
    private static final int CARVE_TICKS = 40;
    private static final double CARVE_MOVE_THRESHOLD_SQ = 0.04;
    /** Hold-to-charge: 3 seconds to full. Must match CircleMageMod.PRIME_MAX_TICKS. */
    private static final int PRIME_MAX_TICKS = 60;
    /** Releases shorter than this (0.5s) fire as a plain, un-enhanced cast. */
    private static final int ENHANCE_MIN_TICKS = 10;

    public static KeyBinding channelKey;
    public static KeyBinding castKey;
    public static KeyBinding nextSpellKey;
    public static KeyBinding prevSpellKey;
    public static KeyBinding selectModifierKey;
    public static KeyBinding missileKey;
    public static KeyBinding fireballKey;
    public static KeyBinding areaSlowKey;
    public static KeyBinding clearMagicKey;
    public static KeyBinding wallOfWindKey;
    public static KeyBinding thunderKey;
    public static KeyBinding barrierKey;
    public static KeyBinding dashKey;
    public static KeyBinding windBladeKey;
    public static KeyBinding carveKey;
    public static KeyBinding toggleFormKey;
    public static KeyBinding activateLawKey;
    public static KeyBinding magicMenuKey;
    public static KeyBinding lawsMenuKey;
    public static KeyBinding journalKey;

    private static KeyBinding[] chantKeys;
    private static int[] chantIds;

    private static int clearHeldTicks = 0;
    private static boolean clearFired = false;

    private static boolean carving = false;
    private static int carveProgress = 0;
    private static Vec3d carveStart = Vec3d.ZERO;

    // Hold-to-charge state. primingViaCast means the Cast key (not a quick key) is doing the holding,
    // which enables the scroll-to-combine release behaviour.
    private static int primingSpellId = -1;
    private static KeyBinding primingKey = null;
    private static boolean primingViaCast = false;
    private static int primeTicks = 0;

    private ModKeybinds() {
    }

    public static void register() {
        channelKey = registerKey("key.circlemage.channel", GLFW.GLFW_KEY_M);
        castKey = registerKey("key.circlemage.cast", GLFW.GLFW_KEY_R);
        nextSpellKey = registerKey("key.circlemage.next_spell", GLFW.GLFW_KEY_UNKNOWN);
        prevSpellKey = registerKey("key.circlemage.prev_spell", GLFW.GLFW_KEY_UNKNOWN);
        selectModifierKey = registerKey("key.circlemage.select_modifier", GLFW.GLFW_KEY_LEFT_ALT);
        // Quick-cast keys. Magic Missile's is unbound by default: R (the Cast key) covers it now.
        missileKey = registerKey("key.circlemage.missile", GLFW.GLFW_KEY_UNKNOWN);
        fireballKey = registerKey("key.circlemage.fireball", GLFW.GLFW_KEY_T);
        areaSlowKey = registerKey("key.circlemage.area_slow", GLFW.GLFW_KEY_V);
        clearMagicKey = registerKey("key.circlemage.clear_magic", GLFW.GLFW_KEY_G);
        wallOfWindKey = registerKey("key.circlemage.wall_of_wind", GLFW.GLFW_KEY_Y);
        thunderKey = registerKey("key.circlemage.thunder", GLFW.GLFW_KEY_H);
        barrierKey = registerKey("key.circlemage.barrier", GLFW.GLFW_KEY_Z);
        dashKey = registerKey("key.circlemage.dash", GLFW.GLFW_KEY_UNKNOWN);
        windBladeKey = registerKey("key.circlemage.wind_blade", GLFW.GLFW_KEY_UNKNOWN);
        carveKey = registerKey("key.circlemage.carve", GLFW.GLFW_KEY_C);
        toggleFormKey = registerKey("key.circlemage.toggle_form", GLFW.GLFW_KEY_X);
        activateLawKey = registerKey("key.circlemage.activate_law", GLFW.GLFW_KEY_B);
        magicMenuKey = registerKey("key.circlemage.magic_menu", GLFW.GLFW_KEY_K);
        lawsMenuKey = registerKey("key.circlemage.laws_menu", GLFW.GLFW_KEY_L);
        journalKey = registerKey("key.circlemage.journal", GLFW.GLFW_KEY_J);

        chantKeys = new KeyBinding[]{missileKey, fireballKey, areaSlowKey, wallOfWindKey, thunderKey};
        chantIds = new int[]{SPELL_MAGIC_MISSILE, SPELL_FIREBALL, SPELL_AREA_SLOW, SPELL_WALL_OF_WIND, SPELL_THUNDER};

        ClientTickEvents.END_CLIENT_TICK.register(ModKeybinds::onClientTick);
    }

    private static KeyBinding registerKey(String translationKey, int keyCode) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                translationKey, InputUtil.Type.KEYSYM, keyCode, CATEGORY));
    }

    // --- Charge state, read by the HUD and the casting-circle renderer ---------

    public static boolean isPriming() {
        return primingSpellId >= 0;
    }

    public static int primingSpellId() {
        return primingSpellId;
    }

    /** Charge progress in [0, 1]. */
    public static float primeProgress() {
        return Math.min(1.0f, primeTicks / (float) PRIME_MAX_TICKS);
    }

    private static void onClientTick(MinecraftClient client) {
        if (client.player == null) {
            return;
        }

        while (channelKey.wasPressed()) {
            ClientPlayNetworking.send(ModNetworking.CHANNEL_TOGGLE, PacketByteBufs.empty());
        }

        // Selector cycling by keybind (Alt+scroll is handled in MouseScrollMixin).
        while (nextSpellKey.wasPressed()) {
            SpellSelectorState.cycle(1);
        }
        while (prevSpellKey.wasPressed()) {
            SpellSelectorState.cycle(-1);
        }

        // Toggle Magic Missile <-> Spear of Light (server validates the 5th-circle requirement).
        while (toggleFormKey.wasPressed()) {
            sendSelectForm(SPELL_MAGIC_MISSILE, !ClientManaState.revertSpear);
        }

        // Activate the selected Law's triggered effect (Spatial Rend / Time Stop / Reversal).
        while (activateLawKey.wasPressed()) {
            ClientPlayNetworking.send(ModNetworking.ACTIVATE_LAW, PacketByteBufs.empty());
        }

        // Open the consolidated menus.
        while (magicMenuKey.wasPressed()) {
            client.setScreen(new MagicScreen());
        }
        while (lawsMenuKey.wasPressed()) {
            if (ClientManaState.lawsUnlocked) {
                client.setScreen(new LawSelectionScreen());
            } else {
                client.inGameHud.setOverlayMessage(
                        Text.literal("The Laws are not yet unlocked.").formatted(Formatting.RED), false);
            }
        }

        // Open the Spellbook (request a fresh snapshot first so it reflects current progress).
        while (journalKey.wasPressed()) {
            ClientPlayNetworking.send(ModNetworking.REQUEST_JOURNAL, PacketByteBufs.empty());
            client.setScreen(new SpellbookScreen());
        }

        handleCastKey(client);
        handleQuickCastKeys(client);
        tickPriming(client);

        while (barrierKey.wasPressed()) {
            sendCast(SPELL_BARRIER, false, 0);
        }

        // Wind Blade: a plain tap fires a 5-blade volley (never primed/combined).
        while (windBladeKey.wasPressed()) {
            sendCast(SPELL_WIND_BLADE, false, 0);
        }

        while (dashKey.wasPressed()) {
            sendDash(client);
        }

        // Carve: tap C to begin a 2-second carve that the server validates.
        while (carveKey.wasPressed()) {
            if (!carving) {
                carving = true;
                carveProgress = 0;
                carveStart = client.player.getPos();
                sendCarve();
            }
        }

        // Drain the anti-magic press queue; it uses hold-to-cast below.
        while (clearMagicKey.wasPressed()) {
            // no-op
        }

        handleClearMagicHold(client);
        handleCarveProgress(client);
    }

    /**
     * The Cast key fires whatever the selector highlights. Chantable spells hold-to-charge from the
     * 5th circle; Barrier/Dash/Wind Blade fire on press; Anti Magic uses its hold (see
     * {@link #handleClearMagicHold}).
     */
    private static void handleCastKey(MinecraftClient client) {
        while (castKey.wasPressed()) {
            int id = SpellSelectorState.selectedSpellId();
            if (id < 0) {
                client.inGameHud.setOverlayMessage(
                        Text.literal("You have not awakened any magic yet — reach the 1st circle.")
                                .formatted(Formatting.RED), false);
                continue;
            }

            // Pressing Cast mid-charge with a different chantable spell highlighted combines them.
            if (primingSpellId >= 0) {
                if (id != primingSpellId && SpellSelectorState.isChantable(id)) {
                    fireCombine(client, primingSpellId, primeTicks, id);
                    resetPriming();
                }
                continue;
            }

            switch (id) {
                case SPELL_BARRIER -> sendCast(SPELL_BARRIER, false, 0);
                case SPELL_WIND_BLADE -> sendCast(SPELL_WIND_BLADE, false, 0);
                case SPELL_DASH -> sendDash(client);
                case SPELL_CLEAR_MAGIC -> {
                    // handled by the hold logic in handleClearMagicHold
                }
                default -> {
                    if (SpellSelectorState.isChantable(id) && ClientManaState.circle >= 5) {
                        beginPriming(id, castKey, true);
                    } else {
                        sendCast(id, false, 0);
                    }
                }
            }
        }
    }

    /**
     * Quick-cast keys: press fires (or begins charging) that spell directly, regardless of the
     * selector. Pressing one mid-charge of a <i>different</i> spell combines the two.
     */
    private static void handleQuickCastKeys(MinecraftClient client) {
        for (int i = 0; i < chantKeys.length; i++) {
            int id = chantIds[i];
            // Guard against a quick key sharing a physical key with the Cast key (e.g. a v1
            // options.txt that still has Magic Missile on R): the Cast key already handled it.
            if (chantKeys[i].equals(castKey)) {
                while (chantKeys[i].wasPressed()) {
                    // drain
                }
                continue;
            }
            while (chantKeys[i].wasPressed()) {
                if (primingSpellId < 0) {
                    if (SpellSelectorState.isChantable(id) && ClientManaState.circle >= 5) {
                        beginPriming(id, chantKeys[i], false);
                    } else {
                        sendCast(id, false, 0);
                    }
                } else if (id != primingSpellId) {
                    fireCombine(client, primingSpellId, primeTicks, id);
                    resetPriming();
                }
            }
        }
    }

    private static void beginPriming(int spellId, KeyBinding key, boolean viaCast) {
        primingSpellId = spellId;
        primingKey = key;
        primingViaCast = viaCast;
        primeTicks = 0;
    }

    /** Advances the charge while the priming key is held; fires (or combines) on release. */
    private static void tickPriming(MinecraftClient client) {
        if (primingSpellId < 0) {
            return;
        }
        if (primingKey != null && primingKey.isPressed()) {
            if (primeTicks < PRIME_MAX_TICKS) {
                primeTicks++;
                if (primeTicks == PRIME_MAX_TICKS) {
                    client.inGameHud.setOverlayMessage(
                            Text.literal(SpellSelectorState.displayName(primingSpellId)
                                    + " fully charged — release, or pick another spell to combine")
                                    .formatted(Formatting.GOLD), false);
                }
            }
            return;
        }

        // Released. Scrolled to a different chantable spell while charging via Cast -> combine.
        int selected = SpellSelectorState.selectedSpellId();
        if (primingViaCast && selected != primingSpellId && SpellSelectorState.isChantable(selected)) {
            fireCombine(client, primingSpellId, primeTicks, selected);
        } else if (primeTicks >= ENHANCE_MIN_TICKS) {
            sendCast(primingSpellId, true, primeTicks);   // enhanced (active-chant) cast
        } else {
            sendCast(primingSpellId, false, 0);           // quick tap -> plain cast
        }
        resetPriming();
    }

    private static void fireCombine(MinecraftClient client, int primedId, int charge, int secondId) {
        sendCombine(primedId, charge, secondId);
        client.inGameHud.setOverlayMessage(
                Text.literal("Combining " + SpellSelectorState.displayName(primedId)
                        + " + " + SpellSelectorState.displayName(secondId) + "...")
                        .formatted(Formatting.DARK_PURPLE), false);
    }

    private static void resetPriming() {
        primingSpellId = -1;
        primingKey = null;
        primingViaCast = false;
        primeTicks = 0;
    }

    /** Anti Magic hold: works on its quick key, or on the Cast key while Anti Magic is selected. */
    private static void handleClearMagicHold(MinecraftClient client) {
        boolean held = clearMagicKey.isPressed()
                || (castKey.isPressed() && primingSpellId < 0
                && SpellSelectorState.selectedSpellId() == SPELL_CLEAR_MAGIC);
        if (held) {
            if (clearFired) {
                return;
            }
            clearHeldTicks++;
            if (clearHeldTicks >= CLEAR_HOLD_TICKS) {
                sendCast(SPELL_CLEAR_MAGIC, false, 0);
                clearFired = true;
                client.inGameHud.setOverlayMessage(Text.literal("Anti Magic released!").formatted(Formatting.AQUA), false);
            } else {
                int percent = (clearHeldTicks * 100) / CLEAR_HOLD_TICKS;
                client.inGameHud.setOverlayMessage(
                        Text.literal("Charging Anti Magic... " + percent + "%").formatted(Formatting.AQUA), false);
            }
        } else {
            clearHeldTicks = 0;
            clearFired = false;
        }
    }

    private static void handleCarveProgress(MinecraftClient client) {
        if (!carving) {
            return;
        }
        // Mirror the server's movement-cancel rule for visual feedback.
        if (client.player.getPos().squaredDistanceTo(carveStart) > CARVE_MOVE_THRESHOLD_SQ) {
            carving = false;
            client.inGameHud.setOverlayMessage(Text.literal("Carving interrupted!").formatted(Formatting.RED), false);
            return;
        }
        carveProgress++;
        if (carveProgress >= CARVE_TICKS) {
            carving = false;
            client.inGameHud.setOverlayMessage(Text.literal("Array carved!").formatted(Formatting.LIGHT_PURPLE), false);
        } else {
            int percent = (carveProgress * 100) / CARVE_TICKS;
            client.inGameHud.setOverlayMessage(
                    Text.literal("Carving array... " + percent + "%  (hold still)").formatted(Formatting.AQUA), false);
        }
    }

    /** Dash toward the held movement input (A = left, D = right, W = forward, …). */
    private static void sendDash(MinecraftClient client) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(client.player.input.movementForward);
        buf.writeFloat(client.player.input.movementSideways);
        ClientPlayNetworking.send(ModNetworking.DASH, buf);
        SpellSelectorState.markCast(SPELL_DASH);
    }

    private static void sendCast(int spellId, boolean primed, int charge) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(spellId);
        buf.writeBoolean(primed);
        buf.writeInt(charge);
        ClientPlayNetworking.send(ModNetworking.CAST, buf);
        SpellSelectorState.markCast(spellId);
    }

    private static void sendCombine(int spellA, int chargeA, int spellB) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(spellA);
        buf.writeInt(chargeA);
        buf.writeInt(spellB);
        ClientPlayNetworking.send(ModNetworking.COMBINE, buf);
    }

    private static void sendSelectForm(int baseSpellId, boolean revert) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(baseSpellId);
        buf.writeBoolean(revert);
        ClientPlayNetworking.send(ModNetworking.SELECT_FORM, buf);
    }

    private static void sendCarve() {
        ClientPlayNetworking.send(ModNetworking.CARVE, PacketByteBufs.empty());
    }
}
