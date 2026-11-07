# CircleMage 2.3.0 — returning-player guide

This describes the source in `MAgicModSource` as reviewed on 6 October 2026. Its built JAR matches the installed mod in your CurseForge `magic` instance. Timings assume 20 ticks per second. Mana costs are base values; circle bonuses, held equipment, and chants can reduce them.

## Get comfortable again in five minutes

1. Open **J** for the journal/spellbook, **K** for forms, passive chants, and arrays, and **L** for Laws. Pages appear as you make progress, so a partly empty journal is expected.
2. Check Options → Controls → CircleMage. Your installed controls differ considerably from the source defaults; use the table below. Your **P** cast key and **Y** wind keys currently have conflicts.
3. Hold **Left Alt** and scroll to choose a known spell. Tap your **Cast Selected Spell** key to fire. Your instance uses **P**; a fresh installation uses **R**.
4. Toggle **M** to channel mana. You become stationary while it is on. Toggle it off to move. Avoid holding ordinary string unless you intend to make Arcane String: string infusion replaces the channeling mana gain.
5. At circle 1 you already know **Magic Missile** and **Dash**. Learn other ordinary spells from Fire, Wind, and Misc Tomes found in chests and towers. Friendly wandering mages exchange an emerald for a Mana Vial.
6. At circle 3, keep successfully using magic until you discover Basic Anti Magic; this is required to enter circle 4. At circle 5, experiment with holding spells and combining them.

On a fresh character, right-click the glowing book that appears in front of you. It rises, flies into you, and prompts the journal. Closing the journal completes the "missing pages" intro. Returning characters do not need to repeat it.

Your latest disk save is `New World (18)`: a creative-mode circle-1 character, with one attuned Mana Crystal and Magic Missile, Dash, and Thunder learned. See [saved-world notes](WORLD_NOTES.md) for the exact snapshot and nearby NPCs. This is disk data, not a reading of the currently open world.

## Controls: your instance versus fresh defaults

The middle column comes from the installed instance's `options.txt` as inspected. A reset/new instance will use the right column. All can be rebound.

| Action | Your saved instance | Source default |
|---|---|---|
| Toggle mana channeling | M | M |
| Cast selected spell; hold to charge | **P** | R |
| Choose spell | Left Alt + mouse wheel | Left Alt + mouse wheel |
| Next/previous spell keys | Unbound | Unbound |
| Quick Magic Missile / Spear | R | Unbound |
| Quick Area Fire | Z | T |
| Quick Area Slow / Dark Orb | V | V |
| Anti Magic: hold about 0.75 seconds | G | G |
| Quick Wall of Wind | **Y** | Y |
| Quick Thunder | H | H |
| Barrier; sneak for self dome | X | Z |
| Dash | Q | Unbound |
| Wind Blade | **Y** | Unbound |
| Begin carving an array | C | C |
| Toggle Missile / Spear form | O | X |
| Activate selected Law | B | B |
| Forms, chants, arrays menu | K | K |
| Laws menu | L | L |
| Journal / spellbook | J | J |

**Binding problems already present:** P is also assigned to vanilla drop, social interactions, and three Iris actions: reload, toggle shaders, and shader-pack selection. Y is shared by Wall of Wind and Wind Blade. L also opens vanilla advancements; C is shared with the creative toolbar-saving action. These bindings compete for the same key; exact handling depends on the active screen and input route. Check Controls if an action appears unreliable. Fresh defaults also give Area Fire the vanilla chat key T. No bindings were changed during this review.

There is no dedicated quick key for the newer projectile **Fireball**: choose it with the selector. Names are confusing because the old internal `FIREBALL`/id 1 now displays as **Area Fire**; the new `FIRE_BOLT`/id 11 displays as **Fireball**.

The three mod menus do not pause the game. Open them somewhere calm.

## Mana, circles, and what to do when progression stops

