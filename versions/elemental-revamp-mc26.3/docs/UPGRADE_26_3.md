# Circle Mage V2: Minecraft 26.3 port

Version: `3.0.0-alpha.2+mc26.3`. Loader: Fabric. Runtime: Java 25.

## Scope

The port retains the elemental spell tree and loadouts, mana absorption and nine circles, spells and combinations, arrays, chants, laws, infusion, wands, robes, assembly station, tutorial book, journal, towers, wandering mages, guardians, Prodigy and existing Master encounter. The Lich remains a progression placeholder, as in the source; this upgrade does not invent an unfinished boss.

The underlying Java names, networking, saved data, item components, armor assets, recipes, advancement resources, particles, input, menus, entity render states and post effects were updated for 26.3. Procedural spell meshes and NPC telegraphs are extracted into immutable render data. Wand tint and crystal-tip selection use current item models/components. Steam fog and camera shake use the current renderer hooks.

Spell volleys use the current damage-cooldown field so independent ice and wind hits retain their damage. Existing custom item data in Law of Space storage is upgraded with Minecraft's data fixer before decoding, preserving slot, count and wand components. Entity and player custom save keys remain intact. Legacy Vitality modifiers also recognize Minecraft's converted UUID identifier.

## Install

Create a separate Minecraft **26.3** Fabric profile with **Java 25**, **Fabric Loader 0.19.5+** and **Fabric API 0.162.0+26.3**. Put the new non-sources JAR from `dist` into that profile's `mods` folder. Keep only one mod with ID `circlemage` in a profile. The old 1.20.1 JAR remains available for the old game version.

Start a new world for the first play session. If moving an old world, use a copy: upgrading Minecraft changes vanilla world data and cannot be undone by swapping the mod JAR back. The focused legacy-data tests do not establish that every old world or modpack migrates correctly.

## Preservation

`C:\Users\Arjun\MAgicModSource` remains the read-only reference. All changes are in `ModMagicV2`. The installed CurseForge instance was not used for the tests. Before the port, source/configuration/docs/release artifacts were archived in `backups/before-26.3-2026-10-14.zip`, with CRC and SHA-256 verification. `reference-verification.json` records fresh comparison of all 262 original files and the four tower structures.

## Verification

The release build passed. **20 Circle Mage server GameTests plus one vanilla control passed, with zero failures.** The isolated 26.3 client test also passed on the AMD Radeon OpenGL backend. Screenshots were visually inspected; the test additionally asserts that the renderer applies the desaturation effect.

| Check | Evidence |
| --- | --- |
| Progression, loadouts, spell eligibility and cooldowns | Server GameTests |
| Bubble, ice/wind volleys, earth attacks, roots, steam, soothing, rebirth and dome behavior | Server GameTests |
| Mage trading, surrender, guardian Dispel insight and boss/guardian saved state | Server GameTests |
| Legacy Law of Space inventory, wand components, slot/count retention and bench consumption | Server GameTests |
| All four tower templates loading and preserving exterior terrain | Server GameTests and NBT comparison |
| Real client-to-server cast and server-to-client mana state | Client GameTest |
| Journal, magic tree, assembly screen, wand/robe appearance, spell/NPC gallery, tutorial book and grayscale impact | Client GameTest and five inspected screenshots |
| Release version, Java 25 class format, required assets, excluded test code, JAR CRC and copied SHA-256 | Packaging audit |

The machine-readable report is [release-verification-26.3.json](release-verification-26.3.json). Logs and XML are in [validation-26.3](validation-26.3), and captures are in [screenshots-26.3](screenshots-26.3).

The development run logs include offline account/Realms authentication messages and a Fabric test-default anisotropic-filter warning; these did not prevent the test world or render checks. The server also emits a development warning about untranslated item tags. Gradle wrapper generation needed its optional URL HEAD check skipped in this environment; the distribution itself was downloaded and SHA-256 verified, and the generated wrapper retains URL validation and checksum verification. Automated tests exercise defined scenarios; they do not replace long survival sessions, a full Master fight, third-party modpack compatibility or an external multiplayer playtest.

Build with JDK 25: `gradlew.bat build`. Server tests: `gradlew.bat runGameTestServer`. Isolated client tests: `gradlew.bat runVisualCheck`.

The older `VALIDATION.md` and `release-verification.json` describe the historical 1.20.1 release and are not evidence for 26.3.
