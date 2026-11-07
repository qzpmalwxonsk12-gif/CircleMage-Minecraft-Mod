package com.example.circlemage.client;

import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.spell.Law;
import com.example.circlemage.spell.Spell;
import com.example.circlemage.client.ClientPackets;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import java.util.ArrayList;
import java.util.List;

/**
 * The Spellbook (v2.2.1) — the tome the player absorbs during the intro, opened with J. It renders
 * as a real open book: a character sheet + tutorial on the home spread, with index ribbons down the
 * right edge. The Magic ribbon opens a second layer of elemental ribbons (fire/water/earth/wind/misc
 * plus locked ??? tabs for chants, combination magic and the Laws). Everything is gated by unlocks —
 * pages only appear once the player has learned or discovered what they describe.
 */
public class SpellbookScreen extends Screen {

    private static final Identifier BOOK = Identifier.fromNamespaceAndPath("circlemage", "textures/gui/spellbook_bg.png");
    private static final int BOOK_W = 340;
    private static final int BOOK_H = 220;

    // Ink palette (dark tones read well on parchment).
    private static final int INK = 0xFF2E2114;
    private static final int INK_HEADER = 0xFF6A2A12;
    private static final int INK_DIM = 0xFF6B5A44;
    private static final int INK_DONE = 0xFF2C6A2A;
    private static final int INK_LOCKED = 0xFF9A6A5A;
    private static final int INK_STAT = 0xFF8A5E1E;
    private static final int INK_HINT = 0xFF3C3A86;
    private static final int PARCHMENT = 0xFFE8DCBC;
    private static final int PARCHMENT_EDGE = 0xFF9A845C;

    private static final int LINE_H = 11;
    private static final long TURN_MS = 220;

    // Navigation.
    private enum View { HOME, MAGIC, STRUCTURES, NPCS, ITEMS, QUESTS, PROGRESSION }
    private enum Magic { FIRE, WATER, EARTH, WIND, MISC, CHANTS, COMBINATION, LAWS }

    private View view = View.HOME;
    private Magic magic = Magic.MISC;
    private int scroll = 0;
    private int maxScroll = 0;
    private long turnStart = -10000;

    // Layout (set in init).
    private int bookX, bookY;
    private int lpX, rpX, pageW, contentTop, contentBottom, linesPerPage;
    private int tabX, tabTop, ribbonW;
    private final List<Bookmark> bookmarks = new ArrayList<>();

    private boolean notifiedClose = false;

    public SpellbookScreen() {
        super(Component.literal("Spellbook"));
    }

    @Override
    protected void init() {
        bookX = (this.width - BOOK_W) / 2;
        bookY = (this.height - BOOK_H) / 2;
        lpX = bookX + 26;
        rpX = bookX + 184;
        pageW = 130;
        contentTop = bookY + 30;
        contentBottom = bookY + 200;
        linesPerPage = (contentBottom - contentTop) / LINE_H;
        // Ribbons hang off the book's right edge; clamp their width so they never leave the screen
        // on small GUI resolutions (the v2.2.1 out-of-bounds bug).
        tabX = bookX + BOOK_W - 14;
        ribbonW = Math.max(34, Math.min(64, this.width - tabX - 4));
        tabTop = bookY + 22;
    }