Mana is both your casting resource and the meter used to ascend. It is not a separate lifetime XP counter. Spending mana slows advancement. When you reach a valid next-circle threshold, you ascend and mana resets to zero; excess is lost. A locked advancement gate can leave the bar capped at its threshold.

| Circle you are entering | Mana needed while in the previous circle | Additional requirement / new feature |
|---|---:|---|
| 1 | 100 | Magic Missile and Dash; channeling boost and natural regeneration |
| 2 | 5,000 | Larger natural regeneration |
| 3 | 20,000 | Successful magic can discover Anti Magic and arrays |
| 4 | 50,000 | **Basic Anti Magic advancement required**; passive chants can be discovered |
| 5 | 100,000 | Hold-to-enhance/full charge; Missile → Spear; 15% base cost/cooldown discount; combination discovery can roll |
| 6 | 200,000 | **Combination Magic advancement required**; Slow → Dark Orb |
| 7 | 400,000 | Continued growth |
| 8 | 700,000 | Continued growth |
| 9 | **10,000,000,000** | **Laws discovery advancement required**; selected Laws become usable |

The code's ninth-circle threshold really is ten billion. The old README's one-million figure is stale. With all eight available earlier-circle realm buffs, the Channeling chant, three Mana Crystals, and no debug multiplier, circle-8 generation is about 9,623 mana/second: roughly **289 hours of continuous channeling** from zero. This is a major balance issue in the current implementation, not a missed shortcut in the guide.

### Generation and realm buffs

- Circle 0 channels at 5 mana/second. From circle 1 the channeling multiplier is 3, giving 15/second before realm buffs.
- Each acquired realm buff doubles channeling gain and multiplies ordinary supported magic damage by **1.2**. The older 1.5 damage figure is stale.
- Natural regeneration, once circle 1 is reached, is `current circle + 5 × attuned crystals` mana/second. It continues while walking, channeling, or infusing string.
- Mana Crystals add **5/second natural regeneration each**, up to three. They no longer double channeling. Right-click to attune; a fourth is refused rather than consumed.
- A realm buff rolls only for your **current** circle. If you ascend before earning it, ordinary play does not later award that missed lower-circle buff.
- The Channeling chant multiplies channeling by 2.5; it does not multiply the separate natural regeneration.
- An Arcane Focus can toggle channeling by right-click, as well as with M.
- Progress is copied across death and dimension changes. Ordinary inventory retention still follows Minecraft's own rules.

### Discoveries are random

These chances are per successful magic use through the discovery hook, unless noted. Repeated failed casts do not count as successful use.

| Discovery | Eligible from | Chance |
|---|---|---:|
| Realm buff for current circle | Circle 1 | 5% |
| Basic Anti Magic | Circle 3 | 0.5% |
| Array carving | Circle 3 | 1% |
| Passive chants | Circle 4 | 5% |
| Combination Magic advancement | Circle 5 | 2% |
| Base Laws discovery | Any circle, periodic online tick | 0.01% per second |

Laws discovery has a mean waiting time of about 2.78 hours, with no guaranteed deadline. You can discover it early, but cannot use Laws until circle 9. The Master's Fragment of Law is currently unrelated to this roll.

If stuck before circle 4, use known magic until Anti Magic is discovered. If stuck before circle 6, continue successful magic at circle 5 until Combination Magic is discovered. If stuck before circle 9, both the Laws advancement and the enormous mana threshold matter.

## Learning ordinary spells

Using a tome teaches one randomly chosen spell from its school that you do not already know. In survival the tome is consumed even when there is nothing it can teach.

| Tome | Current learnable spells |
|---|---|
| Fire | Area Fire; Fireball projectile |
| Wind | Wall of Wind; Wind Blade |
| Misc | Area Slow; Thunder; Barrier |
| Water | **No implemented spells yet** |
| Earth | **No implemented spells yet** |

Keep Water and Earth Tomes for possible future content; using them currently consumes them without teaching a spell. Magic Missile and Dash are innate circle-1 basics. Anti Magic is learned by discovery, not a tome. Spear and Dark Orb are forms of already-known underlying spells.

