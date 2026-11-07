# Circle Mage V2 player guide

Current port: Minecraft Java **26.3**, Fabric Loader **0.19.5+**, Fabric API **0.162.0+26.3**, Java **25**. Gameplay and progression below are retained by the port. See [upgrade notes](UPGRADE_26_3.md) for migration and testing.

## A fresh start

You begin at circle 0 with **Magic Missile learned and equipped**. Absorb the tutorial book if it appears, open J, and channel with M to gather your first 100 mana. Reach circle 1 before using T1 magic. Channel with an empty hand or an item that cannot be infused when you want to accumulate mana.

The mana bar serves as both your spell resource and circle progress. Casting spends that mana. Ascension consumes the required mana and resets the bar. The original circle thresholds are retained:

| Circle reached | Mana needed from previous circle | Additional condition |
|---|---:|---|
| 1 | 100 | None |
| 2 | 5,000 | None |
| 3 | 20,000 | None |
| 4 | 50,000 | Learn Dispel after a guardian epiphany |
| 5 | 100,000 | None |
| 6 | 200,000 | Original Combination Magic advancement |
| 7 | 400,000 | None |
| 8 | 700,000 | None |
| 9 | 10,000,000,000 | Original Laws discovery |

Channeling generates 5 mana/second at circle 0, then 15/second from circle 1 before realm and chant multipliers. Natural flow is `circle + 5 × attuned Mana Crystals` per second, beginning at circle 1. Attune up to three crystals. Permanent realm insights can still be gained while casting; each doubles channeling gain, and the original missile benefits from realm damage boosts.

## Learn, equip, select, cast

Use a Fire, Water, Earth, Wind or Misc Tome to learn one unknown spell in that school at a tier your circle supports. A survival player consumes one tome on success. If everything available at your circle is already learned, the tome stays in your hand for later use.

Open **J** and click a node. The journal shows its name, tier, effect, base mana price and cooldown. **Attune / bind** equips a T1/T2 spell; equipping a T1 replaces your previous T1. T2 has three slots, and an inactive bind button explains when they are full. Click **Unbind** or right-click its node to free a slot. **Select for R** makes an available spell your current cast.

Learned spells remain learned when unbound. At every circle the normal loadout allows **one T1 and three T2**; T3/T4 have no slot cap. A spell's tier must be no higher than your circle. Optional quick keys use exactly the same server rules. `/breakmagiclimit` permanently lifts the T1/T2 count limits for the executing player; it requires cheats/op level 2 and still respects circle, knowledge, mana and cooldown.

| Key | Action |
|---|---|
| J | Magic tree and notes; J is reserved from conflicting bindings at client startup |
| M | Toggle mana channeling |
| R | Cast selected magic; hold for 0.75 seconds if Dispel is selected |
| Left Alt + wheel | Cycle equipped/available spells |
| G | Hold 0.75 seconds to cast Dispel |
| K | Chants and array selection |
| C | Carve selected array, once discovered |
| L | Choose a Law, once discovered |
| B | Activate applicable Law |
| Controls → Circle Mage | Assign optional spell shortcuts; default is unbound |

## Spell reference

One heart is 2 HP. Prices and damage below are base values. Existing focus, wand and circle-5 discounts can reduce costs/cooldowns; target defenses can reduce ordinary damage. Magic Missile retains its original realm/equipment damage scaling. Newly specified damage amounts use the requested fixed base values.

| Tier | Spell / school | Mana | Cooldown | Effect |
|---|---|---:|---:|---|
| T1 | Magic Missile / Misc | 50 | 1s | Original homing light bolt; 4 HP base |
| T1 | Fireball / Fire | 35 | 1.25s | 1 HP impact and 5 seconds of ignition |
| T1 | Ice Volley / Water | 70 | 3s | Three ice spikes, 1 HP each; +5% slow per hit, 4 seconds, capped at 30% |
| T1 | Stone Spike / Earth | 65 | 2.75s | Ground eruption, 3 HP, with a short rising telegraph |
| T1 | Wind Blades / Wind | 60 | 2.5s | Three blades across a brief volley, 1 HP each, slight knockback |
| T2 | Stone Fist / Earth | 160 | 4s | Ground fist, 5 HP and knockback |
| T2 | Stone Dome / Earth | 300 | 10s | Radius-3 stone cage lasting 6 seconds; retains occupants and blocks projectiles/sight |
| T2 | Wind Wall / Wind | 200 | 3s | Original frontal pushing gust |
| T2 | Wind Escape / Wind | 100 | 2s | Original movement burst; follows held movement direction, otherwise forward |
| T2 | Water Bubble / Water | 240 | 8s | Head bubble suffocates for 4 seconds: four 1.5 HP pulses, 6 HP total |
| T2 | Vine Grab / Misc | 180 | 7s | Roots an aimed living target for 3 seconds |
| T2 | Area Fire / Fire | 300 | 7s | Original burning field, radius 3, lasting 5 seconds |
| T3 | Soothing Water / Water | 450 | 40s | 30 seconds of double natural mana flow, fire immunity and Steam immunity |
| T3 | Mark / Misc | 120 | 10s | Aimed living target glows for 30 seconds |
| T3 | Flame Rebirth / Fire | 250 | 20s | Pay exactly 3 HP; cleanse harmful effects, fire, nearby cobwebs and your water bubble |
| T4 | Dispel / Anti Magic / Misc | 750 | 10s | Original cleansing and 5-second harmful-effect immunity, extended to new spell volumes |
| T3 combination | Steam Explosion / Fire + Water | 600 | 30s | One-second water/fire formation, followed by a radius-6 cloud for 30 seconds |

