> Unofficial Minecraft modding project; not affiliated with or endorsed by Mojang or Microsoft. Third-party names and assets remain subject to their owners' rights.

# Circle Mage

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
