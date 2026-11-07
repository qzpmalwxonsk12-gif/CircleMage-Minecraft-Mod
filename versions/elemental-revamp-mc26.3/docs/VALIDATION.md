> Historical 1.20.1 validation. For the current port, see [26.3 upgrade and validation](UPGRADE_26_3.md).

# Validation and playtesting

## Automated coverage

The isolated Fabric GameTest server exercises the actual Minecraft 1.20.1 classes and server mixins. The suite covers:

- fresh-player knowledge, T1 replacement, the three-T2 cap and bypass behavior;
- circle and knowledge checks, authoritative cast rejection, costs and cooldowns;
- loadout/epiphany persistence, death copying and older player-data defaults;
- mana-only circles 1–3 and the fourth-circle advancement gate;
- no Anti Magic discovery before epiphany, then deterministic seeded insight rolls;
- real tome consumption, exhausted-tome retention and `/breakmagiclimit` execution;
- Bubble's 6 HP total, immediate suffocation, expiry and restored air;
- Spike/Fist damage, all three ice hits, stacking slow and root expiry;
- Steam's 15 damage pulses, lifetime, NPC sight, lock loss and reacquisition refusal;
- Soothing's cast restriction, fire/Steam immunity and doubled actual passive mana flow;
- Rebirth's exact health payment, beneficial-effect retention, web removal and bubble cleansing;
- guardian ignition cleansing and persistent witness epiphany;
- Dome containment, expiry and removal by Dispel;
- all four tower NBT templates loading, placing and preserving an exterior terrain sentinel;
- ground selection underneath a target rather than an overhead ceiling.

The XML result is `build/gametest-results.xml`. The final counts and artifact hashes are recorded in [release-verification.json](release-verification.json).

`runVisualCheck` renders the actual Minecraft client journal at circles 1 and 3, its Steam details, its notes, and the registered spell meshes. The screenshots are preserved in [screenshots](screenshots/tree-circle3.png). Visual inspection corrected the slot-counter contrast, note width and preview scaling. This is client rendering verification, not an interactive survival playthrough.

The reference check hashes all **262 initially copied original files**, including all 250 original source/resource files. [reference-verification.json](reference-verification.json) records any changes; the expected list is empty. The NBT verification also compares every non-air block record, palette, entity list and other template metadata against the original. All 90 main resource JSON files are parsed, accepting the original UTF-8 BOMs where present. Release JARs receive a ZIP CRC check, metadata/content inspection and fresh copy hashes.

## Manual playtest checklist

1. In a separate test instance, start a fresh survival world. Verify the tutorial, the M channel toggle, 100-mana circle 1, and only Magic Missile equipped.
2. Learn each school's T1 spell, change T1 in J, and confirm the previous one becomes unavailable from both R and its assigned quick key. Reach T2, equip three, and check the fourth cannot bind.
3. Test Dome at an enemy and while looking down, including jumping, projectiles, narrow terrain and multiplayer movement correction. Check moving target bubbles/vines and aimed Mark.
4. Ignite a natural intact-tower guardian. Verify the epiphany chat, persistent state after reconnecting, normal casting insight, and fourth-circle ascent.
5. Generate intact, broken and underground special towers in fresh chunks on plains and uneven terrain. Inspect their doors, room clearing, underground access and surrounding ground. The automated sentinel test does not cover every natural terrain profile.
6. Cast Steam with and without pre-cast Soothing. Check thick fog, damage, the formation second, effect expiry and NPC behavior. Existing projectiles/area attacks can continue; the implemented restriction covers acquisition and aimed sight.
7. Test J conflicts with the actual modpack, wands/cost discounts, high-GUI-scale HUD layouts and other resource packs. Shader mods may replace the vanilla fog pipeline and need compatibility work.
8. Play the preserved Prodigy and Master encounters with the new loadouts. Check difficulty, special phases and several players hiding in separate Steam clouds.

Multiplayer packet synchronization, every third-party AI implementation, shader/modpack compatibility, every terrain shape, and full survival/boss balancing remain untested. The release is an alpha with automated core-mechanic coverage. No installed game world was opened or modified.