Mana Crystals and tomes are added to vanilla Minecraft chest loot, and tower loot has its own richer pools. There is no crafting recipe for a tome or Mana Crystal in this project.

## Ordinary spell reference

Damage is expressed in HP; two HP is one heart. Costs/cooldowns below are the enum's base values, before circle-5 discount, held equipment, and chants. Some effects use fixed or vanilla damage rather than the shared ordinary spell damage multiplier.

| Spell | Base mana | Cooldown | Base cast delay | What it does |
|---|---:|---:|---:|---|
| Magic Missile | 50 | 1s | Instant | Light bolt, base 4 HP; attempts to home on a target close to the crosshair ray endpoint |
| Area Fire | 300 | 7s | Instant | Fire field at aimed point: radius 3, lasts 5s; enhanced radius 5 with stronger burning |
| Area Slow | 500 | 5s | 0.75s | Radius-10 field, 8s; Slowness I normally, II when enhanced |
| Basic Anti Magic | 750 | 10s | Hold G for 0.75s | Clears harmful statuses/fire and overlapping spell fields; grants 5s immunity to negative effects |
| Wall of Wind | 200 | 3s | Instant | Frontal shove; enhanced gives stronger push and recoil, full charge produces a cyclone |
| Thunder | 500 | 5s | 0.75s | Lightning at aimed point; normal/enhanced/full charge gives 1/3/6 bolts |
| Barrier | 750 | 8s | 1.5s | A 5×5 wall for 7s; cast while sneaking for a self dome |
| Spear of Light | 75 | 1s | Instant | Circle-5 Missile form; faster and stronger bolt; enhanced/full versions interact with floating Dark Orbs |
| Dark Orb | 600 | 6s | Instant | Circle-6 Slow form; ordinary cast throws an orb, enhanced cast makes a floating orb for up to 10s |
| Dash | 100 | 2s | Instant | Movement burst using held movement direction; forward if no movement input |
| Wind Blade | 150 | 3s | Instant | Five slicing projectiles, each based on one third of Missile damage |
| Fireball | 100 | 2s | Instant | New Fire Tome projectile; base 4 HP and ignites the target |

Anti Magic is **negative-effect protection**, not complete invulnerability and not removal of a boss domain. The dome normally lasts 10 seconds or five hits. Keeping sneak held pauses its expiry and hit-count depletion, but costs **500 mana/second**, plus **500 mana per absorbed hit**; inability to pay while sustaining shatters it. When not sneaking, each hit attempts that mana payment and consumes one of the five hit charges. The cast's chat message incorrectly says 100 mana/second. These additional costs still matter with Channeling chant selected.

### Forms and held charging

From circle 5, Magic Missile defaults to Spear of Light. At circle 6, a known Area Slow can become Dark Orb. Use K to choose forms; your O quick toggle changes Missile/Spear. Revert to the original Missile form when making Thunder Mark.

For Missile/Spear, Area Fire, Slow/Dark Orb, Wind, and Thunder:

- Release before 0.5 seconds: ordinary cast.
- Hold at least 0.5 seconds: enhanced cast.
- Hold 3 seconds: fully charged cast.

Barrier, Anti Magic, Dash, Wind Blade, and the newer projectile Fireball do not use this charge system. Some spells also retain their base cast delay after release.

## Combination magic

You need the Combination Magic discovery. The first ingredient is the held/primed spell. Order matters except for Chaos Spear. "Full" means holding the first spell for three seconds; otherwise use an enhanced charge of at least half a second.

To combine with the selector: choose the first spell, hold Cast, use Alt + wheel to select the second while still holding, then release. Alternatively, hold the first spell's quick key and press the second quick key. Selecting an unsupported pair can fail rather than simply firing the original spell.

