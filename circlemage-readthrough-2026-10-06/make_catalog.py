"""Read originals; create only new catalog/notes files beside this script. No game/build execution."""
from pathlib import Path
from datetime import datetime, timezone, timedelta
from collections import Counter
import hashlib
import json
import re

OUT = Path(__file__).resolve().parent
ROOT = OUT.parent
INSTANCE = Path(r'C:\Users\Arjun\curseforge\minecraft\Instances\magic')
JAVA = ROOT / 'src/main/java/com/example/circlemage'
RES = ROOT / 'src/main/resources'
IST = timezone(timedelta(hours=5, minutes=30))

ROLES = {
    'CircleMageMod': 'Server entry point: registration, packets, player ticks, progression, casting, chants, Laws, infusion, intro and equipment effects.',
    'ArcaneStationBlock': 'Station placement/use and screen-handler opening.',
    'ArcaneStationScreenHandler': 'Component slot validation, wand preview/output, consumption and input return.',
    'ModBlocks': 'Arcane Station block/item and screen-handler registration.',
    'BossZone': 'Arena sphere, membership/containment, restrictions, meteor state, rendering particles and serialization.',
    'BossZoneManager': 'Active-zone registry, lookup, overlap checks, lifecycle/load/save and ticking.',
    'BossZoneState': 'Persistent boss-zone definitions in world data.',
    'MeteorRain': 'Pending meteor warnings, rate/cap logic, particles, impact damage and temporary fire.',
    'ArcaneStationScreen': 'Client station layout, input/output labels and wand assembly UI.',
    'CastingCircleRenderer': 'Telegraphed casting circles for mage/boss spell commitments.',
    'CircleMageClient': 'Client registrations, networking, HUD/renderers, item colors and model predicates.',
    'ClientImpactState': 'Impact-frame timing and visual state.',
    'ClientJournalState': 'Client snapshot of encounter and boss progression for journal pages.',
    'ClientManaState': 'Client mirror of mana/circle/discovery/chant/Law state.',
    'ClientShakeState': 'Timed camera shake state.',
    'DarkOrbRenderer': 'Floating/thrown orb and cataclysm rendering.',
    'GiveUpScreen': 'Hardcore Law-of-Life revival dialogue screen.',
    'LawSelectionScreen': 'Unlocked-Law selection and Storage opening UI.',
    'MagicBarrierRenderer': 'Barrier wall/dome geometry and appearance.',
    'MagicGlowParticle': 'Custom glowing particle implementation.',
    'MagicMissileRenderer': 'Missile/Spear/Fireball projectile appearance.',
    'MagicScreen': 'Spell form, passive chant and array selection UI.',
    'ManaHudOverlay': 'Mana meter, circle and regeneration HUD.',
    'MasterRenderer': 'Master model/texture and boss rendering.',
    'ModKeybinds': 'Bindings, selector casting, charging/combining, Anti Magic hold and carving controls.',
    'SpatialRendRenderer': 'Space-cutting projectile rendering.',
    'SpellbookScreen': 'Progress-gated journal, reference pages and NPC/boss lore.',
    'SpellHudOverlay': 'Spell selector, spell icons and charge/cooldown feedback.',
    'SpellSelectorState': 'Client selected spell and known-spell cycling.',
    'TutorialBookRenderer': 'Intro book rendering and animation.',
    'WanderingMageRenderer': 'NPC textures by circle/Prodigy and casting appearance.',
    'WindBladeRenderer': 'Slicing-wind projectile rendering.',
    'BossMagicCommands': 'Operator domains, meteor/rate/restriction controls and Master summoning.',
    'ModCommands': 'Operator progression/testing commands, mage spawning and player Law of Creation command.',
    'DarkOrbEntity': 'Orb movement/fields, spear absorption, staged cataclysm, terrain explosion and aftermath.',
    'MageIdleChannelGoal': 'Idle NPC channeling animation; cosmetic, without an NPC mana budget.',
    'MagicBarrierEntity': 'Wall/dome lifetime, collision, protection and dome hit tracking.',
    'MagicMissileEntity': 'Light/Spear/chaos/fire projectile movement, homing, damage and robe mitigation.',
    'MasterEntity': 'Master engagement, phase/attack logic, movement, resistance, reset, finale, reward and NBT.',
    'ModEntities': 'Eight entity type registrations and dimensions/tracking settings.',
    'SpatialRendEntity': 'Spatial Rend sweep, direct destructible-block removal and high magic damage.',
    'TutorialBookEntity': 'Fresh-player book interaction, absorption animation and intro completion.',
    'WanderingMageCombatGoal': 'NPC range decisions, evasion priority and combat navigation/strafe behavior.',
    'WanderingMageEntity': 'Mage variants, spawning, identity, dialogue/trade, guard/surrender, spells, drops and NBT.',
    'WindBladeEntity': 'Wind Blade projectile collisions, damage and knockback.',
    'ArcaneFocusItem': 'Held cost/damage modifiers and right-click channel toggle.',
    'ArcaneStringItem': 'Arcane String item behavior/description.',
    'FragmentOfLawItem': 'Glinting Master reward and tooltip; no implemented use.',
    'MageRobeItem': 'Robe chestplate item and magic-projectile mitigation percentage.',
    'MagicTomeItem': 'School pools, random unknown spell learning, item use and empty-school behavior.',
    'ManaCrystalItem': 'Permanent crystal attunement, cap and feedback.',
    'ManaInfusion': 'Item-to-arcane conversion recipe registry.',
    'ManaVialItem': 'Timed drinking, mana restoration, cap and bottle return.',
    'ModItems': 'Items, five tome schools, four robes, creative tab and string infusion registration.',
    'RobeArmorMaterial': 'Robe armor durability/protection/repair material metadata.',
    'WandItem': 'Wand component NBT, ingredient mappings, school bonuses, colors and tooltips.',
    'Circles': 'Nine circle thresholds; ten-billion final threshold and basic gate metadata.',
    'ManaData': 'Player persistent progress, bit masks, forms, storage and transient casting/Law timers.',
    'ManaPlayer': 'Interface/access bridge to per-player ManaData.',
    'StorageInventory': '27-slot inventory backed by player persistent storage.',
    'CameraMixin': 'Apply camera shake during camera updates.',
    'ClientPlayerFovMixin': 'Channeling-related field-of-view changes.',
    'GameRendererAccessor': 'Access needed for client rendering/post-processing.',
    'LivingEntityMixin': 'Player damage hooks: anti effects, resistance, chants, Law tracking/protection/reflection.',
    'MouseScrollMixin': 'Spell selector wheel-modifier input interception.',
    'PlayerEntityMixin': 'Attach ManaData and read/write player CircleMage NBT.',
    'ModNetworking': 'Packet identifiers and server-to-client progress/visual/journal synchronization.',
    'ModParticles': 'Custom particle type registry.',
    'AreaEffect': 'Temporary fire/slow/poison field application and particles.',
    'AreaEffectManager': 'Global transient spell-field ticking and overlapping-field cleansing.',
    'ArrayType': 'Four selectable array effects: Fire, Thunder, Wind, Bind.',
    'CombinationSpell': 'Combination interface/metadata, pairing and requirement checks.',
    'Combinations': 'Seven ordered combination implementations, targeting and shared beam helpers.',
    'Law': 'Eleven Laws plus None; names/categories for UI.',
    'LawUnlocks': 'Base/specific Law unlock conditions, hostile checklist and advancement awards.',
    'MagicArray': 'Placed array trigger/arming/rearming/lifetime behavior and effects.',
    'MagicArrayManager': 'Global transient array registry and ticking.',
    'MagicResistance': 'Resistible damage classification, per-circle resistance and clamping.',
    'PassiveChant': 'Four passive chant selections plus None.',
    'Spell': 'Twelve spell/form IDs, base mana, cooldown and cast-delay metadata.',
    'MageTowers': 'Structure-entry detection, one-time chest population and guardian spawn.',
    'WorldProgressState': 'World-wide Prodigy/Master/Lich flags and initialized tower keys.',
}

