# Existing worlds — where you left off

Read-only disk snapshot from the installed CurseForge `magic/saves` directory, inspected on 6 October 2026. All 19 folders use the displayed level name **New World**, so the folder suffix is essential. Times are **Asia/Kolkata (IST)**. The currently running game was not queried or controlled; these are saved values, which may lag live play.

The inspector read level/player NBT, relevant persistent data, and saved entity-region records with **zero recorded read/parse errors**. This is not a complete chunk/terrain health audit. Older folders may contain state from older builds and administrative experiments.

## Most recent save: New World (18)

Last saved: **2026-07-13 02:41:57 IST**. Saved player is **circle 1**, **Creative**, with **437.05 mana**, **1 attuned Mana Crystal**, and learned **Magic Missile, Thunder, Dash**.

Position: approximately **(-645, 134, 170)**, `minecraft:overworld`. No realm buff, array, passive chant, or Laws discovery is saved. The book intro is complete and the wandering-mage encounter is recorded. The saved progression has three initialized towers, a living Prodigy, and no Prodigy/Master defeat flag. No active boss-zone data was found for this world.

Saved mage locations below are last recorded positions, not guaranteed present-day coordinates. They can move when their chunks tick.

| NPC | Role | Circle | HP | Approximate X, Y, Z |
|---|---|---:|---:|---|
| Aldric | Tower Guardian | 3 | 32 | -220, 72, 527 |
| Zephyra the Prodigy | Prodigy | 3 | 60 | -236, 67, 611 |
| Isolde | Wanderer | 1 | 24 | -610, 64, 102 |
| Xanthe | Tower Guardian | 1 | 24 | -549, 68, 250 |
| Elowen | Tower Guardian | 1 | 24 | 166, 63, 404 |


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
| New World (18) | 2026-07-13 02:41:57 | 1 / Creative | 437.05 | 0 | 1 | 5 / 1 / 3 / 0 |
| New World (17) | 2026-07-13 02:25:34 | 2 / Creative | 1,732.10 | 1 | 0 | 3 / 1 / 0 / 0 |
| New World (16) | 2026-07-08 21:59:22 | 2 / Creative | 14,308.90 | 2 | 3 | 2 / 1 / 0 / 0 |
| New World (15) | 2026-07-06 16:37:00 | 1 / Survival | 86.25 | 1 | 0 | 42 / 1 / 0 / 0 |
| New World (14) | 2026-07-05 23:11:35 | 1 / Creative | 169.60 | 1 | 0 | 1 / 1 / 0 / 0 |
| New World (13) | 2026-07-05 23:05:56 | 2 / Creative | 6,033.95 | 2 | 3 | 3 / 1 / 0 / 0 |
| New World (12) | 2026-07-05 17:22:31 | 2 / Creative | 6,719.55 | 1 | 1 | 1 / 1 / 0 / 0 |
| New World (11) | 2026-07-05 16:47:34 | 3 / Survival | 20,713.00 | 2 | 0 | 2 / 1 / 0 / 0 |
| New World (9) | 2026-07-04 21:41:19 | 9 / Creative | 9,986,245,260.00 | 5 | 3 | 2 / 0 / 0 / 0 |
| New World (10) | 2026-07-02 14:35:07 | 1 / Survival | 817.50 | 0 | 0 | 1 / 0 / 0 / 0 |
| New World (8) | 2026-06-23 10:44:21 | 3 / Survival | 46,750.00 | 3 | 0 | 93 / 1 / 0 / 0 |
| New World (7) | 2026-06-22 20:53:44 | 9 / Survival | 9,999,855,655.00 | 7 | 3 | 4 / 1 / 0 / 2 |
| New World (6) | 2026-06-22 09:29:09 | 8 / Creative | 626,673,148.00 | 3 | 2 | 2 / 1 / 0 / 0 |
| New World (4) | 2026-06-21 14:59:13 | 9 / Creative | 9,982,620,006.00 | 7 | 3 | 4 / 4 / 0 / 0 |
| New World (5) | 2026-06-21 09:50:05 | 5 / Creative | 95,040.00 | 5 | 3 | 1 / 1 / 0 / 0 |
| New World (3) | 2026-06-21 03:07:15 | 9 / Creative | 5,832,800.80 | 8 | 0 | 0 / 0 / 0 / 0 |
| New World (2) | 2026-06-21 01:33:33 | 9 / Survival | 5,831,258,675.00 | 9 | 0 | 0 / 0 / 0 / 0 |
| New World (1) | 2026-06-20 15:10:43 | 8 / Creative | 228,419.00 | 9 | 0 | 0 / 0 / 0 / 0 |
| New World | 2026-06-19 07:06:11 | 9 / Creative | 962,200.00 | 8 | 0 | 0 / 0 / 0 / 0 |


## Historical inconsistencies to keep in mind

- `New World (4)` contains four saved Prodigy records even though progression does not report a currently alive Prodigy and records defeat.
- `New World (7)` contains two Master records while its Master-defeated flag is already set.
- Some old worlds contain many saved wandering mages (for example, 93 in `New World (8)`). That alone says nothing about current loaded population or lag.

The admin spawn commands bypass natural eligibility and old builds may have had different behavior. These records justify better UUID/stage reconciliation, but do not prove that the current natural-spawn path duplicates bosses.

The raw snapshot is in the separately created `magic/circlemage-review-2026-10-06/saved-world-inspection.json`. This file intentionally summarizes useful progress/locations without reproducing the full inventories or entity NBT.