| Combination | First ingredient | Second ingredient | Base mana | Cooldown | Result |
|---|---|---|---:|---:|---|
| Chaos Spear | Spear or Dark Orb | The other | 2,000 | 1.5s | Homing chaos lance; either order; both forms needed, so normally circle 6 |
| Thunder Mark | **Original Magic Missile** | Thunder | 1,000 | 1s | Projectile stores one teleport mark |
| Thunder Warp | **Full Wall of Wind** | Thunder | 2,000 | 2s | Teleports to your stored mark with lightning at departure/arrival |
| Fire Escape | Wall of Wind, **not full** | **Area Fire** | 1,250 | 1.5s | Backward dash and a fire field excluding the caster |
| Blaze | **Full Wall of Wind** | **Area Fire** | 500 upfront | 4s | Up to 3s flamethrower; additionally drains 500 mana/second |
| Chain Lightning | Thunder, **not full** | Spear | 3,000 | 2s | Chains through up to five targets, with diminishing damage |
| Divine Wrath | **Full Thunder** | Spear | 4,000 | 3s | Tracking sky beam for 3s; requires a target near the aimed point |

Thunder Warp needs a mark in the same dimension, within 10,000 blocks. Selected Space Manipulation makes the warp free. In the current implementation domain containment does not reliably prevent long teleports; this is an audit finding, not an intended escape mechanic.

### The orb-and-spear detonation is a separate interaction

Chaos Spear above is a paired-key combination that creates a lance. To make the large physical detonation:

1. Use the Dark Orb form and cast an **enhanced** orb so it floats.
2. Launch an **enhanced Spear of Light** into that orb.
3. If both were fully charged, the interaction becomes the super "Hollow Purple" cataclysm. Otherwise it uses the normal chaos blast.

The normal blast uses explosion power 10 and effect radius 12. The super version uses power 27 and effect radius 30, with a staged implosion/shockwave and lingering field. The code creates TNT-type explosions, so these spells can damage terrain under applicable game rules. Practice them away from builds you want to keep. Spatial Rend also removes destructible blocks directly.

## Passive chants and arrays

### Passive chants — K menu

Once discovered, select one chant at a time; select No Chant to stop it. Upkeep chants shut down when you cannot pay.

| Chant | Effect | Upkeep |
|---|---|---:|
| Channeling | 2.5× channeling gain; ordinary casts and upfront combination costs become free | None |
| Attack | +30% damage through the shared ordinary damage helper | 200 mana/s |
| Reinforce | +20% movement speed and 30% incoming damage reduction | 200 mana/s |
| Vitality | Regeneration III | 100 mana/s |

Channeling's free-cast effect does not cover array carving, Laws, sustained dome upkeep, or Blaze's continuous drain. Not every fixed-damage combination or vanilla lightning effect inherits Attack/equipment damage bonuses.

### Arrays — choose type with K, carve with C

Tap C and remain still for two seconds. Moving more than about 0.2 blocks cancels the carving. A successful carve costs 1,000 mana and has a five-second cooldown; cancellation gives a three-second cooldown. The array is placed roughly 3–7 blocks ahead.

Arrays have radius 5, last 30 seconds, arm after one second, and can retrigger every two seconds.

| Type | Trigger effect |
|---|---|
| Fire | Burns for 5s |
| Thunder | Calls lightning |
| Wind | Shoves outward |
| Bind | Slowness IV for 3s |

Arrays affect living entities without an owner/faction filter, including the caster, pets, other players, and NPCs. Anti Magic immunity protects against the relevant Fire/Bind effects. Placed arrays and ordinary spell fields are transient, rather than saved structures.

## Items, crafting, wands, and robes

### The crafting chain

There are 13 recipe JSON files. Quantities below are for one craft, with shaped layouts specified where needed.