def write_new(name, body):
    with (OUT / name).open('x', encoding='utf-8', newline='\n') as handle:
        handle.write(body)

def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()

java = []
for path in sorted(JAVA.rglob('*.java')):
    body = path.read_text(encoding='utf-8-sig')
    java.append({'path': path.relative_to(ROOT).as_posix(), 'class': path.stem,
                 'lines': len(body.splitlines()), 'sha256': sha(path), 'role': ROLES[path.stem]})
assert len(java) == 82 and len(ROLES) == 82

resource_files = sorted(path for path in RES.rglob('*') if path.is_file())
json_files = []
for path in resource_files:
    if path.suffix.lower() == '.json':
        json_files.append((path, json.loads(path.read_text(encoding='utf-8-sig'))))
recipes = [{'path': p.relative_to(ROOT).as_posix(), 'id': p.stem, **data}
           for p, data in json_files if '/recipes/' in p.as_posix()]
assert len(recipes) == 13
resource_counts = dict(sorted(Counter(p.suffix or '(none)' for p in resource_files).items()))
built = ROOT / 'build/libs/circlemage-2.3.0.jar'
installed = INSTANCE / 'mods/circlemage-2.3.0.jar'
catalog = {
    'generated_utc': datetime.now(timezone.utc).isoformat(),
    'project': str(ROOT), 'version': '2.3.0', 'minecraft': '1.20.1',
    'java_file_count': len(java), 'java_line_count': sum(p['lines'] for p in java),
    'java': java, 'resource_file_count': len(resource_files), 'resource_counts': resource_counts,
    'parsed_json_count': len(json_files), 'recipes': recipes,
    'resource_paths': [p.relative_to(ROOT).as_posix() for p in resource_files],
    'built_jar_sha256': sha(built), 'installed_jar_sha256': sha(installed),
    'runtime_testing': 'Not performed; static review and disk-snapshot inspection only.'
}
assert catalog['built_jar_sha256'] == catalog['installed_jar_sha256']
write_new('content-catalog.json', json.dumps(catalog, indent=2, ensure_ascii=False) + '\n')

