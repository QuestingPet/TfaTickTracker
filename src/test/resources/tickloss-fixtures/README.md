# Tick loss fixtures

Slices of real raids, cut by `LogFixtures` and replayed by `FixtureReplayTest`.
They exist so the regression net does not depend on the raw recordings, which
reached 3.7 GB across 72 raids and cannot be checked in. These 3.6 MB are the
authority on regressions: change a rule and the exact numbers nine real fights
produced have to still come out, or the build says which one moved and by how much.

Each file opens with a generated checkpoint -- the room, the health-bar varbits and
the NPCs that were already alive -- because a raw tick range would be missing all
of it. After that the lines are verbatim, minus the varbits and other players'
events that cannot change the outcome.

Regenerate (needs the original logs, which only live on the machine that
recorded them -- enable **Debug** -> `Event Logger` to record more):

    TICKLOSS_FIXTURES=write ./gradlew test --tests '*FixtureReplayTest*' -i

| fixture | why |
|---|---|
| `archive-guardian` | The first room: window opens on the TFA_GUARDIAN transform, no start grace. |
| `bork-and-smorks` | Two health bars. The Smorks drops target all fight and either one can die first. |
| `bork-dud-shot-and-eat` | A crossbow fired with no ammo, then an anglerfish onto the cooldown it started. |
| `moia` | Ordinary health-bar room, for the plain open and close case. |
| `xarth-corpse-freeze` | Freezing a corpse resets the cooldown, so attacks land early and must not be charged. |
| `ocellus-all-phases` | P1 handing over, P2 opening a tick late, P3 quadrants, the void explosion run, and the settle when a quadrant dies while you are ready. |
| `no-ammo-repro` | Seven dud shots in a row, deliberately reproduced at the Archive Guardian. |
| `player-death` | Dying mid fight. ActorDeath lands on the killing hitsplat tick, so whatever wait was outstanding at that moment is settled and anything after it is not. |
| `no-ammo-and-fight-end` | Dud shots with a large settlement at the end, where the fight stopped mid cooldown. |
