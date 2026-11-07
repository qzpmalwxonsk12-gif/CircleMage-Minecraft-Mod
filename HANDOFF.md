# Circle Mage — Developer Handoff

> A Fabric mod for **Minecraft 1.20.1** that adds a self-contained progression magic system:
> channel mana, ascend the nine circles of magehood, unlock spells/arrays/chants, weave combination
> magic, and finally wield reality-bending **Laws**.

This document is written for a developer (human or AI) with **zero prior context**. It describes the
entire project, how it is built, where everything lives, and how to continue development.

---

## 1. Project overview & goals

**Circle Mage** is a single-player-focused (works in MP too) progression mod. There are **no custom
items or blocks** — all magic is driven by a per-player **mana** resource and keybinds. The player:

1. **Channels** mana (stands still, mana fills toward a threshold).
2. **Ascends circles** (1→9) by accumulating enough channelled mana.
3. **Casts spells** with keybinds, spending mana.
4. **Randomly unlocks** systems by *using magic* (realm buffs, magic arrays, passive chants,
   combination magic, anti-magic).
5. At the **5th circle** gains **active chant** (a hold/charge skill layer) and spell transforms.
6. At the **5th–9th circles** unlocks **combination magic** and finally the **Laws** (the endgame).

Design pillars: *grind-with-purpose progression*, *random unlocks that reward continued play*, a
*skill layer* (active chant charging + combinations), and *spectacle* (heavily custom particles,
renderers, screen shake, an impact-frame, and a Gojo-style "Hollow Purple" nuke).

All custom textures are **procedurally generated** (no hand-drawn art); see §10.

---

## 2. Current development status

- **Version:** `1.0.8` (see `gradle.properties` → `mod_version`). Date of this handoff: 2026-06-21.
- **State:** Feature-complete through the "Laws" system. Compiles and **`./gradlew build` passes
  cleanly offline**. The mod has **not been runtime-tested in-game by the author** (the dev
  environment is headless) — see §13 for the specific pieces that are compile-verified but not
  runtime-verified.
- **Deploy convention:** every update bumps the patch version and a freshly-named jar
  `circlemage-<version>.jar` is copied to the user's Desktop
  (`C:\Users\Arjun\OneDrive\Desktop`). Keep filenames versioned; don't overwrite a generic name.