intro = f'''# CircleMage 2.3.0 — project map

This is a Java/Fabric source project for Minecraft 1.20.1, with **{len(java)} Java files / {catalog['java_line_count']:,} source lines** and **{len(resource_files)} resource files**. Its built 2.3.0 JAR matches the installed mod by SHA-256. The catalog is an inventory, not a fresh compilation result.

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

`gradle.properties` declares Minecraft 1.20.1, Yarn 1.20.1+build.10, Fabric loader 0.15.11, Fabric API 0.92.2+1.20.1, and mod version 2.3.0. The installed CurseForge instance has loader 0.19.3 and API 0.92.9+1.20.1, plus Sodium/Iris. No dependency updates or builds were performed.

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
'''
rows = []
for entry in java:
    relative = entry['path'].removeprefix('src/main/java/com/example/circlemage/')
    rows.append(f"| [{relative}](../{entry['path']}) | {entry['lines']} | {entry['role']} |")
tail = '''

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
'''
write_new('PROJECT_MAP.md', intro + '\n'.join(rows) + tail)

snapshot_path = INSTANCE / 'circlemage-review-2026-10-06/saved-world-inspection.json'
snapshot = json.loads(snapshot_path.read_text(encoding='utf-8'))
assert not snapshot['errors']
worlds = sorted(snapshot['worlds'], key=lambda w: w.get('level', {}).get('LastPlayed', 0), reverse=True)
spell_names = ['Magic Missile', 'Area Fire', 'Area Slow', 'Anti Magic', 'Wall of Wind', 'Thunder',
               'Barrier', 'Spear', 'Dark Orb', 'Dash', 'Wind Blade', 'Fireball']

def player(w):
    return w.get('embedded_player') or (w.get('players') or [{}])[0]

def when(w):
    millis = w.get('level', {}).get('LastPlayed')
    return datetime.fromtimestamp(millis / 1000, IST).strftime('%Y-%m-%d %H:%M:%S') if millis else 'unknown'

def counts(w):
    mages = [e['nbt'] for e in w.get('custom_entities', []) if e['nbt'].get('id') == 'circlemage:wandering_mage']
    masters = sum(e['nbt'].get('id') == 'circlemage:master' for e in w.get('custom_entities', []))
    return len(mages), sum(bool(e.get('prodigy')) for e in mages), sum(bool(e.get('guarding')) for e in mages), masters

def progress(w):
    return w.get('persistent_data', {}).get('circlemage_progress.dat', {}).get('data', {})

