# CircleMage 2.3.0 — project map

This is a Java/Fabric source project for Minecraft 1.20.1, with **82 Java files / 14,781 source lines** and **168 resource files**. Its built 2.3.0 JAR matches the installed mod by SHA-256. The catalog is an inventory, not a fresh compilation result.

## How the pieces connect

Client controls choose a known spell/form and send a packet. `CircleMageMod` validates knowledge, circle, cooldown, mana, and domain restrictions, then starts or completes the spell. Projectiles/entities or temporary field managers perform its effects. `ManaData` persists player progression; networking mirrors it to HUDs and menus.

Wandering mage variants use shared movement goals and spell methods. Towers attach guard post data to that same entity. The Prodigy is another variant. Master has a separate combat controller and owns a persistent BossZone; MeteorRain and existing projectile/orb effects provide its arena attacks. WorldProgressState holds encounter flags. The intended Prodigy-to-Master eligibility helper currently has no spawning caller.

## Directory guide

| Location | Purpose |
|---|---|
| `src/main/java/com/example/circlemage` | Original gameplay/client source |
| `src/main/resources` | Mod metadata, mixins, textures/models/shaders, recipes, advancements and worldgen |
| `build.gradle`, `gradle.properties`, `gradlew*`, `gradle/` | Fabric Loom build configuration; Java 17 toolchain |
| `build/libs/` | Existing compiled JARs and source JARs, including older snapshots |
| `build/`, `bin/`, `.gradle/` | Existing outputs and caches; not authoritative source |
| `run/` | Development Minecraft working data |
| `nbt/` | Original/exported tower structure files; deployed templates are in resources |
| `tools/gen_textures.ps1` | Existing asset-generation tool; not executed |
| `README.md`, `HANDOFF.md` | Earlier documentation/history; several current mechanics differ |
| `.vscode/`, `.claude/`, `.qodo/` | Existing editor/tool project files |
| `circlemage-readthrough-2026-10-06/` | New guide, audit notes and verification artifacts only |

`gradle.properties` declares Minecraft 1.20.1, Yarn 1.20.1+build.10, Fabric loader 0.15.11, Fabric API 0.92.2+1.20.1, and mod version 2.3.0. The installed CurseForge instance has loader 0.19.3 and API 0.92.9+1.20.1, plus Sodium/Iris. I did not update dependencies or invoke a build. Background Java/Gradle tooling changed caches/generated mapping data while the source/resources remained unchanged; see the verification report.

## Content inventory

- Nine progression circles, with three discovery gates (entering 4, 6, and 9).
- Twelve spell IDs, including two evolved forms: ten underlying selections plus Spear and Dark Orb.
- Seven combination spells, plus the separate physical orb-and-spear explosion interaction.
- Four passive chants, four arrays, eleven Laws.
- Eight registered entities: Magic Missile, Magic Barrier, Dark Orb, Spatial Rend, Wandering Mage, Wind Blade, Tutorial Book, Master. Prodigy/guardian are mage variants; there is no Lich entity.
- Five tome schools (Water/Earth empty), four robe variants, customizable wand, focus, station, infusion materials, vials, crystals, and Fragment reward.
- Four structure templates / four structure definitions / four template pools / three placement sets, with a 16-biome eligibility tag.
- Thirteen crafting recipe files and fifteen advancement definitions.
- Journal/tutorial intro, mana/spell HUDs, three selection/reference menus, station screen, NPC/projectile renderers, particles, impact effects, camera shake and grayscale post-processing.

## Source file index

Every original Java file is listed below. Links resolve to the originals; descriptions summarize responsibility, not a claim that every path has been tested.