### Version history (what each release added)
| Ver | Summary |
|-----|---------|
| 1.0.0 | Base mod (mana, circles, 7 spells, arrays, chants, realm buffs) — pre-existing. |
| (early) | Custom particle/renderer/texture revamp; fixed the Vitality regen bug (was Regen V re-applied every tick → never healed; now Regen III applied only when missing). |
| 1.0.1–1.0.4 | Spear of Light (5th) + Dark Orb (6th) transforms, priming/charge system, Combination-Magic advancement gate, Hollow Purple super-nuke with 5-phase VFX, screen shake, impact frame. |
| 1.0.5 / .1 | Seven combination spells + framework; switched active-chant modifier from sneak to **right-click**; fixed Fire Escape self-burn, Chain Lightning targeting, Chaos Spear explosion; Thunder Mark/Warp range 10 000; floating Dark Orb range 50 + hovers above blocks. |
| 1.0.6 / .1 | **Laws** (9th-circle ultimate system); 9th-circle mana cap → 10 billion; consolidated UI into two screens (Magic menu `K`, Laws menu `L`). |
| 1.0.7 | Per-law unlock conditions + advancements; fixed Blaze (flamethrower); Black Hole now only vacuums dropped items; hardcore Law-of-Life "give up?" screen + 10× cost; re-added the inventory rank line. |
| 1.0.8 | **Dash** movement skill (player-chosen, unbound by default); **Wandering Mage** NPC (Circle 1–3 roaming caster) + **The Prodigy** elite variant with a world-unique spawn rule and the `PRODIGY_DEFEATED` progression flag (future-Master framework); spawn commands; **realm damage multiplier reduced globally 1.5×→1.2×** per buff. See §17. |
| 1.0.8.1 | Dash now goes toward the held movement key (DASH packet carries input) and is 50 % weaker; mage cooldowns 1.25× (Prodigy 1.0×, its cd buff removed), mage dash cd 1.25× for all; mages walk only (no sprint); super (fully-charged) chaos blast +1.5× explosion & damage; **Mana Crystal** item (rare chest loot ~1/village, ×2 mana gain on use up to 3) — The Prodigy drops one. |
| 1.0.8.2 | Fixed dash strafe direction — now uses Minecraft's movement-input→velocity transform so A/D dash left/right correctly (was inverted). |
| 1.0.8.3 | Vitality chant reworked: Regeneration III, **no bonus hearts**, **100 mana/sec upkeep**. Wandering Mage **Magic Missile cast-commit**: 0.25 s frozen wind-up (no move/dash/other spell) with a **locked, translucent-red telegraph beam** (prediction + horizontal inaccuracy baked in; fired straight, no homing). **Diminishing Thunder**: each consecutive lightning hit on a mage deals 20% less, reset by any other non-fire hit. Telegraph/cast-delay are NPC-only. |
| 1.0.8.4 | Magic Missile aiming fixed: prediction now leads **lateral (x/z) movement only** and aims at the target's **current height** (no vertical lead → no sky/ground misses). Inaccuracy is **horizontal-only**: 50% of shots perfect, 50% deviate **±20° in yaw**. Telegraph still reflects the post-deviation path. |
| 1.0.8.5 | Tightened Magic Missile horizontal deviation from ±20° to **±5°**. |
| 1.0.9 | **Mage Journal** (key **J**): a full-screen progression guide with five tabs — Circle Progression, Advancements, Magic Encyclopedia, Major Encounters, and the Mage's Journey questline — plus a contextual hint system. Backed by a new `JOURNAL_SYNC`/`REQUEST_JOURNAL` packet pair + `ClientJournalState`; persists via existing per-player `ManaData` (`encounteredWanderingMage`) and world `WorldProgressState` (`masterDefeated`/`lichDefeated` added as future-boss infrastructure). |
| 1.0.10 | **Boss Magic** (boss/event-only, command-driven via `/bossmagic`): a reusable **Boss Zone** framework (`boss/` package) plus three abilities — **Domain Barrier** (100-block containment sphere; UUID membership pushes back walking/knockback/projectiles/teleports/dashes), **Meteor Rain** (telegraphed red-circle impacts: frequent Magic-Missile bursts + rarer Fireball blasts that leave temporary fire), and a configurable **Magic Restriction Field** (per-spell suppression inside the zone). Not learnable/castable by players. |
| 1.0.10.1 | Domain barrier **50** blocks (was 100); restriction now suppresses **one** chosen spell (`/bossmagic restrictmagic <magicmissile\|fireball\|windwall\|thunder\|dash\|disable>`); Meteor Rain base rate **×3** + `/bossmagic meteorfrequency <rate>`. Player **Thunder now aggroes** nearby mobs (marks the player as attacker). **Mana infusion** framework (channel while holding an infusable item → converts 1/sec instead of gaining mana): **String → Arcane String** (glowing). New craftables: **Arcane Yarn** (arcane string + stick + arcane string) and **Mage's Robes** (8 arcane yarn around iron/amethyst/emerald/diamond) — chestplate-slot armor that cuts Magic Missile damage by 30/30/40/50%. Channelling **FOV zoom halved** (`ClientPlayerFovMixin`). |
| 1.0.10.2 | **Boss Zones now persist** across world reloads (`BossZoneState` PersistentState on the Overworld; loaded on `SERVER_STARTED`, cleared on `SERVER_STOPPED`, `markDirty` on every change) — fixes command effects resetting on rejoin. Meteor frequency **split per type**: `/bossmagic meteorfrequency magicmissile\|fireball <rate>`; base rates **×50 missile / ×30 fireball**, cap raised **20→1000**. MeteorRain now spawns multiple impacts/tick (so high rates actually manifest) with bigger concurrency caps + throttled telegraphs. |
| 1.0.11 | **The Master** boss (`MasterEntity`, 100 HP, 5th→6th circle). Summon with `/bossmagic summonmaster` (50 blocks away + locator beam). Neutral until escape/approach/attack triggers → raises a Domain Barrier (Boss Zone) + boss bar. Abilities: **Arcane Barrage** (16 straight missiles after a 1s telegraphed wind-up), two **Meteor Rain phases** (70→50% & 20→0% HP, hovers at center, 80% damage reduction, 3s dodge-dash), one-time **Barrier Punishment**. At 0 HP: **Desperation Ascension** (6th circle, 60s countdown) → cinematic ground **Hollow Purple cataclysm** → death, drops **Fragment of Law**, sets `masterDefeated` (+ journal hints foreshadowing Laws & Combination Magic). |
| 1.0.11.1 | New boss ability **Wind Blade Volley** (`WindBladeEntity` — fast gale projectile w/ knockback; 5 blades ~100 ms apart). Arcane Barrage **16→32** missiles. Boss encounter now **ends when the player dies** (`endEncounter` tears down the domain/bar and resets the Master to a fresh neutral boss). The Master's finale Chaos Blast is **half** the player's super-nuke (`DarkOrbEntity.triggerGroundCataclysm(scale)` — power/radius/damage ×0.5; the player's stays full). |
| 1.0.12 | **Wind Blade is now a player spell** (`Spell.WIND_BLADE`, unbound key, 150 mana / 3 s cd) firing a 5-blade volley; each blade deals **1/3 of a Magic Missile's** damage — removed from The Master. New shared **innate magic resistance** stat (`spell/MagicResistance`): **+5%/circle** vs fire & raw magic, applied to the **player** (`LivingEntityMixin.modifyAppliedDamage`), **Wandering Mages** and **The Master**. The Master: 25% (5th circle) **+10% natural** = 35% grounded, **+50% while levitating** (was 80%) = **85%** during meteor phases; **meteor frequency doubled** (100×/60×); Barrier Punishment's 3 missiles now land **2 s later**. New ability **Fire Line** (every 20% HP lost): 1 s telegraph, **16 equally-spaced fire lines** boss→barrier with one aimed at the player; cast 1 forward + "Look behind", cast 2 **reverse** (barrier→boss) + "Look behind", then alternates silently. Movement reworked to **kite around the domain centre** (orbits the side away from the player) so it no longer wall-pins. See §22. |

---

## 3. Dependencies & setup

From `gradle.properties` / `build.gradle`:

- **Minecraft** `1.20.1`
- **Yarn mappings** `1.20.1+build.10` (`v2`)
- **Fabric Loader** `0.15.11` (mod requires `>=0.15.0`)
- **Fabric API** `0.92.2+1.20.1`
- **Java** 17 (`options.release = 17`)
- **Fabric Loom** `1.6-SNAPSHOT`
- `maven_group = com.example.circlemage`, `archives_base_name = circlemage`

### Build / run
```bash
./gradlew build            # full build → build/libs/circlemage-<version>.jar
./gradlew build --offline  # works offline; deps are already cached in this environment
./gradlew compileJava --offline -q   # fast compile check
./gradlew runClient        # launch a dev client (needs a display)
```
> The environment here has the Gradle/Loom caches populated, so **`--offline` works**. There is **no
> network**; do not add dependencies that need downloading.

### Regenerating textures
```bash
powershell -ExecutionPolicy Bypass -File tools/gen_textures.ps1
```
This rewrites every PNG under `src/main/resources/assets/circlemage/textures/{particle,entity,gui}`
using GDI+ (Windows `System.Drawing`). Tweak the parameters at the top of the script and re-run.
**Do not hand-edit the PNGs** — they'll be overwritten.

---

## 4. Folder structure & file organization

Base package: `com.example.circlemage`. Mod id: **`circlemage`** (`CircleMageMod.MOD_ID`).

```
src/main/java/com/example/circlemage/
├─ CircleMageMod.java          # MAIN entrypoint. Server tick, all spell casts, laws ticking,
│                              #   packet receivers, event registration. THE big file (~1000 lines).
├─ command/ModCommands.java    # /circlemage … (op) and /lawcreate (player, Law of Creation)
├─ mana/
│  ├─ ManaData.java            # ALL per-player state (persisted + transient). The "capability".
│  ├─ ManaPlayer.java          # interface + static get(player) — duck-typed accessor (see §8)
│  ├─ Circles.java             # circle thresholds (long[]) + gate helpers
│  └─ StorageInventory.java    # 27-slot SimpleInventory backed by ManaData (Law of Storage)
├─ spell/
│  ├─ Spell.java               # enum of castable spells (id, manaCost, cooldown, castTime)
│  ├─ ArrayType.java           # FIRE/THUNDER/WIND/BIND (magic arrays)
│  ├─ PassiveChant.java        # NONE/CHANNELING/ATTACK/REINFORCE/VITALITY
│  ├─ Law.java                 # enum of the 12 Laws (incl. NONE) + category
│  ├─ LawUnlocks.java          # unlock logic, conditions, per-law advancements, hostile set
│  ├─ CombinationSpell.java    # abstract framework for combination spells
│  ├─ Combinations.java        # the 7 combination spells + dispatcher (find/matches)
│  ├─ AreaEffect.java          # lingering ground field (FIRE/SLOW, +poison, +owner-exclusion)
│  ├─ AreaEffectManager.java   # ticks all active AreaEffects (transient list)
│  ├─ MagicArray.java          # a carved rune circle that re-triggers
│  └─ MagicArrayManager.java   # ticks all active MagicArrays (transient list)
├─ entity/
│  ├─ ModEntities.java         # registers MAGIC_MISSILE, MAGIC_BARRIER, DARK_ORB, SPATIAL_REND
│  ├─ MagicMissileEntity.java  # Magic Missile + Spear of Light + Chaos Spear + Thunder-Mark bolt
│  ├─ MagicBarrierEntity.java  # wall + dome barrier
│  ├─ DarkOrbEntity.java       # Dark Orb / Hollow Purple (5-phase detonation)
│  └─ SpatialRendEntity.java   # Law of Space: the 1×10×75 space blade
├─ particle/ModParticles.java  # registers all custom DefaultParticleTypes (common)
├─ network/ModNetworking.java  # all packet Identifiers + S2C send helpers
├─ mixin/
│  ├─ PlayerEntityMixin.java   # attaches ManaData to every player + NBT read/write
│  ├─ LivingEntityMixin.java   # anti-magic immunity, dome absorb, Reinforce reduction,
│  │                           #   Causality reflect, feather-fall, law-unlock tracking
│  ├─ CameraMixin.java         # (client) camera shake
│  └─ GameRendererAccessor.java# (client) @Invoker for the desaturate post-processor
└─ client/
   ├─ CircleMageClient.java    # client entrypoint: renderers, particle factories, packet receivers,
   │                           #   HUD, inventory rank line, packet senders
   ├─ ModKeybinds.java         # ALL keybinds + the cast/prime/combine client logic
   ├─ ClientManaState.java     # client mirror of synced ManaData fields
   ├─ ManaHudOverlay.java      # bottom mana bar (custom textures)
   ├─ MagicScreen.java         # "Magehood" page (forms / chants / arrays) — opened with K
   ├─ LawSelectionScreen.java  # "Laws" page — opened with L
   ├─ GiveUpScreen.java        # hardcore Law-of-Life flavor screen
   ├─ MagicGlowParticle.java   # the one configurable billboard particle class (Settings record)
   ├─ MagicMissileRenderer.java# orb (Magic Missile) / lance (Spear) renderer
   ├─ MagicBarrierRenderer.java# wall panel + dome shield geometry
   ├─ DarkOrbRenderer.java     # void orb + absorb swirl
   ├─ SpatialRendRenderer.java # tall space-textured blade
   ├─ ClientShakeState.java    # camera-shake fade state
   └─ ClientImpactState.java   # impact-frame (white flash + desaturate shader) state

src/main/resources/
├─ fabric.mod.json             # entrypoints (main + client), mixins config
├─ circlemage.mixins.json      # mixins:[PlayerEntityMixin, LivingEntityMixin]
│                              #   client:[CameraMixin, GameRendererAccessor]
├─ assets/circlemage/
│  ├─ lang/en_us.json          # keybind + entity names
│  ├─ particles/*.json         # one per registered particle type → texture mapping
│  ├─ textures/{particle,entity,gui}/*.png   # all procedurally generated
│  └─ shaders/post/desaturate.json           # impact-frame post chain
├─ assets/minecraft/shaders/program/circlemage_gray.{json,fsh}  # grayscale program (must live in
│                              #   the minecraft namespace; see §13 shader note)
└─ data/circlemage/advancements/*.json       # impossible-trigger advancements (granted in code)

tools/gen_textures.ps1         # texture generator (GDI+)
HANDOFF.md                     # this file
```

---

## 5. Magic progression — circles & mana

### Channelling & circles
- Press **Channel (M)** to toggle channelling. While channelling the player is **frozen in place**
  (a `-100%` movement-speed attribute modifier) and mana fills.
- Mana per second (in `CircleMageMod.onServerTick`):
  `BASE_GAIN_PER_SECOND(5) × manaMultiplier × debugMultiplier × channelingChant × circleGain`
  - `manaMultiplier = 2^(realm buffs)` — each unlocked realm buff doubles mana gain.
  - `channelingChant = 2.5` if the Channeling passive chant is active, else 1.
  - `circleGain = 3.0` once the player is **circle ≥ 1** (the "3× mana after circle 1" rule), else 1.
- **Circle thresholds** (`Circles.THRESHOLDS`, a `long[]`, index = circle reached):
  `0, 100, 5 000, 20 000, 50 000, 100 000, 200 000, 400 000, 700 000, 10 000 000 000`
  - `MAX_CIRCLE = 9`. The 9th circle costs **10 billion** mana (stored in a `double`; HUD shows
    compacted values like `10.0B`).
  - On ascending, current mana resets to 0. At the max circle, mana is capped at the final threshold.
- **Advancement gates** (`CircleMageMod.advancementGate`): you cannot ascend *to* a gated circle
  without its advancement, even with enough mana (progress caps at the threshold):
  - Circle **4** → `basic_anti_magic`
  - Circle **6** → `combination_magic`
  - Circle **9** → `laws`

### Random "magic use" rolls (`doMagicUseRolls`, on every successful cast/combine)
| Reward | From circle | Chance per use |
|--------|-------------|----------------|
| Basic Anti Magic advancement | 3 | 0.5% (`ANTI_MAGIC_CHANCE`) |
| Realm buff for current circle | 1 | 5% (`REALM_BUFF_CHANCE`) |
| Magic Arrays unlock | 3 | 1% (`ARRAY_UNLOCK_CHANCE`) |
| Passive Chants unlock | 4 | 5% (`CHANT_UNLOCK_CHANCE`) |
| Combination Magic advancement | 5 | 2% (`COMBO_ADV_CHANCE`) |

**Realm buffs:** each circle (1..9) can unlock its buff once; each unlocked buff grants **2× mana
gain and ×1.2 magic damage** (`damageMultiplier = 1.2^count`, the constant
`ManaData.REALM_DAMAGE_PER_BUFF`; reduced globally from 1.5 in 1.0.8). They stack multiplicatively.

**Laws discovery** is a separate **0.01% per second** passive roll while playing
(`LAWS_UNLOCK_CHANCE = 0.0001`, checked every 20 ticks) — see §9.

**5th-circle perk** (`circleDiscount` = 0.85): all spell mana costs and cooldowns are ×0.85.

---

## 6. Spells

Defined in `spell/Spell.java` as `(id, manaCost, cooldownTicks, castTimeTicks)`. Cast handling is in
`CircleMageMod.handleCast` → `performSpell`. "Enhanced" = cast under an active chant (see §7).
"Fully charged" = primed to the 3-second cap.

| Spell | Key | Cost | CD | Behaviour | Enhanced / Fully-charged |
|-------|-----|------|----|-----------|--------------------------|
| Magic Missile | R | 50 | 1s | Gravity-free homing bolt, 2 hearts × damageMult × attackChant. Locks onto an entity near the cursor. | enhanced ×1.5 damage |
| Fireball | T | 300 | 7s | Lingering 3-radius fire field, 5s. | enhanced: 5-radius + burn damage/s |
| Area Slow | V | 500 | 5s | 10-radius Slowness field, 8s (Slowness I). | enhanced: Slowness II |
| Anti Magic | G (hold 0.75s) | 750 | 10s | Cleanses negative effects + fire, clears overlapping fields, 5s immunity. **Requires `basic_anti_magic`.** | — |
| Wall of Wind | Y | 200 | 3s | Directional knockback wall (range 10, width 5). | enhanced +50% knockback; **fully charged: omnidirectional cyclone** |
| Thunder | H | 500 | 5s | Lightning bolt at cursor. | enhanced: 3 bolts; **fully charged: 6 bolts** |
| Barrier | Z | 750 | 8s | **Wall** (5×5) normally; **dome** while sneaking (sneak is barrier's own modifier, unrelated to active chant). | dome absorbs 5 hits / 10s; sustain by sneaking at 500 mana/s; repels non-player mobs; fire-immune |
| **Spear of Light** | (R at circle 5) | 75 | 1s | 5th-circle transform of Magic Missile: ×1.25 damage, ×2.5 speed, golden lance, homing. | enhanced ×1.25 dmg & speed |
| **Dark Orb** | (V at circle 6) | 600 | 6s | 6th-circle transform of Area Slow. See below. | enhanced = floating form |

### Dark Orb (`entity/DarkOrbEntity.java`) — important & complex
- **Thrown** (not enhanced): gravity-free, flies straight ≤ **30** blocks, explodes on impact into a
  10-radius Slowness field + **1-second immobilize** (Slowness amp 6).
- **Floating** (enhanced / active chant): flies ≤ **50** blocks, **hovers above the surface** (does
  not phase into blocks), waits to be hit. When struck → 6-radius **Slowness II + Poison + immobilize**.
- **Spear interaction:** if an **active-chant Spear of Light** strikes a floating Dark Orb, the orb
  **absorbs** it (oil-and-water swirl) and detonates **3 seconds** later as **Hollow Purple**.
  - **Normal chaos blast** (the orb absorbed the spear, but *not both fully charged*): mid-size
    explosion — `createExplosion` power 10, radius 12, Slowness II + Poison field, immobilize.
  - **Super nuke** (**both** orb and spear were fully charged): the full **5-phase cataclysm** —
    power **18**, radius **30**, ×2.5 damage, with screen shake, impact frame (B&W flash), light
    blowout, expanding shockwave rings, B&W energy tendrils, and a 10-second residual scar. All in
    `DarkOrbEntity` (`tickChargeUp`, `detonate`, `tickDetonation`, `spawnFlash`).

---

## 7. Active chant, priming & charge (the skill layer)

Available from **circle 5**. Implemented client-side in `ModKeybinds.handleChantableSpells`.

- The **active-chant modifier is holding right-click (the Use key)**. (It was sneak before 1.0.5.1.)
- Chantable spells: **Magic Missile, Fireball, Area Slow, Wall of Wind, Thunder** (not Anti Magic,
  not Barrier).
- **Tap a spell key without right-click** → normal instant cast.
- **Hold right-click + press a chant key** (at circle 5+) → **prime** that spell. It charges up to
  3 s (`PRIME_MAX_TICKS = 60`), shown via an overlay message. **Release the key to cast** it
  *enhanced* (active chant). Held to the cap = *fully charged* (extra effects).
  - Firing happens on **release**, not auto-fire, so you can hold a fully-charged spell and then
    combine it.
- **Combination:** while a spell is primed, **press a second chant key** → fires a **combination
  magic** (`COMBINE` packet carries `primedSpellId, primedCharge, secondSpellId`). The prime resets.

Server side: `handleCast(player, spellId, primed, charge)`. `enhanced = primed && chantable && circle≥5`
(Barrier's `enhanced` is `sneaking`). `fullyCharged = enhanced && charge ≥ PRIME_MAX_TICKS`. Primed
casts fire immediately (the hold *was* the cast time); non-primed casts keep the base `castTimeTicks`
pending-fire behaviour.

### Spell forms
- At circle 5, the Magic Missile slot becomes **Spear of Light**; at circle 6 the Area Slow slot
  becomes **Dark Orb** (automatic). `CircleMageMod.resolveForm` maps base→transform unless reverted.
- **Toggle a form back/forward:** press **X** (toggles bolt form) or use the **Magic menu (K)**.
  Persisted as `revertSpear` / `revertDarkOrb` in `ManaData`.
- Form choice gates which combinations are reachable (combos that name "Magic Missile" need the
  reverted form; combos that name "Spear of Light" need the transform).

---

## 8. Passive chants & magic arrays

### Passive Chants (`spell/PassiveChant.java`) — unlocked at circle 4 (5%/use), pick one in the Magic menu
| Chant | Effect | Upkeep |
|-------|--------|--------|
| None | — | — |
| Channeling | 2.5× mana regen; **spells cost no mana** | — |
| Attack | ×1.30 spell damage | 200 mana/s |
| Reinforce | +20% movement speed; **30% damage reduction** (LivingEntityMixin) | 200 mana/s |
| Vitality | **Regeneration III** + 10 max-health hearts | none |
> NOTE on Vitality: regen is re-applied only when missing/expiring (re-adding it every tick froze
> the effect timer so it never healed — that was the original bug). Don't "fix" it by re-applying
> each tick.

### Magic Arrays (`spell/MagicArray.java`) — unlocked at circle 3 (1%/use)
- **Carve (C):** a 2-second channel (`CARVE_DURATION_TICKS=40`) costing **1000 mana**; you must hold
  still (`CARVE_MOVE_THRESHOLD_SQ`). Cooldown 5 s on success / 3 s if interrupted. Select the type in
  the Magic menu.
- A carved array is a **5-radius rune circle** that re-triggers every 2 s for 30 s (`ARRAY_LIFETIME_TICKS=600`):
  - **Fire** (ignite), **Thunder** (lightning), **Wind** (knock up/out), **Bind** (Slowness IV).
- Arrays/area-effects are **transient** (live in static manager lists, not saved).

---

## 9. Combination magic

Framework: `spell/CombinationSpell.java` (abstract) + `spell/Combinations.java` (registry +
`find(primed, primedFull, second)` dispatcher). Server handler: `CircleMageMod.handleCombine`.
Requires the **`combination_magic`** advancement and circle ≥ 5. **Order matters**; where a
combination needs a *fully charged* ingredient, that ingredient must be the **primed (held)** spell.

| Combination | Recipe (primed + pressed) | Cost | Effect |
|-------------|---------------------------|------|--------|
| **Chaos Spear** | Spear+DarkOrb (either order) | 2000 | Homing chaos lance; on impact = normal-chaos-blast explosion (power 10 + bloom + damage) with **no debuffs**; 5 s residual. |
| **Thunder Mark** | Magic Missile (primed) + Thunder | 1000 | Fires a bolt; on block-hit places a persistent, sparking **Thunder Mark** (one per player, replaces previous). |
| **Thunder Warp** | Wind Wall (full) + Thunder | 2000 | Teleports to the Thunder Mark (same dim, ≤ **10 000** blocks), keeping momentum, no fall damage; lightning-arc VFX. |
| **Fire Escape** | Wind Wall (not full) + Fireball | 1250 | Knocks the caster back + leaves a 6-radius / 5 s fire zone (never burns the caster). |
| **Blaze** | Wind Wall (full) + Fireball | 500 + 500/s | 3-second **flamethrower** cone (range 6, sets enemies on fire). |
| **Chain Lightning** | Thunder (not full) + Spear | 3000 | 5-heart bolt chaining ≤ 5 targets (×0.8 dmg, −1 radius each jump). |
| **Divine Wrath** | Thunder (full) + Spear | 4000 | Locks the cursor target; a tracking sky-beam pillar hammers it for ~3 s. |

Channeling chant makes combos free (consistent with normal spells). Combo cooldowns are tracked in
`ManaData.comboCooldownEnds[8]` indexed by `CombinationSpell.id`. Persistent/channelled combos
(Thunder Mark visual, Blaze, Divine Wrath) are ticked in `CircleMageMod.onServerTick`.

To add a combination: add a `CombinationSpell` subclass in `Combinations.java`, give it a unique
`id` (0–7 used), implement `matches`/`cast`/(optional)`requirementError`, and register it in the
`ALL` array.

---

## 10. The Laws (endgame)

`spell/Law.java` (enum), `spell/LawUnlocks.java` (unlock conditions + advancements), effects in
`CircleMageMod` (`tickActiveLaw`, `activateLaw`, per-law methods) and `LivingEntityMixin`. **Usable
only at circle 9** with the law unlocked; the player selects **one active Law** in the Laws menu (L).
Triggered laws fire with **Activate Law (B)**.

| Law | Type | Effect | Cost | Unlock condition |
|-----|------|--------|------|-----------------|
| Life | event | Cancels death, full-heal in place (`ServerLivingEntityEvents.ALLOW_DEATH`). | 100 000 (10× = 1 000 000 in hardcore) | **5 deaths + respawns** |
| Spatial Rend | active (B) | 1×10×75 space blade; destroys blocks, kills everything in its path. | 10 000 | **Comprehend the void** (take void damage) |
| Space Manipulation | passive | Teleport magics (Thunder Warp) cost 0. | — | Comprehend the void |
| Storage | passive | Adds a 27-slot persistent inventory ("Open Storage" in the Laws menu). | — | Comprehend the void |
| Gravity Amplification | passive | Entities within 10 blocks slowed harder the closer they are; crushed (cramming) at 10 stacks. | 2000/s while affecting | **command only** |
| Black Hole | passive | Vacuums **already-dropped items** within 30 blocks into the inventory (does **not** mine blocks). | 250/s | **command only** |
| Time Stop | active (B) | Freezes entities within 200 blocks for 5 s (mob AI disabled + velocity zeroed). | 10 000 | **command only** |
| Time Reversal | active (B) | Full heal. | 2000 | **command only** |
| Creation | passive (cmd) | Enables `/lawcreate <item> [count]` at 2000 mana/item. | (per item) | Unlocked with base Laws |
| Causality | event | Reflects damage to a living attacker (1000 mana/heart) or negates non-living damage at 3×; also negates fall damage path is separate. | per hit | **Struck by every hostile mob** (curated set in `LawUnlocks.REQUIRED_HOSTILES`) |
| Love of the World | passive | Water breathing, night vision, fire resistance, haste I, speed I, regen I, fall-damage negation. | 4000/s | Unlocked with base Laws |

**Unlock flow** (`LawUnlocks`):
- **Discovering Laws** (the 0.01%/s roll, or `/circlemage unlocklaws`) sets `lawsUnlocked` (the
  circle-9 gate), grants the `laws` advancement, and unlocks **Love of the World + Creation**.
- Conditions are tracked **always** (`deathCount`, `voidComprehended`, `hostilesHit`) and
  `reevaluate()` unlocks any law whose condition is now met *and* base Laws are discovered.
- `/circlemage unlockalllaws` unlocks everything (the only way to get Gravity/Black Hole/Time laws).
- Each law has its own advancement (`law_*`); the command-only ones are `hidden: true`.
- **Hardcore Law of Life:** revives at 10× cost and sends a `GIVE_UP` packet → client opens
  `GiveUpScreen` ("Are you going to give up?" with two **No** buttons — flavor; you're already alive).

---

## 11. Controls & keybinds

Registered in `ModKeybinds`; category `key.circlemage` ("Circle Mage"). All rebindable in
Options → Controls.

| Action | Default | Notes |
|--------|---------|-------|
| Channel Mana | **M** | toggle |
| Magic Missile / Spear | **R** | chantable |
| Fireball | **T** | chantable |
| Area Slow / Dark Orb | **V** | chantable |
| Anti Magic | **G** | hold 0.75 s |
| Wall of Wind | **Y** | chantable |
| Thunder | **H** | chantable |
| Barrier | **Z** | sneak = dome |
| Carve Magic Array | **C** | hold still 2 s |
| Toggle Bolt Form | **X** | Missile ⇄ Spear |
| Activate Law | **B** | triggers selected active law |
| **Dash** | **(unbound)** | movement skill; player binds it in Options → Controls (`Spell.DASH`) |
| Open Mage Journal | **J** | `MageJournalScreen` (progression guide) |
| Open Magic Menu | **K** | `MagicScreen` |
| Open Laws Menu | **L** | `LawSelectionScreen` (msg if not unlocked) |
| **Active chant modifier** | **right-click (Use)** | hold while pressing a chant key to prime/combine |

---

## 12. Technical architecture

### Per-player data ("capability") — `mana/ManaData` + `mana/ManaPlayer`
There is **no Fabric capability API**; data is attached via a **mixin duck-type**:
- `PlayerEntityMixin implements ManaPlayer`, holds a `@Unique` `ManaData` instance, and reads/writes
  it in `writeCustomDataToNbt`/`readCustomDataFromNbt` under the NBT key `"CircleMage"`.
- `ManaPlayer.get(PlayerEntity)` casts the player to `ManaPlayer` and returns its `ManaData`.
- On death/respawn/dimension change, `ServerPlayerEvents.COPY_FROM` copies all persistent fields;
  `AFTER_RESPAWN` syncs to client + increments death count (when `!alive`).

**`ManaData` persisted fields:** mana, circle, realm-buff mask, `arrayUnlocked`, `selectedArray`,
`passiveChantUnlocked`, `selectedChant`, `revertSpear`, `revertDarkOrb`, Thunder Mark (`hasThunderMark`,
`markX/Y/Z`, `markDim`), `lawsUnlocked`, `selectedLaw`, `unlockedLaws` (bitmask), `deathCount`,
`voidComprehended`, `hostilesHit` (set of type ids), `storageItems` (NbtList).
**Transient fields:** `channeling`, `debugManaMultiplier`, `cooldownEnds[16]`, `immunityEndTick`,
`cooldownsEnabled`, carving state, pending cast, `activeDomeId`, `comboCooldownEnds[8]`,
`blazeEndTick`, Divine Wrath target/end, `timeStopEndTick`, `frozenMobs`, `blackHoleRadius` (now
unused after the Black Hole rework — safe to delete).

### Networking — `network/ModNetworking`
- **C2S:** `CHANNEL_TOGGLE`, `CAST` (int spellId, bool primed, int charge), `CARVE`,
  `SELECT_ARRAY` (int), `SELECT_CHANT` (int), `COMBINE` (int primedId, int charge, int secondId),
  `SELECT_FORM` (int baseSpellId, bool revert), `SELECT_LAW` (int), `ACTIVATE_LAW`, `OPEN_STORAGE`.
- **S2C:** `MANA_SYNC` (the full client mirror — see below), `SHAKE` (float intensity, int ticks),
  `IMPACT` (int ticks), `GIVE_UP`.
- `MANA_SYNC` payload order (must match `ClientManaState.update` / the receiver in
  `CircleMageClient`): `double mana, int circle, long threshold, bool channeling, int unlockedBuffs,
  bool arrayUnlocked, int selectedArray, bool chantUnlocked, int selectedChant, bool revertSpear,
  bool revertDarkOrb, bool lawsUnlocked, int selectedLaw, int unlockedLaws`.
  **If you change this, update both ends or the client will desync/crash.**

### Registries
- **Entities** (`ModEntities`, common): `magic_missile`, `magic_barrier`, `dark_orb`, `spatial_rend`.
  Renderers registered in `CircleMageClient`.
- **Particles** (`ModParticles`, common): `arcane, arcane_trail, ember, frost, spark, gale, radiance,
  barrier_shard, rune_fire/thunder/wind/bind, bind_mote, light, void, smoke, dust`. Each needs a
  `assets/circlemage/particles/<name>.json` mapping to a texture, and a client factory in
  `CircleMageClient.registerParticles` (all use the one `MagicGlowParticle` class with a `Settings`
  record: colour, scale curve, gravity, drift, lifetime, spin, fade-in).
- **Advancements** are all `minecraft:impossible` triggers, granted from code via
  `getAdvancementLoader().get(id)` + `grantCriterion`.

### Rendering / VFX
- Entity renderers draw geometry on `RenderLayer.getEntityTranslucentEmissive(texture)`
  (full-bright, double-sided quads). See the four `*Renderer` classes.
- **Camera shake:** server `SHAKE` packet → `ClientShakeState` → `CameraMixin` offsets the render
  camera yaw/pitch (view-only; doesn't affect aim).
- **Impact frame:** server `IMPACT` packet → `ClientImpactState` loads the `desaturate` post shader
  via `GameRendererAccessor` (@Invoker, guarded in try/catch) + a white HUD flash.

### Commands — `command/ModCommands`
- `/circlemage …` (op, perm level 2): `manamultiplier`, `addmana`, `setmana`, `setcircle`,
  `cooldowns`, `unlocklaws`, `unlockalllaws`.
- `/lawcreate <item> [count]` (any player): Law of Creation — gives items at 2000 mana each.

---

## 13. Known bugs, limitations & unverified pieces

- **Not runtime-tested.** Everything compiles and the full build passes, but the author could not
  launch the game. The highest-risk, runtime-only pieces:
  - **`CameraMixin`** (`Camera.update` target) and the **desaturate post shader**
    (`assets/minecraft/shaders/program/circlemage_gray.*`). Both are guarded (the shader load is in
    try/catch), so a failure degrades gracefully rather than crashing — but verify they actually
    apply. The grayscale program **must** live in the `minecraft` namespace because 1.20.1's
    `JsonEffectShaderProgram` resolves program names there.
- **Spatial Rend & (historically) terrain damage:** Spatial Rend annihilates a 1×10×75 block channel
  by design. Tunable in `SpatialRendEntity` (SPEED, MAX_DISTANCE, HALF_UP/DOWN).
- **Time Stop:** uses `setAiDisabled` + velocity-zeroing, not a true tick-freeze. Mobs frozen at the
  moment of a player logout could stay AI-disabled (the `frozenMobs` set is transient). 5 s window.
- **Causality reflect** uses a plain `magic()` source (no attacker) to avoid infinite reflect loops,
  so reflected kills aren't credited to the player.
- **Causality unlock** ("every hostile mob") uses a **curated** ~27-mob set
  (`LawUnlocks.REQUIRED_HOSTILES`), not literally every entity. Expand/trim as desired.
- **Laws "discover" rate** (0.01%/s ≈ once per ~2.8 h of play) is intentionally slow; use the command
  to test.
- **`ManaData.blackHoleRadius`** and its accessors are dead code after the Black Hole rework.
- **Balance:** combos/laws are deliberately powerful and costs are first-pass. The 10-billion 9th
  circle is an enormous grind by design (use `/circlemage addmana` / `setcircle` to test).
- **No `pack.mcmeta` / `recipes` / `loot`** — none needed (no items/blocks).

---

## 14. Configuration & balancing numbers (quick reference)

| Constant | Location | Value |
|----------|----------|-------|
| `BASE_GAIN_PER_SECOND` | `ManaData` | 5 |
| `BASE_DAMAGE` | `ManaData` | 4 (2 hearts) |
| circle gain multiplier | `CircleMageMod.onServerTick` | ×3 at circle ≥ 1 |
| `PRIME_MAX_TICKS` | `CircleMageMod` | 60 (3 s) |
| circle-5 discount | `ManaData.circleDiscount` | ×0.85 |
| realm buff mana / damage | `ManaData` | ×2 / ×1.2 per buff (`REALM_DAMAGE_PER_BUFF`) |
| unlock chances | `CircleMageMod` | see §5 table |
| `LAWS_UNLOCK_CHANCE` | `CircleMageMod` | 0.0001 / sec |
| anti-magic immunity | `CircleMageMod` | 100 ticks (5 s) |
| chant upkeep | `CircleMageMod` | 200 mana/s (Attack/Reinforce) |
| dome upkeep / hit cost | `MagicBarrierEntity` | 500 mana/s / 500 per hit |
| Dark Orb absorb delay | `DarkOrbEntity` | 60 ticks (3 s) |
| Hollow Purple (normal/super) | `DarkOrbEntity` | power 10/18, radius 12/30 |
| spell costs/cooldowns | `Spell` enum | see §6 |
| combo costs | `Combinations` | see §9 |
| law costs | `CircleMageMod` law methods | see §10 |

---

## 15. Design philosophy & intended experience

- **Earn power by playing, not by crafting.** No items; progression is mana + random unlocks that
  reward simply *using* magic. Each circle feels like a milestone.
- **A skill layer on top of the grind.** Active chant (charge timing) and combinations reward
  deliberate input; tapping is fine for basics, mastery is in holding/charging/combining.
- **Spectacle scales with investment.** Early spells are modest; the Hollow Purple super-nuke and the
  Laws are screen-shaking, world-altering payoffs for late-game mages.
- **Cohesive arcane art.** Everything uses the custom glowing-glyph particle/texture style; avoid
  vanilla particle/texture reuse (the whole point of the early revamp).
- **Laws are "broken on purpose"** but gated behind the 9th circle (10 B mana), specific unlock
  conditions, and high mana costs.

---

## 16. Roadmap & recommended next steps

1. **Runtime-verify** (highest priority): launch `runClient` and confirm the camera shake, the
   desaturate impact frame, all four entity renderers, the two menus at various GUI scales, the
   Spatial Rend, and Black Hole vacuuming. Fix anything that mismatched the 1.20.1 mappings.
2. **Balance pass** with `/circlemage` cheats: tune combo/law costs, Hollow Purple power, Spatial
   Rend length, the 10-billion grind (consider whether circle 8→9 needs an intermediate sink).
3. **Combination magic depth:** the framework supports more recipes — add element-based combos.
4. **Persist a few transient combos** if desired (e.g., Divine Wrath/Blaze across save) — currently
   transient is fine because they're short-lived.
5. **Localization & config:** move tunable numbers into a config file (e.g., Cloth Config or a simple
   JSON) so balancing doesn't require recompiles; add more `lang` entries.
6. **Polish the Laws UI** (icons per law, condition hints/progress in `LawSelectionScreen`, a proper
   "comprehend the void" progress indicator, Causality mob-checklist).
7. **Cleanup:** remove dead `blackHoleRadius`; consider extracting the giant `CircleMageMod` into
   smaller handlers (cast handler, law handler) as it has grown large.
8. **Multiplayer review:** the systems are server-authoritative and per-player, but stress-test
   Time Stop / Black Hole / Gravity with multiple players and many entities for TPS.

### How to extend, cheat-sheet
- **New spell:** add to `Spell` enum → handle in `performSpell` → add a keybind in `ModKeybinds`
  (and the chantable arrays if it should support active chant) → add cost/cooldown.
- **New combination:** subclass `CombinationSpell` in `Combinations`, register in `ALL`.
- **New law:** add to `Law` enum → unlock logic in `LawUnlocks` (+ advancement json) → effect in
  `CircleMageMod` (`tickActiveLaw`/`activateLaw`) → it auto-appears in `LawSelectionScreen`.
- **New particle:** `ModParticles.reg(...)` → `particles/<name>.json` → texture (add to
  `gen_textures.ps1`) → factory in `CircleMageClient.registerParticles`.
- **Always** keep `MANA_SYNC` read/write in sync across `ModNetworking`, `CircleMageClient`,
  `ClientManaState`.

---

## 17. Dash skill & the Wandering Mage NPC (added in 1.0.8)

### Dash (player movement skill)
- `Spell.DASH` (id 9): 100 mana, 2 s cooldown, instant. Cast handler `CircleMageMod.castDash`: a
  horizontal burst in the look direction (velocity ≈ 1.9 with a small hop), clears fall damage.
- **Default keybind is unbound** (`GLFW_KEY_UNKNOWN` in `ModKeybinds`) — the player chooses their own
  key in Options → Controls (`key.circlemage.dash`). It is *not* chantable/combinable (excluded from
  `chantable` in `handleCast`) and reuses the existing `CAST` packet.

### Wandering Mage (`entity/WanderingMageEntity` + `entity/WanderingMageCombatGoal`)
A rare roaming Overworld caster; passive until attacked; **infinite mana but obeys spell cooldowns**.
- **Spawning:** `SpawnGroup.MONSTER`, `BiomeModifications.addSpawn(foundInOverworld, …, WEIGHT, 1, 1)`
  in `CircleMageMod.registerWanderingMageSpawning`. **`WEIGHT` (=6) is the rarity knob** (targeting
  ~1 mage / 100–200 blocks). `SpawnRestriction.ON_GROUND` + `WanderingMageEntity.canSpawn`
  (lit, exposed surface) keeps them roaming in daylight. Circle 1/2/3 is rolled in `initialize`.
- **Stats:** HP = `20 + 4×circle` (Prodigy 60); damage = `BASE_DAMAGE × 1.2^circle` (Prodigy ×1.2);
  "receives realm buffs" = one buff per circle (the 1.2× per-buff multiplier). Cooldowns read from the
  `Spell` enum; Prodigy ×0.8. Tracked data `CIRCLE`/`PRODIGY` drive damage + which skin renders.
- **Abilities:** Magic Missile (homing, owner = mage), Wind Wall (knock the player back), Burn (a
  `FIRE` `AreaEffect` under the player), Thunder (one-time, < 50 % HP), Dash (evasion/positioning).
- **AI (combat goal):** range bands 20–50 / 5–20 / <5 (missile / missile+burn / wind wall); projectile
  evasion dashes perpendicular (or sidesteps); positioning dashes back when rushed, closes when the
  player retreats, else strafes; **never flees**; 75-block pursuit. Targeting is set in
  `damage()` (attacker becomes target) and dropped in `mobTick()` (dead / >75 blocks), so it ignores
  mobs by construction (no target-selector goals).
- **Name above head:** a random given name + rank (`"<name> — Circle N Mage"` / `"<name> the
  Prodigy"`) set via `setCustomName` + `setCustomNameVisible` at spawn (vanilla NBT-persisted).
- **Look:** a robed biped — custom 64×64 skins per circle + Prodigy (`textures/entity/wandering_mage_*`,
  generated by `gen_textures.ps1` `MakeMageSkin`); `WanderingMageRenderer` + the `wandering_mage`
  model layer (registered in `CircleMageClient`).

### The Prodigy & the Master framework
- Only a **Circle 3** mage can become The Prodigy, at a **1-in-3** chance, gated by
  `WorldProgressState.canProdigySpawn()` (none alive, none ever defeated). Prodigies are persistent
  (never despawn).
- `world/WorldProgressState` is a `PersistentState` on the **Overworld** holding `prodigyAlive`
  (enforces "only one") and **`prodigyDefeated`** (the permanent `PRODIGY_DEFEATED` flag).
  `onDeath` → `markProdigyDefeated()` + broadcasts *"My master will avenge me..."*; `remove()` frees
  the alive-slot on real removal (not chunk unload). **`canMasterSpawn()`** opens once the Prodigy is
  defeated — the documented extension point for future Master spawn logic (no world reset needed).

### Commands (op, under `/circlemage`)
- `/circlemage spawnmage <1-3>` — spawn a Wandering Mage of the chosen circle in front of you.
- `/circlemage spawnprodigy` — spawn The Prodigy (Circle 3; claims the world's one-Prodigy slot).

### Unverified (1.0.8, compile-only — see §13)
Natural-spawn **rarity/weight**, the combat-AI feel (cast cadence, dash dodging, strafe pathing), the
custom biped skins/model, and the floating name all need an in-game pass. Tune `WEIGHT`, the ability
cooldowns, dash power, and `IDEAL_DISTANCE` to taste.

---

## 18. Mage Journal (1.0.9) — the central progression UI

`client/MageJournalScreen` is a full-screen, themed guide opened with **J**. It is **data-driven**:
each section rebuilds a `List<Line>` from `ClientJournalState` every frame, so extending it never
touches layout code.

**Data flow.** Server builds a snapshot in `ModNetworking.sendJournalSync(player)` (circle, the
anti-magic/combination advancement flags via `hasAdvancement`, array/chant/laws unlocks, the
`unlockedLaws` bitmask, realm-buff count, and the four encounter/boss flags) → `JOURNAL_SYNC` →
`CircleMageClient` receiver → `ClientJournalState`. It's sent on **join** and whenever the client
opens the journal (the keybind fires `REQUEST_JOURNAL` first, guaranteeing fresh data each open).

**Persistence.** Per-player: `ManaData.encounteredWanderingMage` (set when a player comes within 24
blocks of a Wandering Mage, in `WanderingMageEntity.tick`). World-global: `WorldProgressState`
(`prodigyDefeated`, plus `masterDefeated`/`lichDefeated` added now as infrastructure — flip them with
`markMasterDefeated()` / `markLichDefeated()` when those bosses ship).

**Sections:** Circle Progression (status/mana/gate/description/hint per circle, 9th's full reqs hidden
until circle ≥ 8), Advancements (incl. per-Law list from the `Law` enum + `unlockedLaws`), Encyclopedia
(spells/active-chant/passive-chants/arrays/combos; locked entries show `????`), Major Encounters
(hidden until met/defeated), and the Mage's Journey questline. A contextual `hint()` shows at the bottom.

**To extend:** add a circle → it appears automatically (loop over `Circles.THRESHOLDS`); add a Law →
appears in Advancements/Quest via the `Law` enum; add a boss → set its flag in `WorldProgressState`,
add a field to the `JOURNAL_SYNC` payload (both ends) + `ClientJournalState`, and add an `encounter(...)`
/ `quest(...)` line. Keep the `JOURNAL_SYNC` read/write order in sync across `ModNetworking`,
`CircleMageClient`, and `ClientJournalState`.

### Unverified (1.0.9, compile-only)
The journal **layout at various GUI scales/resolutions** (panel sizing, scroll clipping, the unicode
glyphs ✦/✓/✖/●/□), and that the encounter flag/boss flags drive the right entries, need an in-game pass.

---

## 19. Boss Magic & the Boss Zone framework (1.0.10)

Boss/event-only systems in the **`boss/`** package, exposed for testing through **`/bossmagic`** (op).
**Players can never learn, unlock, or cast these** — there are no keybinds, advancements, or unlock
paths; the only entry points are the command and (future) boss code.

- **`BossZone`** — a transient spherical zone (center, radius, dimension, owner UUID). Owns the
  containment barrier, the optional `MeteorRain`, and a configurable restriction set. Ticked by the
  manager. Add new boss mechanics here so future encounters reuse the framework.
  - **Containment (Domain Barrier):** membership is tracked by UUID (`insiders` captured at creation;
    new entities classified when clearly inside). Insiders can't leave, outsiders can't enter —
    enforced radially each tick (cancel cross-shell velocity near the wall; hard `requestTeleport`
    /`setPosition` back when crossed; projectiles crossing are discarded). Because it's membership-based
    and after-the-fact, it catches **teleports and dashes** too, not just walking/knockback.
  - **Visual:** `renderShell()` draws a `BARRIER_SHARD` patch of the sphere only around players near the
    wall (cheap; the full 100-block sphere is never meshed).
- **`BossZoneManager`** — static registry: `register`, `removeByOwner`, `zoneByOwner`,
  `zoneContaining(world,pos)`, `wouldOverlap` (blocks improper overlap), `isSpellRestricted(player,id)`,
  and `tickAll()` (called from `onServerTick`). Zones **persist** via `BossZoneState` (1.0.10.2) — call
  `BossZoneManager.markDirty()` after any zone change so it saves.
- **`MeteorRain`** — per-zone. Continuously queues telegraphed `Impact`s at random ground points inside
  the disc. Telegraph = a **red `DustParticleEffect` circle that fills** over the warning period
  (replaces trajectory lines since many land at once). **Type A** (Magic Missile): frequent, small
  (~1 block), ~1.25 s warn, 2-heart burst. **Type B** (Fireball): rare, ~3-block, 2 s warn, bigger hit
  + a short `AreaEffect.FIRE` that auto-expires (no block changes). Concurrency caps + random placement
  keep safe gaps — threatening but always dodgeable. Tune the `A_*`/`B_*` constants.
- **Magic Restriction Field** — `BossZone.setRestricted(spellId, bool)` + `setRestrictionsActive`. The
  command's default set blocks Magic Missile (+Spear), Burn/Fireball, Wind Wall, Thunder, Dash, but it's
  per-spell and per-zone so future bosses choose their own. Enforced in `CircleMageMod.isSpellSuppressed`
  (called from `handleCast`/`handleDash`/`handleCombine`): only inside an active zone, only the listed
  spells, with the message *"The domain suppresses certain forms of magic."*

**Commands:** `/bossmagic domain` · `removedomain` · `meteorrain` (toggle) · `restrictmagic enable|disable`.

**To extend (future Master/Lich, phases):** add a mechanic class ticked by `BossZone`, or new restriction
sets; flip `WorldProgressState` boss flags on defeat (already wired to the journal). The zone already
tracks players inside, active mechanics, and restrictions — the documented hook for multi-phase fights.

### Unverified (1.0.10, compile-only)
Containment feel at the wall (jitter/teleport-back on players, fast projectiles), the Meteor Rain
telegraph readability/rates, and restriction coverage all need an in-game pass. Knobs: `DOMAIN_RADIUS`,
the `MeteorRain` `A_*`/`B_*` constants, and the command's restriction set. Note the per-tick
sphere entity scan is sized for boss arenas, not huge mob farms.

---

## 20. Mana infusion & Mage's Robes (1.0.10.1)

**Mana infusion** (`item/ManaInfusion`): a small `Item → Item` registry. While a player channels and
holds an infusable item in the main hand, `CircleMageMod.tickManaInfusion` converts **one item per
second into its arcane form instead of gaining mana** (timer on `ManaData.infusionProgress`). Register
more pairs in `ModItems.register` (currently `Items.STRING → ARCANE_STRING`).

**New items** (`item/`, all registered via `ModItems.reg`):
- `ARCANE_STRING` — `ArcaneStringItem` overrides `hasGlint → true` (the "glowing" look).
- `ARCANE_YARN` — plain item; recipe `arcane_string + stick + arcane_string` (`data/.../recipes/arcane_yarn.json`).
- `MAGE_ROBE_{IRON,AMETHYST,EMERALD,DIAMOND}` — `MageRobeItem extends ArmorItem` (CHESTPLATE).
  Recipe: 8 `arcane_yarn` around a core (iron/amethyst_shard/emerald/diamond). Magic resistance
  0.30/0.30/0.40/0.50 via `MageRobeItem.getMagicResistance()`; applied in
  `MagicMissileEntity.onEntityHit` (`dealt *= 1 - MageRobeItem.resistanceOf(target)`) — extend to other
  magic by calling `resistanceOf` at those damage sites.

**Worn rendering:** each robe uses `RobeArmorMaterial` (4 entries, names `robe_iron`…`robe_diamond`).
1.20.1 resolves armor textures in the **minecraft** namespace, so the worn layers live at
`assets/minecraft/textures/models/armor/robe_*_layer_1.png` (+ transparent `_layer_2`). All textures
(item icons + worn layers) are generated by `gen_textures.ps1` — **re-run it after edits, don't hand-edit PNGs.**

### Unverified (1.0.10.1, compile-only)
The `ClientPlayerFovMixin` (`getFovMultiplier` target), the worn-robe armor textures actually
resolving, the glint on Arcane String, and the recipes/robe equip + resistance all need an in-game
check. Robe magic resistance currently only reduces **Magic Missile** damage (per spec).

---

## 21. The Master boss (1.0.11)

`entity/MasterEntity` (100 HP, `MAX_HP`) — the first major boss, summoned only via **`/bossmagic
summonmaster`** (spawns 50 blocks ahead + a `RADIANCE` locator beam). No natural spawn. Built entirely
on existing systems (Boss Zone, Meteor Rain, Magic Missile, Dark Orb super-nuke). The whole brain is in
`mobTick()` (no combat goal) as a phase state machine; idle uses only Swim + LookAt goals.

- **Triggers** (`tickNeutralTriggers` + `damage`): approach (≤20), escape (was ≤50 then left), or
  attacked → `trigger()` raises a 64-block Domain Barrier owned by the Master's UUID, sets the boss
  bar, targets the player, with the three dialogue lines.
- **Containment**: the Boss Zone keeps both the Master and the player in (radius 64 > the 50 trigger).
- **Boss bar**: `ServerBossBar` "The Master | 5th Circle"; players in the zone added/removed each tick;
  percent = HP, or the draining 60s countdown in phase 2 ("| 6th Circle — Ns").
- **Arcane Barrage**: 1s stationary wind-up with red-dust telegraphs in 16 directions, then 16
  evenly-spaced straight Magic Missiles (no homing/deviation).
- **Meteor Rain phases**: 70→50% and 20→0% HP. Teleports to center, hovers 10 up (`setNoGravity`),
  toggles the zone's Meteor Rain, takes **80% reduced damage** (`damage()` multiplies by 0.2 — covers
  magic/fire/etc.), dash-dodges projectiles on a 3s CD and snaps back to hover.
- **Barrier Punishment** (once): `CircleMageMod.handleCast` calls `MasterEntity.tryBarrierPunish` on a
  player's Barrier cast inside the domain → "I won't make it that easy.", 3 missiles, and adds
  `BARRIER` to the zone's restriction set (suppressed for the rest of the fight).
- **Phase 2 / death**: at lethal damage, `damage()` clamps to 1 HP once and runs `beginPhase2()`
  (circle→6, ascension beam, 60s countdown), then stays invulnerable (clamped) until the finale.
  Countdown 0 → `beginFinale()` (teleport center, pause) → `unleashCataclysm()` spawns a `DarkOrbEntity`
  and calls the new `triggerGroundCataclysm()` (full super Hollow Purple), then `kill()` (bypasses the
  clamp via `finaleDeath`/OUT_OF_WORLD). `onDeath` removes the domain + boss bar, marks
  `WorldProgressState.markMasterDefeated()`, drops `FRAGMENT_OF_LAW`, and re-syncs the journal.
- **Rendering**: `MasterRenderer` (biped, slightly dilated, `the_master.png`); name + boss bar separate.

### Unverified (1.0.11, compile-only)
The entire encounter needs an in-game pass: trigger conditions, the boss bar + containment, barrage
telegraph/timing, both meteor phases (hover/dodge/80% reduction), barrier punishment, the phase-2
countdown bar, and the finale cataclysm + cleanup/drop. Tunables live as constants atop `MasterEntity`
(HP, radius, cooldowns, `COUNTDOWN_TICKS`, `HOVER_HEIGHT`). Mid-fight reload behaviour (Master + zone
both persist; boss bar re-adds players each tick) is also untested.

> **Note (1.0.12):** several of the numbers above were superseded — Arcane Barrage is **32** missiles,
> the meteor-phase reduction is now **85%** (magic only, see §22), and Barrier Punishment is delayed 2 s.

---

## 22. Magic resistance, player Wind Blade & Master combat upgrades (1.0.12)

### Innate magic resistance — `spell/MagicResistance`
A shared stat for all mages. `forCircle(circle)` = **5% × circle** (15% at the 3rd, 25% at the 5th, …),
clamped to 95%. `isResistible(source)` is true for **fire** (`IS_FIRE` tag) and **raw magic**
(`MAGIC` / `INDIRECT_MAGIC`) but **not lightning** (the Wandering Mage already has its own diminishing-
Thunder falloff, so resistance would double-dip) and never for `BYPASSES_INVULNERABILITY` damage.
Applied at three sites, each multiplying incoming resistible damage by `(1 − resistance)`:
- **Player** — `LivingEntityMixin.modifyAppliedDamage` (@RETURN), using `ManaData.getCircle()`; stacks
  with the existing Reinforce 30% reduction and with worn Mage's Robes (robes reduce the missile at the
  projectile, *before* `damage()`).
- **Wandering Mage** — in `damage()`, after the lightning falloff, using `getCircle()`.
- **The Master** — in `damage()` (replaces the old flat `×0.2`): `forCircle(5)=0.25` **+ 0.10 natural**
  (`MASTER_NATURAL_RESIST`) = **35%** grounded; **+ 0.50** (`MASTER_LEVITATE_RESIST`) while `meteorActive`
  = **85%** airborne. Non-magic damage during a meteor phase is no longer reduced (it's now "magic
  resistance"), but he hovers out of melee range anyway.

### Wind Blade — now a player spell (removed from the boss)
`Spell.WIND_BLADE` (id 10, 150 mana, 3 s cd). Unbound default key `key.circlemage.wind_blade` →
`sendCast(10)`. Not chantable/primable/combinable (excluded in `handleCast`). `castWindBlade` starts a
**5-blade volley** stored transiently on `ManaData` (`windBladeShots`/`windBladeNextTick`); the blades
fire ~2 ticks apart from `onServerTick` (`firePlayerWindBlade`) toward the player's *current* look, so
the volley can sweep. Each blade reuses `WindBladeEntity` (owner = player) and deals
`BASE_DAMAGE × damageMultiplier × attackChant ÷ 3` — **1/3 of a Magic Missile**. The Master's
`tickWindBlade`/`fireWindBlade` and all `WINDBLADE_*` constants/state were deleted.

### The Master — new/changed combat
- **Meteor frequency doubled**: `startMeteorPhase` sets the zone's rates to **100× missile / 60× fireball**
  (`MASTER_METEOR_RATE_*`, double the 50×/30× base).
- **Delayed Barrier Punishment**: `onPlayerUsedBarrier` now schedules the 3 retaliation missiles for
  `now + 40` (2 s) via `barrierPunishFireTick`/`barrierPunishTarget`; `tickBarrierPunish` fires them.
  The dialogue + Barrier suppression are still immediate.
- **Fire Line** (`maybeStartFireLine`/`startFireLine`/`tickFireLine`/`sweepFireLines`): triggered each
  time HP crosses a fresh **20% step** (`fireLineThreshold` starts 0.80, drops by 0.20; one big hit fires
  only one line). A **1 s telegraph** (`FIRE_LINE_CAST_TICKS`) draws **16 equally-spaced lines**
  (`FIRE_LINE_DIRS`) from the boss out to the barrier, the fan rotated so **line 0 points at the player**
  (`fireLineBaseAngle`). Then a fire **front** sweeps each line at `FIRE_LINE_SPEED` (2 blocks/tick),
  spawning embers and dealing `magicDamage()` + brief fire to anything within `FIRE_LINE_HIT_RADIUS`
  (once each). **Cast 1 = forward** (boss→barrier) + "Look behind"; **cast 2 = reverse** (barrier→boss,
  `fireLineReverse`) + "Look behind"; from cast 3 it **alternates silently**. The boss holds still while
  casting/sweeping (other attacks are gated). `fireLineThreshold`/`fireLineCasts` persist in NBT.
- **Kiting movement** (`tickMovement` now takes the `BossZone`): instead of backpedalling away from the
  player (which pinned it on the wall), the Master **orbits the domain centre** on the side *opposite* the
  player (`desired = centre − player`), at `ORBIT = 16` blocks, with a slow strafe weave; it dashes back
  toward centre if crowded (`< KEEP_DISTANCE`) or pushed past `MAX_FROM_CENTER = 28`.

### Unverified (1.0.12, compile-only)
Needs an in-game pass: that player Wind Blade fires a 5-blade volley at ~1/3 missile damage on its bound
key; that circle resistance visibly reduces fire/magic on the player, mages and the Master (35% grounded
/ 85% levitating); the doubled meteor density; the 2 s-delayed punishment; the Fire Line telegraph →
sweep (forward, reverse, alternation, the player-aimed line, "Look behind" on the first two only); and
that the boss now stays central instead of wall-pinning. Tunables are the new constants atop
`MasterEntity` (`FIRE_LINE_*`, `MASTER_*_RESIST`, `MASTER_METEOR_RATE_*`, `BARRIER_PUNISH_DELAY`) and
`Spell.WIND_BLADE` / `WIND_BLADE_VOLLEY` / `WIND_BLADE_GAP` in `CircleMageMod`.