notes = '''# Existing worlds — where you left off

Read-only disk snapshot from the installed CurseForge `magic/saves` directory, inspected on 6 October 2026. All 19 folders use the displayed level name **New World**, so the folder suffix is essential. Times are **Asia/Kolkata (IST)**. The currently running game was not queried or controlled; these are saved values, which may lag live play.

The inspector read level/player NBT, relevant persistent data, and saved entity-region records with **zero recorded read/parse errors**. This is not a complete chunk/terrain health audit. Older folders may contain state from older builds and administrative experiments.

## Most recent save: New World (18)

'''
latest = worlds[0]
p = player(latest)
magic = p.get('CircleMage', {})
known = [name for idx, name in enumerate(spell_names) if int(magic.get('unlockedSpells', 0)) & (1 << idx)]
pos = p.get('Pos', [0, 0, 0])
notes += f"""Last saved: **{when(latest)} IST**. Saved player is **circle {magic.get('circle', 0)}**, **{'Creative' if p.get('playerGameType') == 1 else 'Survival'}**, with **{magic.get('mana', 0):,.2f} mana**, **{magic.get('crystalsConsumed', 0)} attuned Mana Crystal**, and learned **{', '.join(known)}**.

Position: approximately **({pos[0]:.0f}, {pos[1]:.0f}, {pos[2]:.0f})**, `{p.get('Dimension')}`. No realm buff, array, passive chant, or Laws discovery is saved. The book intro is complete and the wandering-mage encounter is recorded. The saved progression has three initialized towers, a living Prodigy, and no Prodigy/Master defeat flag. No active boss-zone data was found for this world.

Saved mage locations below are last recorded positions, not guaranteed present-day coordinates. They can move when their chunks tick.

| NPC | Role | Circle | HP | Approximate X, Y, Z |
|---|---|---:|---:|---|
"""
for entity in latest['custom_entities']:
    e = entity['nbt']
    if e.get('id') != 'circlemage:wandering_mage':
        continue
    name_data = json.loads(e.get('CustomName', '{}'))
    name = name_data.get('text', 'Mage').replace('\ufffd', '—')
    name = name.split(' — ')[0]
    role = 'Prodigy' if e.get('prodigy') else 'Tower Guardian' if e.get('guarding') else 'Wanderer'
    xyz = ', '.join(f'{v:.0f}' for v in e.get('Pos', [0, 0, 0]))
    notes += f"| {name} | {role} | {e.get('mageCircle', '?')} | {e.get('Health', 0):g} | {xyz} |\n"

notes += '''

## Choosing a world to return to

- **New World (18):** latest early-game state, with Thunder and one crystal; best match for the most recent session.
- **New World (17):** preceding circle-2 creative session, Magic Missile/Dash and one acquired realm buff.
- **New World (16):** circle 2, three crystals and two acquired realm buffs; an accelerated early-game starting point.
- **New World (11):** circle-3 survival snapshot with arrays discovered; a useful ordinary progression save.
- **New World (9):** circle-9 creative test save, all eleven Laws unlocked and Prodigy/Master defeat recorded; useful for inspecting advanced systems.

These are choices based on saved progress, not a recommendation to overwrite/migrate any world. No save was changed.

## All saved worlds

Realm column counts set circle bits, not the raw integer mask. Mages includes Prodigies and guards. Entity totals are saved custom entity records; they are not measurements of loaded mobs or tick cost. Player mode is the embedded player's saved mode.

| Folder | Last saved (IST) | Circle / mode | Mana | Realm buffs | Crystals | Mages / Prodigies / guards / Masters |
|---|---|---|---:|---:|---:|---|
'''
for w in worlds:
    p = player(w)
    m = p.get('CircleMage', {})
    mode = {0: 'Survival', 1: 'Creative', 2: 'Adventure', 3: 'Spectator'}.get(p.get('playerGameType'), '?')
    realm_count = sum(bool(int(m.get('realmBuffs', 0)) & (1 << n)) for n in range(1, 10))
    c = counts(w)
    notes += f"| {w['folder']} | {when(w)} | {m.get('circle', 0)} / {mode} | {m.get('mana', 0):,.2f} | {realm_count} | {m.get('crystalsConsumed', 0)} | {' / '.join(map(str, c))} |\n"

notes += '''

## Historical inconsistencies to keep in mind

- `New World (4)` contains four saved Prodigy records even though progression does not report a currently alive Prodigy and records defeat.
- `New World (7)` contains two Master records while its Master-defeated flag is already set.
- Some old worlds contain many saved wandering mages (for example, 93 in `New World (8)`). That alone says nothing about current loaded population or lag.

The admin spawn commands bypass natural eligibility and old builds may have had different behavior. These records justify better UUID/stage reconciliation, but do not prove that the current natural-spawn path duplicates bosses.

The raw snapshot is in the separately created `magic/circlemage-review-2026-10-06/saved-world-inspection.json`. This file intentionally summarizes useful progress/locations without reproducing the full inventories or entity NBT.
'''
write_new('WORLD_NOTES.md', notes)
print(json.dumps({'java_files': len(java), 'java_lines': catalog['java_line_count'],
                  'resources': len(resource_files), 'parsed_json': len(json_files),
                  'resource_counts': resource_counts, 'recipes': len(recipes),
                  'worlds': len(worlds), 'snapshot_errors': len(snapshot['errors']),
                  'jars_match': True, 'created': ['content-catalog.json', 'PROJECT_MAP.md', 'WORLD_NOTES.md']}))
