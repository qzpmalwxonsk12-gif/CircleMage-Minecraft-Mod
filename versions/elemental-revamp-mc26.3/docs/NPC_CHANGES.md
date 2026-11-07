# NPC changes in V2

The original Wandering Mage, Prodigy and Master implementations were retained as the world/boss foundation. Changes below support the new magic rather than redesigning their entire encounter systems.

- **Tower guardian discovery:** a burning guardian cleanses fire/harmful effects, dispels intersecting new spell effects and grants the persistent epiphany to nearby visible witnesses. The guardian has an eight-second Dispel cooldown that survives saving.
- **Persistent guarding:** guardians are excluded from the old low-health surrender path, idle mana-channeling and wandering goals, so they retain their post and hostile role. Their combat target is dropped beyond the guard's chase leash.
- **Mage range gap:** the midrange attack band now covers 5–20 blocks instead of leaving that band without a useful casting choice. Burn and close-range wind attacks require sight.
- **Steam and domes:** mob sight checks respect these spell volumes. Existing locks and navigation are cleared; new locks through an obscured segment are refused. The Master's manual local target fallback also checks sight before aimed combat, preventing it from bypassing that shared rule. A hidden player can keep an encounter alive without being aimed at.
- **Guardian initialization retry:** an intact/special tower is marked initialized only after a guardian spawn succeeds and its loot is populated. A rejected spawn can be retried instead of permanently leaving a guardianless tower.
- **World cleanup:** original area-effect Dispel now checks the player's actual world, and transient area/array lists are cleared when the server stops to avoid retaining old worlds between single-player sessions.

Automated tests cover a guardian cleansing ignition and awarding epiphany, Steam blocking a mob's sight/acquisition and clearing its existing target, dome containment/expiry/Dispel, and the four templates preserving an exterior terrain sentinel when placed. These checks do not establish complete Master/Prodigy encounter balance or every third-party NPC's targeting behavior.

Further boss work can build on the earlier source audit in `C:\Users\Arjun\MAgicModSource\circlemage-readthrough-2026-10-06\NPC_BOSS_REVIEW.md`. That audit's other findings remain review items rather than promised fixes in this magic-focused alpha.
