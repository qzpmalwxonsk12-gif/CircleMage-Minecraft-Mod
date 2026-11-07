package com.example.circlemage.client;

import com.example.circlemage.spell.Spell;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The client's spell selector (v2 casting): a wheel of the spells the player has actually
 * <i>learned</i> (v2.1 — circle-1 basics plus whatever the magic tomes have taught; Anti Magic
 * appears once its advancement is earned). The player cycles it with Alt+scroll (or the next/prev
 * keybinds) and fires the highlighted spell with the single Cast key. Also keeps a client-side
 * <i>estimate</i> of each spell's cooldown so the HUD can dim icons without extra network traffic
 * (the server remains authoritative).
 */
public final class SpellSelectorState {

    /** Wheel display order (Spell ids): bolt, area fire, fireball, slow, anti, wind wall, thunder,
     *  barrier, dash, blade. Only the learned subset is shown. */
    private static final int[] ORDER = {0, 1, 11, 2, 3, 4, 5, 6, 9, 10};

    private static int selectedIndex = 0;
    private static final long[] cooldownEnds = new long[16];

    private SpellSelectorState() {
    }

    /** The spells currently on the wheel: every learned spell, in {@link #ORDER}. */
    public static List<Integer> available() {
        List<Integer> out = new ArrayList<>();
        for (int id : ORDER) {
            boolean known = id == 3 ? ClientJournalState.antiMagic : ClientManaState.isSpellUnlocked(id);
            if (known) {
                out.add(id);
            }
        }
        return out;
    }

    public static int selectedIndex() {
        return selectedIndex;
    }

    /** The highlighted spell's id, or -1 when no magic has been learned yet. */
    public static int selectedSpellId() {
        List<Integer> spells = available();
        if (spells.isEmpty()) {
            return -1;
        }
        selectedIndex = Math.floorMod(selectedIndex, spells.size());
        return spells.get(selectedIndex);
    }

    /** Cycles the wheel by {@code dir} (+1 next / -1 previous) with a soft click and an overlay hint. */
    public static void cycle(int dir) {
        MinecraftClient client = MinecraftClient.getInstance();
        List<Integer> spells = available();
        if (spells.isEmpty()) {
            if (client.player != null) {
                client.inGameHud.setOverlayMessage(
                        Text.literal("You have not awakened any magic yet — reach the 1st circle.")
                                .formatted(Formatting.RED), false);
            }
            return;
        }
        selectedIndex = Math.floorMod(selectedIndex + dir, spells.size());
        if (client.player != null) {
            client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25f, 1.9f);
            client.inGameHud.setOverlayMessage(
                    Text.literal(displayName(spells.get(selectedIndex))).formatted(Formatting.LIGHT_PURPLE), false);
        }
    }

    /** The spells that can be charged (primed) and combined at the 5th circle. */
    public static boolean isChantable(int spellId) {
        return spellId == 0 || spellId == 1 || spellId == 2 || spellId == 4 || spellId == 5;
    }

    /** Display name accounting for the player's active transforms (and the v2.1 fire renames). */
    public static String displayName(int spellId) {
        return switch (spellId) {
            case 0 -> ClientManaState.usesSpear() ? "Spear of Light" : "Magic Missile";
            case 1 -> "Area Fire";
            case 2 -> ClientManaState.usesDarkOrb() ? "Dark Orb" : "Area Slow";
            case 3 -> "Anti Magic";
            case 4 -> "Wall of Wind";
            case 5 -> "Thunder";
            case 6 -> "Barrier";
            case 9 -> "Dash";
            case 10 -> "Wind Blade";
            case 11 -> "Fireball";
            default -> "Spell";
        };
    }

    /** HUD icon for a spell, following the bolt/slow transforms. */
    public static Identifier icon(int spellId) {
        String name = switch (spellId) {
            case 0 -> ClientManaState.usesSpear() ? "icon_spear" : "icon_bolt";
            case 1 -> "icon_fireball";
            case 2 -> ClientManaState.usesDarkOrb() ? "icon_orb" : "icon_slow";
            case 3 -> "icon_anti";
            case 4 -> "icon_wind";
            case 5 -> "icon_thunder";
            case 6 -> "icon_barrier";
            case 9 -> "icon_dash";
            case 10 -> "icon_blade";
            case 11 -> "icon_firebolt";
            default -> "icon_bolt";
        };
        return new Identifier("circlemage", "textures/gui/spell/" + name + ".png");
    }

    /** Records a just-sent cast so the HUD can show the estimated cooldown. */
    public static void markCast(int spellId) {
        MinecraftClient client = MinecraftClient.getInstance();
        Spell spell = Spell.byId(spellId);
        if (client.world == null || spell == null || spellId >= cooldownEnds.length) {
            return;
        }
        cooldownEnds[spellId] = client.world.getTime() + Math.round(spell.cooldownTicks * clientDiscount());
    }

    /** Remaining-cooldown fraction for a spell: 0 = ready, 1 = just cast (client estimate). */
    public static float cooldownFraction(int spellId) {
        MinecraftClient client = MinecraftClient.getInstance();
        Spell spell = Spell.byId(spellId);
        if (client.world == null || spell == null || spellId < 0 || spellId >= cooldownEnds.length) {
            return 0f;
        }
        long remaining = cooldownEnds[spellId] - client.world.getTime();
        if (remaining <= 0) {
            return 0f;
        }
        double total = Math.max(1.0, spell.cooldownTicks * clientDiscount());
        return (float) Math.min(1.0, remaining / total);
    }

    /** Mirrors the server's 5th-circle cooldown discount for the estimate. */
    private static double clientDiscount() {
        return ClientManaState.circle >= 5 ? 0.85 : 1.0;
    }
}