| Output | Recipe / acquisition |
|---|---|
| Mana Dust | Wandering mage defeat/yield loot; tower loot |
| Mana Vial | Glass bottle + **2 Mana Dust**, shapeless |
| Mana Liquid | **Any vanilla potion item** + Mana Dust, shapeless; no water-only NBT condition |
| Arcane Wood | Mana Liquid + any plank, shapeless |
| Arcane Glue | Mana Liquid + Glowstone Dust + Mana Dust + Bone Meal, shapeless |
| Arcane String | Hold ordinary string in either hand and channel: one conversion/second |
| Arcane Yarn | One horizontal row: Arcane String / Stick / Arcane String |
| Arcane Focus | One vertical column: Mana Crystal / Arcane Yarn / Stick |
| Arcane Station | 2-wide × 3-high: Carpet Carpet / Arcane Wood Arcane Wood / Arcane Wood Arcane Wood |
| Glow Crystal | **2 Glowstone Dust** + Mana Dust + Arcane Glue, shapeless |
| Dragon Crystal | **2 Dragon's Breath + 2 Glow Crystals + 2 Arcane Glue**, shapeless |
| Iron Mage Robe | 8 Arcane Yarn around an Iron Ingot |
| Amethyst Mage Robe | 8 Arcane Yarn around an Amethyst Shard |
| Emerald Mage Robe | 8 Arcane Yarn around an Emerald |
| Diamond Mage Robe | 8 Arcane Yarn around a Diamond |

Mana Dust and infused String are acquisition entries, not additional recipe JSON files. The four robe recipes count separately toward the 13.

A Mana Vial takes about 1.2 seconds to drink and restores up to 500 mana, capped by the current next-circle capacity. It returns a glass bottle. Drinking at full mana still spends the vial. At circle 0, vial use itself does not run the ascension check: channel afterward to advance once the meter is full.

### Focus versus assembled wand

An **Arcane Focus** held in either hand gives ordinary spell cost ×0.85 and supported spell damage ×1.10, and right-click toggles channeling.

An **Arcane Wand** is assembled in the station:

- Slot A: Arcane Glue, required.
- Slot B: material, required — Stick, Bamboo, End Rod, Blaze Rod, Bone, Iron Ingot, or Copper Ingot.
- Slot C: optional core — Glow Crystal, Heart of the Sea, Nether Star, Echo Shard, or Dragon Crystal.
- Slot D: optional tip — Diamond, Amethyst Shard, or Emerald.
- Take the output to consume one item from each occupied component slot. Closing returns remaining input items.

Keep the wand in either hand for its bonuses. If both hands hold wands, the main-hand wand is chosen rather than combining two sets of components.

| Component | Current functional bonus |
|---|---|
| End Rod material | Misc-school cost and cast time ×0.80 |
| Blaze Rod material | Fire-school cost and cast time ×0.80 |
| Other materials | Appearance |
| Glow Crystal core | Held torchlight; implemented using temporary server-side light blocks |
| Heart of the Sea core | Intended water cost ×0.80 and damage ×1.20; **no current spell maps to Water**, so no useful spell bonus yet |
| Nether Star core | Ordinary spell cost/cooldown ×0.80 and supported damage ×1.20 |
| Echo Shard core | Misc cost/cooldown ×0.70 and supported damage ×1.15 |
| Dragon Crystal core | Dormant; no combat bonus yet |
| Crystal tips | Appearance |

Fire = Area Fire and Fireball. Wind = Wall of Wind and Wind Blade. Misc = Slow, Dark Orb, Anti Magic, Thunder, Barrier. Missile, Spear, and Dash fall into Other. Equipment/circle multipliers compose multiplicatively, subject to rounded mana/tick values. Do not expect every Law or fixed-damage combination to receive these ordinary casting bonuses.

### Robes

Each robe is a chestplate with 4 armor points and 300 durability. Its custom projectile mitigation is 30% for Iron/Amethyst, 40% for Emerald, and 50% for Diamond. The mitigation hook is in the Magic Missile projectile, rather than blanket protection from every magical effect. Circle-based innate resistance separately reduces supported fire/raw-magic sources by 5% per circle; vanilla lightning is excluded from that helper.