    // --- Rendering ------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xA0100D18);
        context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,BOOK, bookX, bookY, 0f, 0f, BOOK_W, BOOK_H, BOOK_W, BOOK_H);

        buildBookmarks();
        renderBookmarks(context, mouseX, mouseY);

        if (view == View.HOME) {
            renderHome(context, mouseX, mouseY);
        } else {
            renderFlow(context, buildFlow());
        }

        renderPageTurn(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    /** The home spread: player portrait + character sheet on the left, tutorial on the right. */
    private void renderHome(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        // Left page: character portrait (clipped to the page) + stats.
        int px = lpX + 40;
        int py = contentTop + 84;
        context.enableScissor(lpX, contentTop, lpX + pageW, contentTop + 92);
        if (this.minecraft != null && this.minecraft.player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(context, px-40, py-84, px+40, py+8, 42, .0625f, mouseX, mouseY,
                    this.minecraft.player);
        }
        context.disableScissor();

        int y = contentTop + 100;
        String name = this.minecraft != null && this.minecraft.player != null
                ? this.minecraft.player.getGameProfile().name() : "Apprentice";
        context.text(this.font, Component.literal(name).withStyle(net.minecraft.ChatFormatting.BOLD),
                lpX, y, INK_HEADER, false);
        y += LINE_H + 2;
        int c = ClientManaState.circle;
        String stage = c >= 1 ? c + ordinal(c) + " Circle Mage" : "Aspiring Mage";
        y = stat(context, "Stage", stage, lpX, y);
        y = stat(context, "Mana", compact((long) Math.floor(ClientManaState.currentMana)) + " / "
                + compact(ClientManaState.threshold), lpX, y);
        y = stat(context, "Realm Buffs", String.valueOf(ClientManaState.unlockedBuffs), lpX, y);
        y = stat(context, "Traits", "None", lpX, y);

        // Right page: tutorial (wrapped to the page width).
        List<Line> tut = new ArrayList<>();
        tut.add(Line.header("How to Begin"));
        tut.add(Line.blank());
        wrap(tut, "Press M to channel mana — hold still and let the world pour in.", INK, 0);
        wrap(tut, "Reach the 1st circle to awaken Magic Missile and Dash.", INK, 0);
        wrap(tut, "Hold Alt and scroll to pick a spell, then press R to cast (hold to charge).", INK, 0);
        wrap(tut, "Wandering mages roam the wilds — greet, trade, or duel them.", INK, 0);
        wrap(tut, "Search chests for magic tomes to learn new spells.", INK, 0);
        wrap(tut, "Tap the ribbons to explore this tome.", INK_HINT, 0);
        renderColumn(context, tut, rpX, false);
    }

    private int stat(GuiGraphicsExtractor context, String key, String value, int x, int y) {
        context.text(this.font, Component.literal(key + ": "), x, y, INK_DIM, false);
        int keyW = this.font.width(key + ": ");
        context.text(this.font, Component.literal(value), x + keyW, y, INK_STAT, false);
        return y + LINE_H;
    }

    /** Renders a flow of lines across both pages, honouring the scroll offset. */
    private void renderFlow(GuiGraphicsExtractor context, List<Line> lines) {
        maxScroll = Math.max(0, lines.size() - 2 * linesPerPage);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        List<Line> left = new ArrayList<>();
        List<Line> right = new ArrayList<>();
        for (int i = 0; i < linesPerPage; i++) {
            int li = scroll + i;
            if (li < lines.size()) {
                left.add(lines.get(li));
            }
            int ri = scroll + linesPerPage + i;
            if (ri < lines.size()) {
                right.add(lines.get(ri));
            }
        }
        renderColumn(context, left, lpX, false);
        renderColumn(context, right, rpX, false);

        if (maxScroll > 0) {
            String more = scroll < maxScroll ? "▼ more" : "";
            if (!more.isEmpty()) {
                context.text(this.font, Component.literal(more), rpX + pageW - 40, contentBottom + 1, INK_DIM, false);
            }
            if (scroll > 0) {
                context.text(this.font, Component.literal("▲"), lpX, contentBottom + 1, INK_DIM, false);
            }
        }
    }

    private void renderColumn(GuiGraphicsExtractor context, List<Line> lines, int x, boolean ignored) {
        // Hard-clip every column to its page so nothing can render off the parchment.
        context.enableScissor(x, contentTop - 2, x + pageW + 2, contentBottom + 2);
        int y = contentTop;
        for (Line line : lines) {
            if (!line.text.isEmpty()) {
                context.text(this.font, Component.literal(line.text), x + line.indent, y, line.color, false);
            }
            y += LINE_H;
        }
        context.disableScissor();
    }

    // --- Bookmarks ------------------------------------------------------------

    private void buildBookmarks() {
        bookmarks.clear();
        bookmarks.add(new Bookmark("Home", 0xFF7A4A2A, true, "home"));
        if (view == View.MAGIC) {
            bookmarks.add(new Bookmark("Fire", 0xFFB4502A, true, "m_fire"));
            bookmarks.add(new Bookmark("Water", 0xFF2E6AB0, true, "m_water"));
            bookmarks.add(new Bookmark("Earth", 0xFF6E7A38, true, "m_earth"));
            bookmarks.add(new Bookmark("Wind", 0xFF3E9A6E, true, "m_wind"));
            bookmarks.add(new Bookmark("Misc", 0xFF7A4AB0, true, "m_misc"));
            bookmarks.add(new Bookmark(ClientJournalState.chants ? "Chants" : "???",
                    0xFF6A5A9A, ClientJournalState.chants, "m_chants"));
            bookmarks.add(new Bookmark(ClientJournalState.combination ? "Combos" : "???",
                    0xFF6A5A9A, ClientJournalState.combination, "m_combo"));
            bookmarks.add(new Bookmark(ClientJournalState.laws ? "Laws" : "???",
                    0xFF6A5A9A, ClientJournalState.laws, "m_laws"));
        } else {
            bookmarks.add(new Bookmark("Magic", 0xFF7A4AB0, true, "magic"));
            bookmarks.add(new Bookmark("Structures", 0xFF6C6C74, true, "structures"));
            bookmarks.add(new Bookmark("NPCs", 0xFF2E8A8A, true, "npcs"));
            bookmarks.add(new Bookmark("Items", 0xFFB08A2A, true, "items"));
            bookmarks.add(new Bookmark("Quests", 0xFF3E8A46, true, "quests"));
            bookmarks.add(new Bookmark("Progress", 0xFF3E5AA6, true, "progression"));
        }
    }

    private void renderBookmarks(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int ty = tabTop;
        int right = tabX + ribbonW;
        for (Bookmark b : bookmarks) {
            boolean selected = isSelected(b.code);
            boolean hover = mouseX >= tabX - 6 && mouseX <= right && mouseY >= ty && mouseY <= ty + 18;
            int left = tabX - (selected ? 6 : 0);
            int fill = b.enabled ? b.color : 0xFF555055;
            if (hover && b.enabled) {
                fill = brighten(fill);
            }
            context.fill(left, ty, right, ty + 18, fill);
            context.outline(left, ty, right - left, 18, selected ? 0xFFF2E9D0 : 0xFF201810);
            int textColor = b.enabled ? 0xFFF3ECD6 : 0xFFB8AEA8;
            // Clip the label to the ribbon so long names never spill past its edge.
            context.enableScissor(left + 2, ty, right - 2, ty + 18);
            context.text(this.font, Component.literal(b.label), tabX + 4, ty + 5, textColor, false);
            context.disableScissor();
            b.y = ty;
            ty += 21;
        }
    }

    private boolean isSelected(String code) {
        return switch (code) {
            case "home" -> view == View.HOME;
            case "magic" -> view == View.MAGIC;
            case "structures" -> view == View.STRUCTURES;
            case "npcs" -> view == View.NPCS;
            case "items" -> view == View.ITEMS;
            case "quests" -> view == View.QUESTS;
            case "progression" -> view == View.PROGRESSION;
            case "m_fire" -> view == View.MAGIC && magic == Magic.FIRE;
            case "m_water" -> view == View.MAGIC && magic == Magic.WATER;
            case "m_earth" -> view == View.MAGIC && magic == Magic.EARTH;
            case "m_wind" -> view == View.MAGIC && magic == Magic.WIND;
            case "m_misc" -> view == View.MAGIC && magic == Magic.MISC;
            case "m_chants" -> view == View.MAGIC && magic == Magic.CHANTS;
            case "m_combo" -> view == View.MAGIC && magic == Magic.COMBINATION;
            case "m_laws" -> view == View.MAGIC && magic == Magic.LAWS;
            default -> false;
        };
    }

    // --- Section content ------------------------------------------------------

    private List<Line> buildFlow() {
        return switch (view) {
            case MAGIC -> buildMagic();
            case STRUCTURES -> buildStructures();
            case NPCS -> buildNpcs();
            case ITEMS -> buildItems();
            case QUESTS -> buildQuests();
            case PROGRESSION -> buildProgression();
            default -> new ArrayList<>();
        };
    }

    private List<Line> buildMagic() {
        List<Line> out = new ArrayList<>();
        switch (magic) {
            case FIRE -> {
                out.add(Line.header("Fire Magic"));
                out.add(Line.blank());
                int shown = 0;
                shown += spellPage(out, 1);   // Area Fire
                shown += spellPage(out, 11);  // Fireball
                if (shown == 0) {
                    out.add(Line.dim("These pages are still blank. Seek a Tome of Fire."));
                }
            }
            case WIND -> {
                out.add(Line.header("Wind Magic"));
                out.add(Line.blank());
                int shown = 0;
                shown += spellPage(out, 4);   // Wall of Wind
                shown += spellPage(out, 10);  // Wind Blade
                if (shown == 0) {
                    out.add(Line.dim("These pages are still blank. Seek a Tome of Wind."));
                }
            }
            case MISC -> {
                out.add(Line.header("Miscellany"));
                out.add(Line.blank());
                int shown = 0;
                shown += spellPage(out, 0);   // Magic Missile (innate)
                shown += spellPage(out, 9);   // Dash (innate)
                shown += spellPage(out, 2);   // Area Slow
                shown += spellPage(out, 5);   // Thunder
                shown += spellPage(out, 6);   // Barrier
                shown += spellPage(out, 3);   // Anti Magic
                if (shown == 0) {
                    out.add(Line.dim("These pages are still blank. Reach the 1st circle to begin."));
                }
            }
            case WATER -> {
                out.add(Line.header("Water Magic"));
                out.add(Line.blank());
                out.add(Line.dim("Water magic is not yet known to this world."));
            }
            case EARTH -> {
                out.add(Line.header("Earth Magic"));
                out.add(Line.blank());
                out.add(Line.dim("Earth magic is not yet known to this world."));
            }
            case CHANTS -> {
                out.add(Line.header("Passive Chants"));
                out.add(Line.blank());
                if (ClientJournalState.chants) {
                    wrap(out, "Always-on enchantments fed by your mana. Choose one from the Magic menu (K).", INK_BODY(), 0);
                    out.add(Line.blank());
                    chant(out, "Channeling", "2.5x mana regen; spells cost no mana.");
                    chant(out, "Attack", "+30% spell damage (200 mana/s).");
                    chant(out, "Reinforce", "+20% speed, 30% damage reduction (200 mana/s).");
                    chant(out, "Vitality", "Regeneration III (100 mana/s).");
                } else {
                    out.add(Line.locked("This knowledge is not yet yours."));
                }
            }
            case COMBINATION -> {
                out.add(Line.header("Combination Magic"));
                out.add(Line.blank());
                if (ClientJournalState.combination) {
                    wrap(out, "Hold a spell to charge it, then pick a second to weave the two together.", INK_BODY(), 0);
                    out.add(Line.blank());
                    chant(out, "Chaos Spear", "Spear + Dark Orb: a homing chaos lance.");
                    chant(out, "Thunder Warp", "Set a mark, then blink to it.");
                    chant(out, "Blaze", "A sustained flamethrower cone.");
                    chant(out, "Divine Wrath", "A tracking pillar of light.");
                } else {
                    out.add(Line.locked("This knowledge is not yet yours."));
                }
            }
            case LAWS -> {
                out.add(Line.header("The Laws"));
                out.add(Line.blank());
                if (ClientJournalState.laws) {
                    wrap(out, "Reality-bending powers wielded only at the 9th circle.", INK_BODY(), 0);
                    out.add(Line.blank());
                    for (Law law : Law.values()) {
                        if (law == Law.NONE) {
                            continue;
                        }
                        boolean u = ClientJournalState.isLawUnlocked(law.ordinal());
                        out.add(new Line((u ? "✓ " : "✗ ") + law.displayName, u ? INK_DONE : INK_LOCKED, 0));
                    }
                } else {
                    out.add(Line.locked("This knowledge is not yet yours."));
                }
            }
        }
        return out;
    }

    private void chant(List<Line> out, String name, String desc) {
        out.add(new Line("• " + name, INK, 0));
        wrap(out, desc, INK_DIM, 6);
        out.add(Line.blank());
    }

    /** Adds a spell's page if the player knows it. Returns 1 if shown, else 0. */
    private int spellPage(List<Line> out, int spellId) {
        if (!spellKnown(spellId)) {
            return 0;
        }
        Spell spell = Spell.byId(spellId);
        out.add(new Line("✓ " + SpellSelectorState.displayName(spellId), INK_DONE, 0));
        if (spell != null) {
            // Wrapped, so long stat strings can never run off the page.
            wrap(out, "Cost " + spell.manaCost + " · CD " + String.format("%.1fs", spell.cooldownTicks / 20.0)
                    + damageSuffix(spellId), INK_STAT, 6);
        }
        wrap(out, spellDesc(spellId), INK, 6);
        out.add(Line.blank());
        return 1;
    }

    private boolean spellKnown(int spellId) {
        if (spellId == 3) {
            return ClientJournalState.antiMagic;
        }
        return ClientManaState.isSpellUnlocked(spellId);
    }

    private String damageSuffix(int spellId) {
        String d = switch (spellId) {
            case 0 -> "2 hearts";
            case 11 -> "2 hearts + ignites";
            case 10 -> "~0.7 x5";
            case 5 -> "lightning";
            default -> "";
        };
        return d.isEmpty() ? "" : "  ·  " + d;
    }

    private String spellDesc(int spellId) {
        return switch (spellId) {
            case 0 -> "A homing arcane bolt — your first weapon. Aim near a foe to lock on.";
            case 9 -> "A quick burst toward your held movement key, with no fall damage.";
            case 1 -> "Ignites a lingering field of fire at the crosshair.";
            case 11 -> "A blazing bolt that sets whatever it strikes alight.";
            case 4 -> "A gale that hurls foes away from you.";
            case 10 -> "A volley of five slicing gusts toward your aim.";
            case 2 -> "A wide field that mires foes in slowness.";
            case 5 -> "Calls lightning down on the crosshair.";
            case 6 -> "Raise a wall ahead — or a protective dome while sneaking.";
            case 3 -> "Cleanse harmful magic and gain 5s of immunity.";
            default -> "";
        };
    }

    private List<Line> buildStructures() {
        List<Line> out = new ArrayList<>();
        out.add(Line.header("Places of Power"));
        out.add(Line.blank());
        out.add(new Line("• Ruined Mage Towers", INK_HEADER, 0));
        wrap(out, "Crumbling spires left by mages of old, about as common as villages. Their "
                + "shelves still hold oddments — and sometimes something better.", INK, 6);
        out.add(Line.blank());
        out.add(new Line("• Mage Towers", INK_HEADER, 0));
        wrap(out, "Intact towers, rarer than their ruins, and never unattended: a guardian mage "
                + "patrols the grounds and does not suffer trespassers. Both chests hold real "
                + "treasure for whoever bests them.", INK, 6);
        out.add(Line.blank());
        out.add(new Line("• The Deep Towers", INK_HEADER, 0));
        wrap(out, "Whispers speak of a rare tower whose cellars descend some twenty blocks beneath "
                + "the earth. What its keeper guards down there is anyone's guess.", INK, 6);
        return out;
    }

    private List<Line> buildNpcs() {
        List<Line> out = new ArrayList<>();
        out.add(Line.header("Encounters"));
        out.add(Line.blank());
        boolean any = false;
        if (ClientJournalState.encounteredMage) {
            any = true;
            npc(out, "Wandering Mage",
                    "A roaming mage of the 1st-3rd circle. Peaceful unless provoked: greet one for "
                            + "guidance, or trade an emerald for a Mana Vial. In battle it strafes, dashes "
                            + "from projectiles and telegraphs its missiles with a casting circle; a wounded "
                            + "one yields and vanishes.");
        }
        if (ClientJournalState.prodigyDefeated) {
            any = true;
            npc(out, "The Prodigy",
                    "A gifted mage whose talent surpassed his peers. His final words hinted at a master.");
        }
        if (ClientJournalState.masterDefeated) {
            any = true;
            npc(out, "The Master",
                    "Teacher of The Prodigy — a powerful 5th-circle mage.");
        }
        if (ClientJournalState.lichDefeated) {
            any = true;
            npc(out, "The Lich", "One of the greatest magical threats ever faced.");
        }
        if (!any) {
            out.add(Line.dim("You have not yet met anyone of note."));
            out.add(Line.dim("Pages will be written as you wander."));
        }
        return out;
    }

    private void npc(List<Line> out, String name, String desc) {
        out.add(new Line("✓ " + name, INK_DONE, 0));
        wrap(out, desc, INK, 6);
        out.add(Line.blank());
    }

    private List<Line> buildItems() {
        List<Line> out = new ArrayList<>();
        out.add(Line.header("Items & Materials"));
        out.add(Line.blank());
        item(out, "Magic Tomes", "Teach one random spell of their element, or fizzle. Found in chests.");
        item(out, "Mana Crystal", "Attune to speed natural mana flow by +5/s (max 3). Chest treasure.");
        item(out, "Mana Dust", "Residue of a mage's craft. Dropped by Wandering Mages.");
        item(out, "Mana Vial", "Restores 500 mana. Craft: bottle + 2 mana dust.");
        item(out, "Arcane Focus", "-15% cost, +10% damage while held. Craft: crystal / yarn / stick.");
        item(out, "Mana Liquid", "Craft: water bottle + mana dust.");
        item(out, "Arcane Wood", "Craft: mana liquid + planks.");
        item(out, "Arcane Glue", "Craft: mana liquid + glowstone dust + mana dust + bone meal.");
        item(out, "Arcane Station", "Wand bench (A glue, B material, C core, D crystal). Craft: 2 carpets over 2x2 arcane wood.");
        item(out, "Arcane Wand", "Assembled at the station; material/core/crystal shape its look and its bonuses.");
        item(out, "Glow Crystal", "Wand core — sheds torchlight. Craft: 2 glowstone dust + mana dust + glue.");
        item(out, "Dragon Crystal", "Dormant wand core. Craft: 2 dragon's breath + 2 glow crystals + 2 glue.");
        return out;
    }

    private void item(List<Line> out, String name, String desc) {
        out.add(new Line("• " + name, INK_HEADER, 0));
        wrap(out, desc, INK, 6);
        out.add(Line.blank());
    }

    private List<Line> buildQuests() {
        List<Line> out = new ArrayList<>();
        out.add(Line.header("Mage's Journey"));
        out.add(Line.blank());
        int c = ClientManaState.circle;
        boolean learnedTome = anyTomeSpell();
        quest(out, c >= 1, "Reach the 1st Circle");
        quest(out, learnedTome, "Learn a spell from a magic tome");
        quest(out, c >= 3, "Reach the 3rd Circle");
        quest(out, ClientJournalState.encounteredMage, "Encounter a Wandering Mage");
        quest(out, ClientJournalState.arrays, "Discover Magic Arrays");
        quest(out, ClientJournalState.chants, "Discover Passive Chants");
        quest(out, ClientJournalState.prodigyDefeated, "Defeat The Prodigy");
        quest(out, c >= 5, "Reach the 5th Circle");
        quest(out, ClientJournalState.combination, "Learn Combination Magic");
        quest(out, ClientJournalState.masterDefeated, "Defeat The Master");
        quest(out, c >= 9, "Reach the 9th Circle");
        quest(out, ClientJournalState.laws, "Unlock the Laws");
        return out;
    }

    private void quest(List<Line> out, boolean done, String label) {
        out.add(new Line((done ? "☑ " : "☐ ") + label, done ? INK_DONE : INK, 0));
    }

    private boolean anyTomeSpell() {
        for (int id : new int[]{1, 2, 4, 5, 6, 10, 11}) {
            if (ClientManaState.isSpellUnlocked(id)) {
                return true;
            }
        }
        return false;
    }

    private List<Line> buildProgression() {
        List<Line> out = new ArrayList<>();
        out.add(Line.header("The Nine Circles"));
        out.add(Line.blank());
        String[] hint = {
                "",
                "The first awakening — basic mana manipulation.",
                "Keep channelling; the threshold climbs.",
                "Casting magic may awaken Basic Anti-Magic and Arrays.",
                "Requires the Basic Anti-Magic advancement.",
                "Mana grows thin; keep casting for random masteries.",
                "Requires Combination Magic.",
                "The climb steepens far beyond mere mana.",
                "Seek understanding, not just power.",
                "Its full requirements reveal themselves only as you near them.",
        };
        int c = ClientManaState.circle;
        for (int i = 1; i <= 9; i++) {
            String status = c >= i ? "✓ " : (c + 1 == i ? "● " : "✗ ");
            int color = c >= i ? INK_DONE : (c + 1 == i ? INK_STAT : INK_LOCKED);
            out.add(new Line(status + i + ordinal(i) + " Circle", color, 0));
            wrap(out, hint[i], INK_DIM, 6);
        }
        return out;
    }

    // --- Page-turn animation --------------------------------------------------

    private void renderPageTurn(GuiGraphicsExtractor context) {
        long elapsed = Util.getMillis() - turnStart;
        if (elapsed >= TURN_MS) {
            return;
        }
        float p = elapsed / (float) TURN_MS;
        int fullW = pageW + 16;
        int coverW = (int) ((1.0f - p) * fullW);
        int x0 = rpX - 4;
        // A parchment page sweeping off the right page to reveal the new content.
        context.fill(x0, contentTop - 6, x0 + coverW, contentBottom + 4, PARCHMENT);
        context.fill(x0 + coverW - 2, contentTop - 6, x0 + coverW, contentBottom + 4, PARCHMENT_EDGE);
    }

    private void startTurn() {
        turnStart = Util.getMillis();
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
        }
    }

    // --- Input ----------------------------------------------------------------

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) { double mouseX=event.x(), mouseY=event.y(); int button=event.button();
        for (Bookmark b : bookmarks) {
            if (mouseX >= tabX - 6 && mouseX <= tabX + ribbonW && mouseY >= b.y && mouseY <= b.y + 18) {
                if (!b.enabled) {
                    return true;
                }
                navigate(b.code);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void navigate(String code) {
        View before = view;
        Magic beforeMagic = magic;
        switch (code) {
            case "home" -> view = View.HOME;
            case "magic" -> {
                view = View.MAGIC;
                magic = Magic.MISC;
            }
            case "structures" -> view = View.STRUCTURES;
            case "npcs" -> view = View.NPCS;
            case "items" -> view = View.ITEMS;
            case "quests" -> view = View.QUESTS;
            case "progression" -> view = View.PROGRESSION;
            case "m_fire" -> magic = Magic.FIRE;
            case "m_water" -> magic = Magic.WATER;
            case "m_earth" -> magic = Magic.EARTH;
            case "m_wind" -> magic = Magic.WIND;
            case "m_misc" -> magic = Magic.MISC;
            case "m_chants" -> magic = Magic.CHANTS;
            case "m_combo" -> magic = Magic.COMBINATION;
            case "m_laws" -> magic = Magic.LAWS;
            default -> {
            }
        }
        if (view != before || magic != beforeMagic) {
            scroll = 0;
            startTurn();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double amount) {
        if (view != View.HOME) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (amount * 3)));
        }
        return true;
    }

    @Override
    public void onClose() {
        notifyClosed();
        super.onClose();
    }

    @Override
    public void removed() {
        notifyClosed();
        super.removed();
    }

    private void notifyClosed() {
        if (!notifiedClose && ClientManaState.introStage == 1) {
            notifiedClose = true;
            ClientPackets.send(ModNetworking.BOOK_CLOSED, FriendlyByteBufs.empty());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- Text helpers ---------------------------------------------------------

    private static int INK_BODY() {
        return INK;
    }

    private void wrap(List<Line> out, String text, int color, int indent) {
        int maxWidth = pageW - indent;
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String trial = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && this.font.width(trial) > maxWidth) {
                out.add(new Line(cur.toString(), color, indent));
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(trial);
            }
        }
        if (cur.length() > 0) {
            out.add(new Line(cur.toString(), color, indent));
        }
    }

    private static String ordinal(int n) {
        if (n % 100 >= 11 && n % 100 <= 13) {
            return "th";
        }
        return switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    private static String compact(long v) {
        if (v >= 1_000_000_000L) {
            return String.format("%.1fB", v / 1_000_000_000.0);
        }
        if (v >= 1_000_000L) {
            return String.format("%.1fM", v / 1_000_000.0);
        }
        if (v >= 10_000L) {
            return String.format("%.1fK", v / 1_000.0);
        }
        return Long.toString(v);
    }

    private static int brighten(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 30);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + 30);
        int b = Math.min(255, (argb & 0xFF) + 30);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // --- Small structs --------------------------------------------------------

    private static final class Bookmark {
        final String label;
        final int color;
        final boolean enabled;
        final String code;
        int y;

        Bookmark(String label, int color, boolean enabled, String code) {
            this.label = label;
            this.color = color;
            this.enabled = enabled;
            this.code = code;
        }
    }

    private record Line(String text, int color, int indent) {
        static Line header(String t) {
            return new Line(t, INK_HEADER, 0);
        }

        static Line body(String t) {
            return new Line(t, INK, 0);
        }

        static Line dim(String t) {
            return new Line(t, INK_DIM, 0);
        }

        static Line locked(String t) {
            return new Line(t, INK_LOCKED, 0);
        }

        static Line blank() {
            return new Line("", INK, 0);
        }
    }
}