| File | Lines | Responsibility |
|---|---:|---|
| [block/ArcaneStationBlock.java](../src/main/java/com/example/circlemage/block/ArcaneStationBlock.java) | 38 | Station placement/use and screen-handler opening. |
| [block/ArcaneStationScreenHandler.java](../src/main/java/com/example/circlemage/block/ArcaneStationScreenHandler.java) | 191 | Component slot validation, wand preview/output, consumption and input return. |
| [block/ModBlocks.java](../src/main/java/com/example/circlemage/block/ModBlocks.java) | 42 | Arcane Station block/item and screen-handler registration. |
| [boss/BossZone.java](../src/main/java/com/example/circlemage/boss/BossZone.java) | 314 | Arena sphere, membership/containment, restrictions, meteor state, rendering particles and serialization. |
| [boss/BossZoneManager.java](../src/main/java/com/example/circlemage/boss/BossZoneManager.java) | 123 | Active-zone registry, lookup, overlap checks, lifecycle/load/save and ticking. |
| [boss/BossZoneState.java](../src/main/java/com/example/circlemage/boss/BossZoneState.java) | 51 | Persistent boss-zone definitions in world data. |
| [boss/MeteorRain.java](../src/main/java/com/example/circlemage/boss/MeteorRain.java) | 196 | Pending meteor warnings, rate/cap logic, particles, impact damage and temporary fire. |
| [CircleMageMod.java](../src/main/java/com/example/circlemage/CircleMageMod.java) | 2074 | Server entry point: registration, packets, player ticks, progression, casting, chants, Laws, infusion, intro and equipment effects. |
| [client/ArcaneStationScreen.java](../src/main/java/com/example/circlemage/client/ArcaneStationScreen.java) | 37 | Client station layout, input/output labels and wand assembly UI. |
| [client/CastingCircleRenderer.java](../src/main/java/com/example/circlemage/client/CastingCircleRenderer.java) | 138 | Telegraphed casting circles for mage/boss spell commitments. |
| [client/CircleMageClient.java](../src/main/java/com/example/circlemage/client/CircleMageClient.java) | 244 | Client registrations, networking, HUD/renderers, item colors and model predicates. |
| [client/ClientImpactState.java](../src/main/java/com/example/circlemage/client/ClientImpactState.java) | 59 | Impact-frame timing and visual state. |
| [client/ClientJournalState.java](../src/main/java/com/example/circlemage/client/ClientJournalState.java) | 50 | Client snapshot of encounter and boss progression for journal pages. |
| [client/ClientManaState.java](../src/main/java/com/example/circlemage/client/ClientManaState.java) | 77 | Client mirror of mana/circle/discovery/chant/Law state. |
| [client/ClientShakeState.java](../src/main/java/com/example/circlemage/client/ClientShakeState.java) | 40 | Timed camera shake state. |
| [client/DarkOrbRenderer.java](../src/main/java/com/example/circlemage/client/DarkOrbRenderer.java) | 95 | Floating/thrown orb and cataclysm rendering. |
| [client/GiveUpScreen.java](../src/main/java/com/example/circlemage/client/GiveUpScreen.java) | 44 | Hardcore Law-of-Life revival dialogue screen. |
| [client/LawSelectionScreen.java](../src/main/java/com/example/circlemage/client/LawSelectionScreen.java) | 91 | Unlocked-Law selection and Storage opening UI. |
| [client/MagicBarrierRenderer.java](../src/main/java/com/example/circlemage/client/MagicBarrierRenderer.java) | 167 | Barrier wall/dome geometry and appearance. |
| [client/MagicGlowParticle.java](../src/main/java/com/example/circlemage/client/MagicGlowParticle.java) | 108 | Custom glowing particle implementation. |
| [client/MagicMissileRenderer.java](../src/main/java/com/example/circlemage/client/MagicMissileRenderer.java) | 155 | Missile/Spear/Fireball projectile appearance. |
| [client/MagicScreen.java](../src/main/java/com/example/circlemage/client/MagicScreen.java) | 154 | Spell form, passive chant and array selection UI. |
| [client/ManaHudOverlay.java](../src/main/java/com/example/circlemage/client/ManaHudOverlay.java) | 104 | Mana meter, circle and regeneration HUD. |
| [client/MasterRenderer.java](../src/main/java/com/example/circlemage/client/MasterRenderer.java) | 37 | Master model/texture and boss rendering. |
| [client/ModKeybinds.java](../src/main/java/com/example/circlemage/client/ModKeybinds.java) | 434 | Bindings, selector casting, charging/combining, Anti Magic hold and carving controls. |
| [client/SpatialRendRenderer.java](../src/main/java/com/example/circlemage/client/SpatialRendRenderer.java) | 83 | Space-cutting projectile rendering. |
| [client/SpellbookScreen.java](../src/main/java/com/example/circlemage/client/SpellbookScreen.java) | 775 | Progress-gated journal, reference pages and NPC/boss lore. |
| [client/SpellHudOverlay.java](../src/main/java/com/example/circlemage/client/SpellHudOverlay.java) | 101 | Spell selector, spell icons and charge/cooldown feedback. |
| [client/SpellSelectorState.java](../src/main/java/com/example/circlemage/client/SpellSelectorState.java) | 148 | Client selected spell and known-spell cycling. |
| [client/TutorialBookRenderer.java](../src/main/java/com/example/circlemage/client/TutorialBookRenderer.java) | 66 | Intro book rendering and animation. |
| [client/WanderingMageRenderer.java](../src/main/java/com/example/circlemage/client/WanderingMageRenderer.java) | 117 | NPC textures by circle/Prodigy and casting appearance. |
| [client/WindBladeRenderer.java](../src/main/java/com/example/circlemage/client/WindBladeRenderer.java) | 24 | Slicing-wind projectile rendering. |
| [command/BossMagicCommands.java](../src/main/java/com/example/circlemage/command/BossMagicCommands.java) | 217 | Operator domains, meteor/rate/restriction controls and Master summoning. |
| [command/ModCommands.java](../src/main/java/com/example/circlemage/command/ModCommands.java) | 228 | Operator progression/testing commands, mage spawning and player Law of Creation command. |
| [entity/DarkOrbEntity.java](../src/main/java/com/example/circlemage/entity/DarkOrbEntity.java) | 784 | Orb movement/fields, spear absorption, staged cataclysm, terrain explosion and aftermath. |
| [entity/MageIdleChannelGoal.java](../src/main/java/com/example/circlemage/entity/MageIdleChannelGoal.java) | 61 | Idle NPC channeling animation; cosmetic, without an NPC mana budget. |
| [entity/MagicBarrierEntity.java](../src/main/java/com/example/circlemage/entity/MagicBarrierEntity.java) | 331 | Wall/dome lifetime, collision, protection and dome hit tracking. |
| [entity/MagicMissileEntity.java](../src/main/java/com/example/circlemage/entity/MagicMissileEntity.java) | 398 | Light/Spear/chaos/fire projectile movement, homing, damage and robe mitigation. |
| [entity/MasterEntity.java](../src/main/java/com/example/circlemage/entity/MasterEntity.java) | 957 | Master engagement, phase/attack logic, movement, resistance, reset, finale, reward and NBT. |
| [entity/ModEntities.java](../src/main/java/com/example/circlemage/entity/ModEntities.java) | 100 | Eight entity type registrations and dimensions/tracking settings. |
| [entity/SpatialRendEntity.java](../src/main/java/com/example/circlemage/entity/SpatialRendEntity.java) | 115 | Spatial Rend sweep, direct destructible-block removal and high magic damage. |
| [entity/TutorialBookEntity.java](../src/main/java/com/example/circlemage/entity/TutorialBookEntity.java) | 186 | Fresh-player book interaction, absorption animation and intro completion. |
| [entity/WanderingMageCombatGoal.java](../src/main/java/com/example/circlemage/entity/WanderingMageCombatGoal.java) | 157 | NPC range decisions, evasion priority and combat navigation/strafe behavior. |
| [entity/WanderingMageEntity.java](../src/main/java/com/example/circlemage/entity/WanderingMageEntity.java) | 830 | Mage variants, spawning, identity, dialogue/trade, guard/surrender, spells, drops and NBT. |
| [entity/WindBladeEntity.java](../src/main/java/com/example/circlemage/entity/WindBladeEntity.java) | 139 | Wind Blade projectile collisions, damage and knockback. |
| [item/ArcaneFocusItem.java](../src/main/java/com/example/circlemage/item/ArcaneFocusItem.java) | 54 | Held cost/damage modifiers and right-click channel toggle. |
| [item/ArcaneStringItem.java](../src/main/java/com/example/circlemage/item/ArcaneStringItem.java) | 20 | Arcane String item behavior/description. |
| [item/FragmentOfLawItem.java](../src/main/java/com/example/circlemage/item/FragmentOfLawItem.java) | 33 | Glinting Master reward and tooltip; no implemented use. |
| [item/MageRobeItem.java](../src/main/java/com/example/circlemage/item/MageRobeItem.java) | 34 | Robe chestplate item and magic-projectile mitigation percentage. |
| [item/MagicTomeItem.java](../src/main/java/com/example/circlemage/item/MagicTomeItem.java) | 125 | School pools, random unknown spell learning, item use and empty-school behavior. |
| [item/ManaCrystalItem.java](../src/main/java/com/example/circlemage/item/ManaCrystalItem.java) | 63 | Permanent crystal attunement, cap and feedback. |
| [item/ManaInfusion.java](../src/main/java/com/example/circlemage/item/ManaInfusion.java) | 33 | Item-to-arcane conversion recipe registry. |
| [item/ManaVialItem.java](../src/main/java/com/example/circlemage/item/ManaVialItem.java) | 94 | Timed drinking, mana restoration, cap and bottle return. |
| [item/ModItems.java](../src/main/java/com/example/circlemage/item/ModItems.java) | 131 | Items, five tome schools, four robes, creative tab and string infusion registration. |
| [item/RobeArmorMaterial.java](../src/main/java/com/example/circlemage/item/RobeArmorMaterial.java) | 65 | Robe armor durability/protection/repair material metadata. |
| [item/WandItem.java](../src/main/java/com/example/circlemage/item/WandItem.java) | 332 | Wand component NBT, ingredient mappings, school bonuses, colors and tooltips. |
| [mana/Circles.java](../src/main/java/com/example/circlemage/mana/Circles.java) | 38 | Nine circle thresholds; ten-billion final threshold and basic gate metadata. |
| [mana/ManaData.java](../src/main/java/com/example/circlemage/mana/ManaData.java) | 729 | Player persistent progress, bit masks, forms, storage and transient casting/Law timers. |
| [mana/ManaPlayer.java](../src/main/java/com/example/circlemage/mana/ManaPlayer.java) | 15 | Interface/access bridge to per-player ManaData. |
| [mana/StorageInventory.java](../src/main/java/com/example/circlemage/mana/StorageInventory.java) | 46 | 27-slot inventory backed by player persistent storage. |
| [mixin/CameraMixin.java](../src/main/java/com/example/circlemage/mixin/CameraMixin.java) | 40 | Apply camera shake during camera updates. |
| [mixin/ClientPlayerFovMixin.java](../src/main/java/com/example/circlemage/mixin/ClientPlayerFovMixin.java) | 25 | Channeling-related field-of-view changes. |
| [mixin/GameRendererAccessor.java](../src/main/java/com/example/circlemage/mixin/GameRendererAccessor.java) | 17 | Access needed for client rendering/post-processing. |
| [mixin/LivingEntityMixin.java](../src/main/java/com/example/circlemage/mixin/LivingEntityMixin.java) | 137 | Player damage hooks: anti effects, resistance, chants, Law tracking/protection/reflection. |
| [mixin/MouseScrollMixin.java](../src/main/java/com/example/circlemage/mixin/MouseScrollMixin.java) | 31 | Spell selector wheel-modifier input interception. |
| [mixin/PlayerEntityMixin.java](../src/main/java/com/example/circlemage/mixin/PlayerEntityMixin.java) | 37 | Attach ManaData and read/write player CircleMage NBT. |
| [network/ModNetworking.java](../src/main/java/com/example/circlemage/network/ModNetworking.java) | 166 | Packet identifiers and server-to-client progress/visual/journal synchronization. |
| [particle/ModParticles.java](../src/main/java/com/example/circlemage/particle/ModParticles.java) | 61 | Custom particle type registry. |
| [spell/AreaEffect.java](../src/main/java/com/example/circlemage/spell/AreaEffect.java) | 146 | Temporary fire/slow/poison field application and particles. |
| [spell/AreaEffectManager.java](../src/main/java/com/example/circlemage/spell/AreaEffectManager.java) | 47 | Global transient spell-field ticking and overlapping-field cleansing. |
| [spell/ArrayType.java](../src/main/java/com/example/circlemage/spell/ArrayType.java) | 26 | Four selectable array effects: Fire, Thunder, Wind, Bind. |
| [spell/Combinations.java](../src/main/java/com/example/circlemage/spell/Combinations.java) | 431 | Seven ordered combination implementations, targeting and shared beam helpers. |
| [spell/CombinationSpell.java](../src/main/java/com/example/circlemage/spell/CombinationSpell.java) | 52 | Combination interface/metadata, pairing and requirement checks. |
| [spell/Law.java](../src/main/java/com/example/circlemage/spell/Law.java) | 37 | Eleven Laws plus None; names/categories for UI. |
| [spell/LawUnlocks.java](../src/main/java/com/example/circlemage/spell/LawUnlocks.java) | 150 | Base/specific Law unlock conditions, hostile checklist and advancement awards. |
| [spell/MagicArray.java](../src/main/java/com/example/circlemage/spell/MagicArray.java) | 170 | Placed array trigger/arming/rearming/lifetime behavior and effects. |
| [spell/MagicArrayManager.java](../src/main/java/com/example/circlemage/spell/MagicArrayManager.java) | 29 | Global transient array registry and ticking. |
| [spell/MagicResistance.java](../src/main/java/com/example/circlemage/spell/MagicResistance.java) | 52 | Resistible damage classification, per-circle resistance and clamping. |
| [spell/PassiveChant.java](../src/main/java/com/example/circlemage/spell/PassiveChant.java) | 27 | Four passive chant selections plus None. |
| [spell/Spell.java](../src/main/java/com/example/circlemage/spell/Spell.java) | 49 | Twelve spell/form IDs, base mana, cooldown and cast-delay metadata. |
| [world/MageTowers.java](../src/main/java/com/example/circlemage/world/MageTowers.java) | 212 | Structure-entry detection, one-time chest population and guardian spawn. |
| [world/WorldProgressState.java](../src/main/java/com/example/circlemage/world/WorldProgressState.java) | 147 | World-wide Prodigy/Master/Lich flags and initialized tower keys. |