Targeted new spells reach up to 32 blocks with a 2-block aiming tolerance; terrain stops the aiming ray. Earth attacks need a solid floor within 6 blocks below the target/cursor. Look down more than 65° to place Stone Dome around yourself. The dome uses a temporary spell entity and disappears without modifying blocks.

Flame Rebirth refuses to cast at 3 HP or less. It removes harmful effects while retaining beneficial effects, and removes only nearby cobwebs. Vine Grab prevents Wind Escape while its root lasts. Water Bubble can be removed with Flame Rebirth or Dispel; its expiry restores air.

## Breaking the fourth circle

Find an **intact or special mage tower**. Its guardian is a persistent circle 1–3 Wandering Mage. Learn Fireball from a Fire Tome, equip it in J, and ignite the guardian. The guardian casts Dispel when burning, at most once every eight seconds.

Witness that cleansing within 32 blocks with line of sight to gain the chat message: **“Epiphany! You gained insights into anti magic and how to break the 4th circle.”** Each successful spell use afterward has a **5% chance** to learn Dispel and earn its advancement. Rolls are per cast, not per projectile/hit, and failed casts grant no roll. You can learn it before circle 4, but using the T4 spell requires reaching circle 4. With Dispel learned and 50,000 mana, the fourth circle opens.

## Steam Explosion

Learn **Fireball and Ice Volley**, then reach circle 3. Their knowledge unlocks the Steam recipe automatically; you do not have to equip both T1 ingredients. Click the Steam recipe at the bottom of the left journal page, select it for R, and cast.

A water sphere forms about three blocks ahead, fire surrounds it, and it bursts into a **12-block diameter sphere**. The forming sphere has radius 2. The cloud lasts 30 seconds after its one-second formation and deals 1 HP every two seconds to living occupants, including its caster. NPCs lose sight and their target lock through the cloud. Inside it, vision becomes thick pale fog.

Cast Soothing Water **before** entering the cloud; it cannot be cast inside active Steam. It protects against the damage and fog for 30 seconds. Watch the timing: the formation second is separate from the cloud's lifetime. Dispel can clear a Steam cloud touching you. Existing airborne projectiles and area attacks can still enter the cloud even though NPCs cannot acquire or aim at hidden targets.

## Retained items, crafting and world systems

Channel while holding **String** to infuse one item per second into Arcane String instead of gaining the normal channeling mana. The original recipes retain Arcane Yarn, four Mage's Robes, Mana Liquid, Mana Vials, Arcane Wood, Arcane Glue, Arcane Focus, Arcane Station, Glow Crystal and Dragon Crystal. A Mana Vial restores up to 500 mana. Robes retain their original magic resistance. Hold an Arcane Focus for its spell-cost discount and original missile boost; right-click it to toggle channeling.

An Arcane Station assembles a wand from **A: Arcane Glue** and **B: material** (required), plus **C: core** and **D: crystal tip** (optional). Materials include stick, bamboo, end rod, blaze rod, bone, iron and copper. Cores include Glow Crystal, Heart of the Sea, Nether Star, Echo Shard and Dragon Crystal; tips include diamond, amethyst and emerald. The item's tooltip describes its modifiers. School affinity now follows the new elemental catalog, including Water and Earth.

All four tower templates, their loot pools, Wandering Mages, the Prodigy and the Master are imported. Intact/special towers have guardians; broken towers retain their different chest loot. Newly generated towers no longer place the large exterior air margins. Existing already-carved holes in older saves are not repaired by this change.

The original later systems remain: Magic Arrays (Fire, Thunder, Wind, Bind), passive chants (Channeling, Attack, Reinforce, Vitality), and circle-9 Laws. Arrays can be discovered through casting from circle 3, chants from circle 4, and the original Combination advancement from circle 5. K selects chants/arrays; C carves arrays. L selects a Law and B activates the applicable ability. These imported systems and bosses have not received a complete balance redesign in this alpha. Legacy player spell forms/combinations outside the new tree cannot be cast through player packets.

For quick isolated testing with cheats, use `/circlemage setcircle 3`, `/circlemage addmana 10000`, `/give @s circlemage:tome_fire`, `/give @s circlemage:tome_water`, `/breakmagiclimit`, and `/circlemage cooldowns false`. Admin NPC commands retain `/circlemage spawnmage 1`, `/circlemage spawnprodigy`, and `/bossmagic summonmaster`.