## Towers and wandering mages

### Towers

The world generator has four templates: intact Mage Tower, two broken variants, and a special underground tower. They generate in 16 eligible Overworld biomes, including plains, forests, taigas, snowy plains, savannas, desert, meadow, and cherry grove. New structures appear in newly generated chunks.

Placement sets use chunk spacing/separation: broken 34/8, intact 48/12, special 68/17. These are placement settings, not a promise to find a tower every fixed number of blocks. The special tower is configured about 21 blocks below its heightmap level.

Entering a structure's bounds triggers its one-time chest population. Intact and special towers receive richer special-item rolls and a tower guardian; broken towers have lighter special loot. Loot includes crafting materials, vials, tomes, crystals, and wand components. Water/Earth Tomes can also roll despite their empty spell lists.

### Ordinary mages

Wandering mages naturally spawn on lit ground in Overworld biomes, with random circles 1–3. Ordinary versions have 24/28/32 HP respectively, use stronger magic with circle, and are neutral until attacked. They greet nearby players, sometimes perform a cosmetic channeling animation, and can unlock a journal encounter entry.

While friendly, right-click them for progress-related hints. Hold an emerald in the main hand and right-click to exchange **one emerald for one Mana Vial**. There is no stock limit or trade cooldown in this version. Guards and aggressive/yielding mages do not offer the normal friendly interaction.

In combat they fire telegraphed missiles, kite around a preferred distance, dodge incoming projectiles, place fire fields at mid range, shove at close range, and use Thunder once below half health. A normal mage that survives damage to 25% HP or below yields, drops one Mana Dust, runs for about 3.5 seconds, and **disappears permanently**. It does not become a recruitable or returning NPC. Killing an ordinary mage drops 1–2 Mana Dust.

Tower guardians use the same base entity with a persistent guard post and proximity aggression. Currently they still inherit roaming behavior and the ordinary yield/disappear behavior. Once one is gone, the tower's one-time initialization does not replace it. This is a current weakness, explained in the [review](NPC_BOSS_REVIEW.md).

### The Prodigy

A naturally initializing circle-3 mage has a one-in-three chance to become the Prodigy while world progression says no Prodigy is alive and none has been defeated: roughly one eligible Prodigy candidate per nine newly initialized mages. The actual encounter rate still depends on spawning.

The Prodigy has 60 HP, an extra damage bonus, faster ordinary cooldowns than normal mages, and does not yield. It shares the wandering-mage combat repertoire rather than a separate multi-phase boss implementation. Defeating it drops 3–4 Mana Dust and a Mana Crystal, and records a world-wide defeat flag. Natural progression stops producing new Prodigies afterward. Administrative spawn commands can bypass this progression.

## The Master: implemented fight, missing natural encounter chain

**Defeating the Prodigy does not automatically spawn the Master in the current source.** A helper describes eligibility, but no natural spawning path calls it. The implemented encounter is reached with the operator command `/bossmagic summonmaster`, which places him about 50 blocks in the horizontal look direction, at the surface, with a locator beam.

He is a 100-HP circle-5 boss. A living survival-mode player approaching within 20 blocks triggers the fight; being seen within 50 and then trying to leave that range can also trigger it. Attacking him is another trigger. Creative/spectator players are excluded from ordinary proximity targeting, so walking up in creative is not a representative test.

### Fight sequence