## Resource index

The machine-readable [content catalog](content-catalog.json) lists every resource path and full recipe JSON. Key directories:

| Resource path | Contents |
|---|---|
| `fabric.mod.json`, `circlemage.mixins.json` | Entrypoints, dependencies and mixin registration |
| `data/circlemage/recipes/` | 13 crafting definitions; wand assembly and string infusion are code-driven |
| `data/circlemage/advancements/` | Four system discoveries and eleven Law advancement definitions |
| `data/circlemage/structures/` | Four deployed binary NBT templates |
| `data/circlemage/worldgen/` | Structure definitions, template pools and spacing sets |
| `data/circlemage/tags/worldgen/biome/` | Tower biome eligibility |
| `data/circlemage/loot_tables/blocks/` | Arcane Station block drop; chest/tower loot is injected/generated in code |
| `data/minecraft/tags/blocks/` | Station axe-mining tag |
| `assets/circlemage/lang/en_us.json` | English item/spell/key labels |
| `assets/circlemage/models/`, `blockstates/` | Item and station models/states |
| `assets/circlemage/textures/` | Items, NPCs, projectiles, station, HUD/menu and spell icon images |
| `assets/circlemage/particles/` | Particle texture mappings |
| `assets/circlemage/shaders/post/` | Desaturation post-process configuration |
| `assets/minecraft/shaders/program/` | Custom grayscale shader program |
| `assets/minecraft/textures/models/armor/` | Robe armor layers |

## Persistence boundaries

- Player `CircleMage` NBT: mana, circle, realm buffs, learned spell/Law masks, crystals, selections, storage, intro/encounter progress, void/death/hostile tracking, and Thunder Mark.
- World progress: Prodigy alive/defeated, Master defeated, Lich defeated, initialized tower keys. This is shared world state, not a per-player quest chain.
- Boss-zone world data: domain definitions/restrictions/meteor settings. Membership and pending meteor warnings are reconstructed/transient.
- Entity NBT: mage identity/guard data and a subset of Master phase flags. Several attack/surrender fields are transient; see the review.
- Short spell fields/arrays: static transient manager lists. Their server lifecycle cleanup needs improvement.

No behavior changes were implemented. Start future edits with the prioritized [NPC/boss review](NPC_BOSS_REVIEW.md).
