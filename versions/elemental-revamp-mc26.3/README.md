# Circle Mage V2

An isolated revamp of CircleMage for **Minecraft Java 26.3, Fabric, Java 25**. Version **3.0.0-alpha.2+mc26.3** replaces player spell selection with a tiered elemental tree while retaining the original mana, items, crafting, towers, mages and boss foundation.

The reference project at `C:\Users\Arjun\MAgicModSource` is read-only. This project builds and runs entirely in `ModMagicV2`; the installed CurseForge instance and its saves have not been edited.

## Play

The release is `dist/circlemage-v2-3.0.0-alpha.2+mc26.3.jar`. Use Fabric Loader 0.19.5+ and Fabric API for 26.3 (0.162.0+26.3). The mod retains the ID **`circlemage`**, so a game instance must load either the original mod or V2. Keep the source JAR out of the `mods` folder.

**J** opens the magic tree. **M** channels mana. **R** casts your selected spell. **Alt + mouse wheel** cycles available spells. Learn spells from tomes, then equip one T1 and up to three T2 spells in J. T3 and T4 need no loadout slot. The **G** shortcut holds Dispel for 0.75 seconds. Other spell shortcuts are available in Controls and begin unbound.

Read [the player guide](docs/PLAYER_GUIDE.md) for progression, costs, controls and retained systems. Read [the additional design choices](docs/DESIGN_CHOICES.md) for the assumptions I made where the request left room for judgment. [NPC changes](docs/NPC_CHANGES.md) and [26.3 upgrade notes](docs/UPGRADE_26_3.md) describe the fixes and verification limits.

## Build and develop

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
.\gradlew.bat runClient
```

Set `JAVA_HOME` to a JDK 25 installation before building. Loom is pinned to 1.17.21 and the Gradle wrapper to 9.6.0. Minecraft 26.3 supplies official class names directly; this project no longer uses Yarn mappings.

`runGameTestServer` uses `build/gametest26_3` and writes `build/gametest-results.xml`. Test classes and resources are excluded from the release JAR. `runVisualCheck` uses Fabric's client GameTest API to create a separate test world, exercise packets and render screens/entities, capture screenshots, then exit. It uses `build/visualcheck26_3` and never opens your installed instance.

The earlier 1.20.1 release remains in `dist`. `backups/before-26.3-2026-10-14.zip` contains the verified pre-upgrade source, configuration, docs and artifacts.

`tools/tower_nbt.py` inspects the deployed tower templates; `--apply` removes only exterior air. `tools/verify_reference.py` checks the preserved original files and NBT structural content against the initial copy manifest. The original pasted request is retained in [USER_REQUEST.txt](docs/USER_REQUEST.txt).

The spell catalog lives in `spell/SpellCatalog.java`, base prices in `spell/Spell.java`, new abilities in `spell/RevampMagic.java`, and finite-lived spell volumes in `entity/ArcaneEffectEntity.java`. Older boss-only spell implementations remain available internally for the imported NPC/boss systems; player packets are limited to the new catalog.