1. **Engagement:** he raises a spherical domain of radius 64 and shows a boss bar. He orbits the center rather than simply retreating to the wall.
2. **Arcane Barrage:** a red one-second casting telegraph precedes 32 outward missiles. Sidestep their paths and use openings during the committed cast.
3. **Fire Lines:** HP threshold crossings around 80%, 60%, 40%, and 20% schedule a 16-line sweep when he is free to cast. The first sweep moves outward; the next reverses inward; later ones alternate. Watch the telegraphs and his first two "Look behind" lines. Large damage bursts can skip multiple thresholds into one cast.
4. **Meteor phases:** one begins at/below 70% HP and ends at/below 50%; another begins at/below 20%. He levitates and becomes much more resistant to supported magic, from about 35% grounded to 85% airborne during his circle-5 phase. Move out of the red ground warnings. Ordinary melee/lightning are not reduced by the same custom resistance helper.
5. **First Barrier attempt:** he queues three retaliation missiles with a two-second delay and suppresses further Barriers for that fight. Current code reacts to any Barrier attempt, including a wall, and does so before mana/knowledge/cooldown validation. Even a failed cast can provoke it; this should be corrected in a future edit.
6. **Lethal hit:** ordinary lethal damage leaves him at 1 HP, ascends him to circle 6, and starts a **60-second survival countdown**. Continued normal damage cannot finish him during this sequence.
7. **Finale:** after the countdown he telegraphs a cataclysm for about two seconds, then performs a reduced ground Hollow Purple and dies. Collect the **Fragment of Law** afterward. His defeat is recorded world-wide.

If no eligible living target remains, the usual encounter path removes the domain and resets him to a fresh neutral boss. Reload/chunk-unload handling has gaps: do not assume every pending attack resumes exactly as before. The source review details those cases; they were not playtested.

The Fragment of Law currently glints and has a tooltip, but has **no use interaction, recipe, or Laws-discovery integration**. A Lich is mentioned in world progression and journal infrastructure; there is no registered Lich entity or fight in this source.

## Laws — circle 9

Open L, select one unlocked Law, and use B for the active-key Laws. Other Laws apply passively, react to events, or have their own UI/command.

Discovering base Laws grants **Creation** and **Love of the World**. Taking void damage records comprehension for the three Space Laws. Five actual deaths unlock Life after base discovery. Causality requires being struck by every type in the fixed list below. Gravity Amplification, Black Hole, Time Stop, and Time Reversal currently have **no natural unlock path**; the operator unlock-all command exposes them.

| Law | How to use | Mana | Current effect |
|---|---|---:|---|
| Life | Selected; triggers on otherwise lethal damage | 100,000 per revival; 1,000,000 in hardcore | Cancels death, restores health, clears statuses/fire in place; requires sufficient mana |
| Spatial Rend | B | 10,000 | Fast, tall space-cutting projectile, up to 75 blocks; removes destructible blocks and applies extreme damage |
| Space Manipulation | Selected; use Thunder Warp | Warp becomes free | Removes Thunder Warp mana cost |
| Storage | Open Storage in L menu | No listed casting cost | 27-slot personal storage |
| Gravity Amplification | Selected | 2,000/s while affecting entities | Downward force, slowing, and crushing within about 10 blocks, stronger nearby |
| Black Hole | Selected | 250/s | Pulls loose item entities from within 30 blocks; not the old block-destroying version described in older notes |
| Time Stop | B | 10,000 | About 5s of velocity freezing and mob AI disabling in a 200-block expanded box; other players are excluded |
| Time Reversal | B | 2,000 | Restores full health; does not roll back terrain/inventory |
| Creation | `/lawcreate <item> [count]` | 2,000 per item | Creates a registry item; count 1–64; requires this Law selected at circle 9, not operator privileges |
| Causality | Selected; reacts to damage | 1,000 per incoming heart from living attacker; 3,000 per environmental heart | Cancels/reflects damage when payable |
| Love of the World | Selected | 4,000/s | Water breathing, night vision, fire resistance, haste, speed, Regeneration I, and fall-damage negation |

Causality tracks these 27 types: Zombie, Husk, Drowned, Skeleton, Stray, Wither Skeleton, Creeper, Spider, Cave Spider, Enderman, Witch, Slime, Silverfish, Endermite, Phantom, Pillager, Vindicator, Evoker, Vex, Ravager, Blaze, Ghast, Magma Cube, Hoglin, Zoglin, Piglin Brute, Guardian. Its source tracks attacker entity types, so indirect attacks should be tested before assuming every apparent hit credits the checklist.

