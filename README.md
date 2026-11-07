> Unofficial Minecraft modding project; not affiliated with or endorsed by Mojang or Microsoft. Third-party names and assets remain subject to their owners' rights.

# Circle Mage

## Choose a source variant

CircleMage is one Minecraft magic-mod project with two separately buildable source variants. Do not install both at once: they share the `circlemage` mod ID and target different game/toolchain versions.

| Variant | Source | Declared target |
|---|---|---|
| Original Circle Mage | Repository root | Minecraft 1.20.1, Fabric Loader 0.15.11, Fabric API 0.92.2+1.20.1; mod version 2.3.0 |
| Elemental revamp | [Variant guide](versions/elemental-revamp-mc26.3/README.md) | Minecraft 26.3, loader 0.19.5 and its own Fabric API; version 3.0.0-alpha.2+mc26.3 |

Use Java 17 for the original 1.20.1 project; follow the revamp's own Java/build requirements for its newer target. Run the Gradle wrapper **inside the variant directory you selected**. For the original on Windows:

```powershell
.\gradlew.bat build
```

Install the resulting local mod JAR into the matching Fabric instance with its required Fabric API. Match game, loader and API versions to that variant's `gradle.properties`, rather than combining files across variants. Generated JARs and local Minecraft saves are not part of this source snapshot.

The original feature guide follows. The revamp adds its own elemental-tree/selection workflow and retains a separate README. This is unofficial Minecraft modding work, with no Mojang/Microsoft affiliation or endorsement; existing third-party rights remain with their owners.

A Fabric mod for **Minecraft 1.20.1** that adds a channelled mana system, nine "circles" of
magehood, permanent realm buffs, advancements, and a homing **Magic Missile** spell.

## Controls

| Key | Action |
| --- | ------ |
| **M** | Toggle channelling. While channelling you cannot walk and your mana fills at **+5/sec** (× realm multipliers). |
| **R** | Cast **Magic Missile** (counts as "using magic"). |

## Mana & Circles

- The mana bar sits at the bottom of the screen, to the right of the hotbar. It starts at `0/100`.
- Reaching the threshold ascends you a circle, resets the bar, and the new requirement is shown.
- Open your inventory to see **`Current level: N circle mage`** once you've reached circle 1.

| Circle | Mana required | Extra gate |
| ------ | ------------- | ---------- |
| 1 | 100 | — |
| 2 | 5,000 | — |
| 3 | 20,000 | — |
| 4 | 50,000 | requires the **Basic Anti Magic** advancement |
| 5 | 100,000 | — |
| 6 | 200,000 | — |
| 7 | 400,000 | — |
| 8 | 700,000 | — |
| 9 | 1,000,000 | — |

## Realm buffs (permanent)

Each circle ("realm") has a permanent buff. While **using magic at that circle**, there is a **5%
chance per cast** to permanently unlock it. Each unlocked buff grants:

- **×2** mana gained, and
- **×1.5** magic damage.

These stack multiplicatively across every realm you've unlocked.

## Advancements

- **Basic Anti Magic** — from the **3rd circle** onward, every magic use has a **0.5% chance** to
  unlock it. It is also the key required to ascend to the **4th circle**.

## Magic Missile

- Fires toward your crosshair, **ignores gravity**, and travels up to **50 blocks** before fizzling.
- **Course correction:** if a mob is within **1 block** of your crosshair point, the missile locks on
  and homes toward it.
- Deals **2 hearts** (×1.5 per unlocked realm buff).

## Building

Requires JDK 17.

```bash
./gradlew build
```

The built jar lands in `build/libs/`. To launch a dev client: `./gradlew runClient`.

## Project layout

```
src/main/java/com/example/circlemage/
  CircleMageMod.java          # server logic: channelling, circle ascension, casting, rolls
  mana/                       # ManaData, Circles thresholds, ManaPlayer duck interface
  entity/                     # MagicMissileEntity + registration
  network/                    # packet identifiers + mana sync
  mixin/                      # PlayerEntityMixin (NBT persistence of mana state)
  client/                     # keybinds, HUD bar, inventory level text, missile renderer
src/main/resources/
  data/circlemage/advancements/basic_anti_magic.json
  assets/circlemage/lang/en_us.json
```


## Source variants

- The original Minecraft 1.20.1 Circle Mage source remains at the repository root.
- [Elemental revamp](versions/elemental-revamp-mc26.3/README.md): the separate newer source targets the Minecraft version declared in its own build files (26.3). It retains its own Gradle build and resources.

Build and install one compatible variant at a time. Both use the `circlemage` mod ID and must not be loaded together. The elemental revamp is not a replacement build for the original game version.


## Source snapshot and validation

See [BACKUP_NOTES.md](BACKUP_NOTES.md) for the omitted build outputs, local settings, credentials and runtime data. Build generated binaries locally using the instructions above; a source checkout is not an installed application. Earlier validation figures in this guide describe their original development runs. This documentation update checks source references and instructions; it does not claim a fresh application, device or gameplay test.