Time Stop's cleanup currently relies on a transient list of numeric entity IDs. Disconnecting, dying, or changing dimensions during a stop could leave AI disabled; this is a source-level risk awaiting a playtest. Avoid relying on its lifecycle behavior until that issue is addressed.

## Optional operator reference

These commands change the world or your character. They are printed for your own use and were **not run** during the review. Most require cheats/operator permission level 2.

| Command | Purpose |
|---|---|
| `/circlemage addmana <amount>` | Add mana |
| `/circlemage setmana <amount>` | Set mana |
| `/circlemage setcircle <0-9>` | Set circle directly; does not mean every spell/discovery is learned |
| `/circlemage manamultiplier <value>` | Debug channeling multiplier |
| `/circlemage cooldowns <true/false>` | Enable/disable ordinary debug-controlled cooldown gate |
| `/circlemage unlocklaws` | Discover base Laws and evaluate earned conditions |
| `/circlemage unlockalllaws` | Unlock all Laws, including command-only ones |
| `/circlemage spawnmage <1-3>` | Spawn an ordinary mage |
| `/circlemage spawnprodigy` | Spawn a Prodigy, bypassing the natural uniqueness gate |
| `/bossmagic summonmaster` | Summon the implemented Master fight |
| `/bossmagic domain` | Create a player-owned domain; actual radius 50, despite its radius-100 feedback text |
| `/bossmagic removedomain` | Remove your player-owned domain; not a general all-Master-domain cleanup |
| `/bossmagic meteorrain` | Toggle meteor rain for your domain |
| `/bossmagic meteorfrequency magicmissile <rate>` | Adjust missile meteor frequency |
| `/bossmagic meteorfrequency fireball <rate>` | Adjust fireball meteor frequency |
| `/lawcreate minecraft:diamond 1` | Normal Creation-Law example; only with eligible selected Law and mana |

`/bossmagic restrictmagic` accepts `disable`, `magicmissile`, `fireball`, `windwall`, `thunder`, or `dash`. Each selection replaces the current restricted set. The historical `fireball` option restricts Area Fire, not the newer projectile Fireball. These are development arena tools.

## What is unfinished or misleading in this snapshot

- Water/Earth schools have tomes but no spells; the water wand core cannot buff a current school assignment.
- Dragon Crystal is a craftable dormant core; Fragment of Law is a reward with no gameplay use yet.
- The Prodigy → Master natural encounter transition is absent; the Lich fight is absent.
- Several Laws exist but are command-only unlocks.
- Circle 9's ten-billion threshold creates extreme progression time, and missing earlier realm buffs worsens it.
- Tower guards share unsuitable roaming/yield behavior; some NPC/boss state is lost on reload.
- Root README/HANDOFF contain older mechanics. Prefer this guide, current enums, and live item feedback when they conflict.

## Source anchors

The main references are [CircleMageMod](../src/main/java/com/example/circlemage/CircleMageMod.java), [ManaData](../src/main/java/com/example/circlemage/mana/ManaData.java), [Circles](../src/main/java/com/example/circlemage/mana/Circles.java), [Spell](../src/main/java/com/example/circlemage/spell/Spell.java), [ModKeybinds](../src/main/java/com/example/circlemage/client/ModKeybinds.java), [Combinations](../src/main/java/com/example/circlemage/spell/Combinations.java), [WandItem](../src/main/java/com/example/circlemage/item/WandItem.java), [LawUnlocks](../src/main/java/com/example/circlemage/spell/LawUnlocks.java), [MageTowers](../src/main/java/com/example/circlemage/world/MageTowers.java), [WanderingMageEntity](../src/main/java/com/example/circlemage/entity/WanderingMageEntity.java), [MasterEntity](../src/main/java/com/example/circlemage/entity/MasterEntity.java), and the [13 recipe files](../src/main/resources/data/circlemage/recipes).
