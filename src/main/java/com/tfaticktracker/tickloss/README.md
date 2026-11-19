# Tick loss

How this plugin decides a tick was lost, and why each rule is the way it is.

Nearly everything below was measured from recorded raids rather than reasoned about, and the
numbers are kept with the rules so that a future change has something to argue against. Where a
rule looks arbitrary, there is usually a log line in the history that made it so.

## What it is

Measures the **local player's** unused attack ticks, per raid room and per boss phase. A tick you
were off cooldown, in a live fight, with something to hit, and did not attack is a tick lost.

It started as a prototype inside
[Tfa Mistake Tracker](https://github.com/QuestingPet/TfaMistakeTracker), which is where the raid
plumbing comes from: `RaidState`, `RaidRoom`, the raid events and `EventLogger` were carried over
wholesale and are not specific to counting ticks. Keeping the prototype's dependency on them
one-way is what made the move a copy rather than a refactor.

## Why it is local-player-only

For another raider you *can* read animation, target, spotanims, visible hitsplats and
`PlayerComposition.getEquipmentId(KitType.WEAPON)`. You **cannot** read their attack style
(`VarPlayerID.COM_MODE`), spellbook, autocast or spec energy.

That is fatal rather than inconvenient:

- Rapid vs Accurate is one tick in three and is invisible from the scene.
- The Obligator is 3t on the smash and 5t on the multi-target thrust. The animations are
  distinct, but when the animation is masked — summoning a thrall, eating on the same tick —
  the only recovery is the attack option, which is local-only. `FracturedArchive` in
  `ngraves95/attacktimer` does exactly this and hits the same wall.
- A masked animation means no attack is recorded at all, so ticks used are undercounted and
  loss is **overstated**. That is the worst direction of error for a number people compare in
  a team.

So other raiders' numbers are self-reported over `PartyService` — each client computes its own
with full varbit access and broadcasts a per-room summary. Scene observation of other players is
deliberately not used, and would also sit badly with the plugin-hub rule against crowdsourcing
data about other players. See **Party integration** below.

## Party integration

`TickLossParty` and `TickLossRoomMessage`. One message per room, sent when a segment closes.

**A member counts only if they are in your party AND a raider in your raid.** Both, never either.
The RuneLite party has nothing to do with who is in the instance, so a party-only filter puts
someone else's unrelated raid in your table as though it were comparable. The raid side comes from
`RaidState.getRaiderNames()`, which reads the game's own raider name varcs; the party side from
`PartyService.getMemberById(...).getDisplayName()`. Both are run through `Text.sanitize` before
comparing.

**Only room totals cross the wire.** Room, phase, counted, lost, lostToEating, lostToDudShots,
attacks, earlyAttacks. Never the `Moment`, the cooldown, an animation id, a weapon or a varbit —
those are the debug vocabulary, and the published overlay does not show them even for yourself.
Sending on close rather than live is the same boundary: a running broadcast of the room you are
standing in *is* per-attack information.

Four decisions worth keeping:

- **`room.name()`, never the ordinal.** Reordering `RaidRoom` would silently relabel everyone's
  history. A name the receiver cannot parse leaves the row in place with an unknown label, because
  the numbers are still theirs and still comparable.
- **No player name in the payload.** `PartyMemberMessage` already carries `memberId`; the receiver
  resolves the display name locally. We never put a name on the wire ourselves.
- **A protocol version.** Members run different builds. Gson ignores unknown fields, so new sender
  to old receiver is safe; the dangerous direction is the reverse, where a field the sender never
  had deserialises to 0 and the receiver renders a confident "0 due to no ammo" that is a lie by
  omission. `TickLossRoomMessage.VERSION` lets a receiver show nothing where the sender could not
  have known.
- **Everything arriving is untrusted.** `isPlausible()` is the arithmetic gate — the same
  invariants the fixtures assert of us, plus a magnitude ceiling so one hostile message cannot
  swamp a running total. A member who fails it is ignored rather than clamped, and none of it is
  ever allowed to touch our own figures. It is display only.

Late joiners need no catch-up protocol: they start appearing from the next room that closes, which
is what every sender does anyway. Rate is a non-issue at roughly sixteen messages a raid, against
the party panel's every two ticks.

Two config items: **Show Party** (Display section, off by default, because it is the one section
whose height depends on how many people are in the party) and **Share With Party** (on by default).
No IP warning is required — that rule is for third-party servers, and the party server is
RuneLite's own.

What it shows, in the same overlay rather than a second one:

```
Party              last  raid    eff
Greg M               24    39  91.1%
Dps11                31    52  88.2%
Norazo                9     9  95.9%
```

The last room that finished, the raid so far, and overall efficiency, under a heading that names
the columns. The full per-member, per-room grid lives in the sidebar panel (`TickLossPanel`),
which pivots either way round: raiders holding rooms, or rooms holding raiders.

## How the fight window works

`RaidNpcs` is the authority, not the boss health bar. The window is open while something in the
room's **required** set is alive, because every boss presents its real target by *transforming*
into it, so `NpcChanged` lands exactly on the tick the fight becomes live.

Established from the event logs in `debug-logs/`:

| room | what happens | why the health bar alone is wrong |
|---|---|---|
| Archive Guardian | spawns as `TFA_GUARDIAN_START` on room entry, becomes `TFA_GUARDIAN` 55 ticks later | region entry would charge the walk-in as loss |
| Ocellus | P1 → P2 → P3 by `NpcChanged`; `HPBAR_HUD_BASEHP` moves on P1→P2 but **not** P2→P3 | base HP misses a phase, and cycles on non-phases like the Guardian's shield |
| Ocellus P2 | damage goes to `TFA_OCELLUS_P2_SWORD_HITBOX`, which spawns 7 ticks after the transform | those 7 ticks are unavoidable and must not count |
| Xarth | HP hits 0 and `ActorDeath` fires at the same tick; the bar clears **14 ticks later** | corpses are still attackable, so "anything attackable" is wrong too |
| Dreadnoth | never fires `ActorDeath` — it splits into jellies; the bar clears ~9 ticks *before* the last jelly dies | the bar closes early here and late at Xarth |

Hence the three conditions, and the two hand-maintained sets:

- `REQUIRED` — presence means the fight is live. Excludes `TFA_XARTH_CORPSE` (optional) and
  `TFA_OCELLUS_P2` (not the damage target). Includes `TFA_JELLY_MINION` (must die).
- `ENDS_FIGHT` — this NPC dying closes the window immediately. Dreadnoth has no entry on
  purpose.
- `EXTRA_BAR_NPC` — the NPC whose life the HUD's *second* health bar tracks. Bork only.

### The Smorks, and the raid's second health bar

Bork is the one room with two fight-ending NPCs, and the Smorks is the one required NPC in the
raid whose death the client cannot see directly. Across 166 Bork fights in `debug-logs/` it fires
`ActorDeath` **zero** times, plays no death animation, never transforms, and goes on targeting
players and dropping them right up to the room change. It just despawns, 9 or 10 ticks after its
last hitsplat.

The signal is the HUD's extra health bar — `HPBAR_HUD_EXTRA_CURRENT` (16570) over
`HPBAR_HUD_EXTRA_BASE` (16571) — which at Bork is the Smorks' own bar and runs the whole room.
It reads 0 on the Smorks' last-hitsplat tick in 149 of 157 fights, one tick *before* it in 7
(those being hits already in flight, so the bar is the more accurate of the two), and it **never
once reads 0 early**, at any point in any fight.

Measured over 158 fights, against the two alternatives:

| end rule | dead ticks charged | real attacks shut out |
|---|---|---|
| extra bar | **0** | **0** |
| the despawn | 1394 | 0 |
| "a straggler drops its target" | 2 | **1739** |

That last row is a rule this file used to recommend, and it was wrong: the Smorks drops its
target *throughout* the fight. In `tfa-events-20261101-090727.log` Bork dies at 1789 and the
Smorks goes to null at +4 with the player still hitting it at +10, +12 and +14.

Scoped per room on purpose. Ocellus uses the same extra bar for something that is not an NPC at
all — its base sits at 96 in all 146 Ocellus rooms logged — so reading it as a life bar there
would end the fight on a mechanic.

**Either one can die first.** `bossDeathSeen` only means "at least one fight-ending NPC is down";
the window does not shut until `endsFightAlive` is empty, so whichever dies first, the survivor
keeps it open. Bork dies first in 157 of the 159 fights that show both, the Smorks first in 1,
and in 6 more the Smorks goes down while Bork never does at all.

Corpses and jellies are indistinguishable to the client — both are attackable NPCs that spawn
mid-fight — so this distinction is game knowledge and has to be a table.

Your own death closes the window via `ActorDeath` on the local player, the same signal
`DeathDetector` uses. `Player.isDead()` is not usable for this and `Actor.getHealthRatio()`
returns -1 unless a health bar is currently being transmitted for that actor. `Raider`'s
`GHOST_POSE_IDS` fallback is a leftover from the ToA port and does not apply in TFA; the raid's
own signal is `TFA_CLIENT_SPECTATE_MODE` going to 1 a few ticks after you die, which is blocked
as `SPECTATING`.

The health bar, base HP and `MINIMAP_STATE` are still read: the bar and base HP for the overlay
as a cross-check, and the minimap fade (`value == 2`, 1–6 ticks before `RaidRoomChanged`) to
exclude the room transition.

## The shape of it, and where to put a fix

Four layers, deliberately separated after too many rounds of patching one blob:

| layer | file | depends on | covered by |
|---|---|---|---|
| the cooldown | `AttackTimeline` | nothing at all | `AttackTimelineTest`, 22 scenarios |
| the fight window | `FightWindowState` | nothing but `RaidNpcs` | `FightWindowStateTest`, and `FixtureReplayTest` over 9 recorded slices |
| weapon speed | `AttackSpeed` | nothing at all | `AttackSpeedTest`, 12 cases |
| game knowledge | `RaidNpcs` | nothing | all of the above |
| the adapters | `FightWindow`, `TickLossTracker`, `WeaponSpeeds` | the client | the build |

`AttackTimeline` and `FightWindowState` are pure state machines: facts in, "that tick counted / that was an
attack, N ticks late, M of them the food's fault" out. No client, no events, no RuneLite types. Everything
that decides whether a tick was lost lives in those two, which means both can be driven from a test, or from
a recorded raid, instead of from a live one.

`FightWindow` and `TickLossTracker` do nothing but translate. If a fix belongs in either of them, it is
probably in the wrong place.

### Two ways to check a change

**`AttackTimelineTest`** -- scenarios, written in ticks. Fast, and the place for anything about the cooldown:

```java
new Sim().speed(5)
        .attack(0)          // 954, bow
        .attack(6)          // 960, first cast
        .attack(8)          // 962, second cast, inside the first's cooldown
        .attack(13)         // 967, bow
        .assertLost(1);
```

A failing assertion prints the whole tick-by-tick run, so the scenario diagnoses itself.

**`FixtureReplayTest`** -- nine slices of real raids, checked into
`src/test/resources/tickloss-fixtures/` and replayed through the real `FightWindowState` and the real
`AttackTimeline`. It holds each one's exact numbers plus the invariants, so a rule change that shifts a past
raid fails the build and has to be looked at. It needs no `debug-logs/` and runs in about a second:

```
./gradlew test --tests '*FixtureReplayTest*' -i
```

A log carries what the replay needs, because `EventLogger` is ours and was taught to record it. Each TICK
line now has the animation frame, the facts behind `AttackSpeed` (weapon, its aspeed, style, rapid, the two
casting flags), whether the target is actually attackable, and whether we are in a raid. `MENU` and
`INVENTORY` lines carry the click and the slot diff that together confirm an eat, which the replay
reconstructs the same way the plugin does live -- so eat attribution is covered by the fixtures too, in
bork-dud-shot-and-eat and player-death.

Only *inputs* are logged, never the plugin's conclusions -- a log of what the plugin decided would replay its
own answers back and prove nothing. The one judgement call is `aspeed` and `targetatk`, which are lookups
into item stats and NPC composition: game data the client has and a log otherwise cannot, not decisions.

> **If a replay cannot answer something, log the input rather than guess at it.** `EventLogger` is part of
> this plugin; when the replay was inferring weapon speed from animations, the fix was to record the weapon.
>
> **When a run shows the wrong figure, write the scenario BEFORE the fix.** Ticks, what happened on each,
> and the number it should have produced. That is the mechanism that was missing while the same bugs kept
> coming back.

This replaced a Python reimplementation of the same rules. That copy was wrong three separate times -- it
counted the whole team's attacks as the player's, it detected attacks from events while the plugin polled
per tick, and it had no cooldown-reset branch -- so every time it "verified" a fix across sixty logs it was
measuring something other than the plugin. There is now one implementation.

### The three rules that keep being got wrong

1. **An attack must have STARTED.** An attack animation lingers after the shot that produced it, and
   `AnimationChanged` does not re-fire when an animation repeats with the same id. So "an attack animation is
   showing" is true both for a fresh attack and for one two ticks old, and reading it as an attack invents
   them. `Actor.getAnimationFrame()` settles it: it restarts on a new attack and advances otherwise.
2. **Attacking early is never loss, and ALWAYS re-seeds the cooldown.** Common with any weapon, not just the
   raid's own resets. attacktimer does not do this -- while its holdoff is positive it ignores the poll
   entirely -- so this part is ours and has to stay ours.
3. **Eating adds to the attack cooldown wherever it already stands.** It does not start a timer of its own,
   and the cooldown runs on past zero, so what a mouthful costs depends on how long you had been ready:

   | cooldown at | eat | becomes | ticks lost to the food |
   |---|---|---|---|
   | -1 | shark (3) | 2 | 2 |
   | -2 | shark (3) | 1 | 1 |
   | -2 | karambwan (2) | 0 | none, you attack immediately |
   | -2 | combo (5) | 3 | 3 |
   | -5 | combo (5) | 0 | none |
   | +1 | karambwan (2) | 3 | 2 |

   All of those are in `tfa-events-20261114-115313.log`, and the same arithmetic is the only thing that
   explains combos spread over two ticks: ready 386, anglerfish 387, karambwan 388, bow 391; and ready 396,
   anglerfish 398, karambwan 399, bow 401.

   `eatAdjustedReady` is that counter. `nextAttackTick` is deliberately NOT moved by eating -- it stays the
   weapon's own cooldown, so the wait a mouthful causes still shows up as overshoot rather than being quietly
   absorbed. The two meet at `min(overshoot, pendingEatCost)`, which is what stops eating from ever being
   blamed for more ticks than were actually lost.

   Two earlier versions of this rule were wrong in instructive ways. Booking only the ticks the food added
   *on top of* the running cooldown read a combo eat as "5 lost (4 eat)" when the loss was entirely the
   food's. Flat-rate "the delay, less one if you were off cooldown" got the common cases right by accident --
   the counter was usually at -1 -- but could not explain a karambwan eaten two ticks past ready costing
   nothing at all.

## How loss is counted

```
lost = Σ max(0, attackTick - expectedNextAttackTick)
```

A sum of positive overshoots, **never** window-minus-ticks-used. The raid resets your attack
cooldown in places — freezing a Xarth corpse with the purging staff lets you attack again
immediately — so attacks can land earlier than the weapon's speed. Clamping at zero means that
contributes nothing; a window-minus-used figure would report over 100% efficiency.

### Firing with no ammo burns a cooldown for nothing

A bow or crossbow fired with no usable ammo **does not shoot, but still starts the weapon's cooldown**.
Nothing visible marks it: no animation, no projectile, no hitsplat. The tick reads as idling while
the game has quietly put you on a full cooldown. The only signal is the chat message, and
`DudShot` matches the two stems that cover all four wordings:

| message | when |
|---|---|
| "You'll need to equip some suitable **crossbow bolts / arrows** to use the **crossbow / bow** you're holding." | nothing in the ammo slot |
| "You can't use that ammo with your **crossbow / bow**." | wrong ammo equipped |

A dud **sets** the cooldown from the current tick, the way a real attack does — unlike eating, which
*adds* its delay to the counter wherever that stands. Getting this backwards charges only part of the
cooldown whenever you were idle beforehand.

It is not an attack, so it never increments `attacks`, and it adds nothing to the total: because
`nextAttackTick` is left alone, the whole gap is already charged as the next attack's overshoot. What
it changes is **attribution**, and that is the point. From tick 4740 of
`debug-logs/tfa-events-20261116-215240.log`, a Zaryte crossbow (aspeed 6, rapid, so 5t):

```
t4735  attack                      weapon clock ready at 4740
t4740  DUD -- "can't use that ammo with your crossbow"
       real cooldown runs to 4745, nothing fired
t4743  eat anglerfish              2 ticks left to run, + 3 = ready 4748
t4748  attack                      exactly on time

before:  8 lost (0 to eating)      <- the food looked free: the weapon clock
                                      said we had been ready since 4740
after:   8 lost (3 to eating, 5 to the dud shot)
```

The same thing happens at tick 5362 of that log, and nine more times in a stretch where it was
reproduced deliberately. Across the 71 recorded raids there are **32 dud shots costing 159 ticks**, in
10 of the logs — rare, but it was silently misattributing food every time it happened.

### A fight that ends settles whatever is outstanding

The formula above is charged **by the next attack** — a gap is measured when something closes it.
So when nothing closes it, nothing is charged, and sitting three ticks off cooldown as an Ocellus
quadrant dies used to cost exactly zero.

`AttackTimeline.settle` fixes that: on the tick the window shuts, if the cooldown has already
expired, the outstanding wait is billed with the same arithmetic, the close tick standing in for
the attack tick. It is booked against the segment that owned the last **counted** tick, not
whatever is current — a settlement lands one tick after the window shuts, by which point the phase
label may already have rolled.

Only a close that means the **fight** ended settles anything. `FightWindowState.endsTheFight`
draws the line:

| settles | does not |
|---|---|
| `BOSS_DEAD`, `PHASE_ENDING`, `BOSS_UNATTACKABLE`, `PLAYER_DEAD` | `NOT_IN_RAID`, `SPECTATING`, `NOT_A_FIGHT_ROOM`, `ROOM_TRANSITION`, `NO_TARGET_YET`, `NOT_STARTED`, `OPENING`, `DISABLED` |

The right column is the measurement stopping rather than the fight ending; billing it would charge
for walking between rooms. Death is in the left column because `ActorDeath` on the local player
fires on the tick the killing hitsplat lands — at tick 428 of
`debug-logs/tfa-events-20261114-115313.log` it arrives with the hitsplat that did it — so the
ticks settled are ones you were alive, ready, and not attacking for.

**Food is only charged for the ticks the fight actually had.** A mouthful banks its whole cost the
moment it is swallowed: an anglerfish at −1 banks two ticks. If the boss dies the tick after, only
one of those two ever had a tick to spend, and the other was going to be spent in a fight that no
longer existed. So the part of `eatAdjustedReady` reaching past the close is subtracted back off,
and eating on the very tick the fight ends costs nothing at all.

Adding this moved the 70 recorded raids then on record from 40788 lost ticks to 44083, with
`counted`, `attacks`, `early` and `eat` all unchanged. On the complete frame-logged raids it is small and sits where you
would expect: `20261116-163629` +3, `20261116-172306` +1, `20261115-000949` +38 over 29228 counted
ticks. The large relative jumps are all short fragments of abandoned attempts — `20261114-112954`
is 268 ticks of walking into the Archive Guardian and leaving again, where 11 attacks in an 84-tick
window genuinely is mostly loss. Logs where the replay sees no attacks at all now read as 100%
loss rather than 0%, which is the honest answer for a log with nothing in it.

No attack debt accrues while the window is shut. Without that, the first attack of a fight was
charged for the entire walk-in: in `debug-logs/tfa-events-20261112-141632.log` the room was
entered at tick 962, the fight only became live at 984, and the first attack at 986 scored 20
lost ticks. An eat during a shut window is also discarded rather than banked.

Casting is paced by the spell rather than the weapon, so it is 5t however fast the staff itself
is, with the Harmonised nightmare staff at 4t. Both paths come from DamageHistory: a manual cast
is `MenuOptionClicked` with `"Cast"` and `MenuAction.WIDGET_TARGET_ON_NPC`, good for one attack
per click, and autocast is the `AUTOCAST_SET` varbit together with a `CASTING` or
`DEFENSIVE_CASTING` style. Gating on those rather than on the style alone is deliberate: it keeps
powered staves on their own `aspeed`, since a trident has no autocast spell set.

Combo eating **sums**: a karambwan plus a normal food on the same tick cost 5 delay ticks, not 3.
Measured in the same log -- elder maul (6t) attacks at 333 and 344 around a combo eat at 336, an
11 tick gap. attacktimer accrues eat delays additively for the same reason.

Only **food** is tracked. Brews, restores and other potions are drunk without costing an attack,
so a `"Drink"` menu option is ignored outright.

An eat off cooldown used to be credited as an invisible attack, on the reading that the eat animation
replaces the attack animation. That was wrong: off-cooldown eats show no attack on the eat tick because
no attack was made. In `tfa-events-20261113-172610.log` the eat at 847 is followed by a bow at 850 --
three ticks of food delay -- and the hitsplats confirm nothing was fired at 847. The food's own delay is
now left standing and the next attack attributes its overshoot to eating, so it stays out of the
headline figure rather than being charged to you.

Consequence: the food's own 2/3/4 tick delay never enters the number, so `delayForItem` and the
food id config now only affect what the overlay displays.

Freezing a Xarth corpse costs no tick and does not touch the attack cooldown, so
`HUMAN_SPELLCAST_GRASP` is vetoed. Measured across every log in `debug-logs/`: 309 grasp-then-weapon
transitions never once went below 5t (the bow's own cooldown running underneath), while 291
grasp-then-grasp transitions bottom out at 2t -- click speed, no cooldown at all.

The *cast* being free does not make the *excursion* free, though: you still swap off your weapon
and back. That gap is attributed to `lostToUtility` and kept out of the headline, the same way
eating is. On one Xarth run it was the difference between 19t lost and 3t lost (+16t swap) over
163 counted ticks. Caveat: this is the freeze being free on a corpse, not the spell being free in
general, and other freeze-type spells used the same way need adding to `UTILITY_CASTS`.

A boss cannot be hit on the tick it becomes live, only from the next one, so the tick the window's
conditions first hold is not counted. Without that every fight carried one tick of phantom loss,
worst in the rooms with no opening grace, where it was charged directly.

Ocellus P3 leaps between quadrants, and the arena is empty for about ten ticks each time. The
leap animation (`NPC_OCELLUS_PHASE3_UNLEASH_OUT_90F`) opens a `BOSS_UNATTACKABLE` state that
closes on the boss's next real animation. Three leaps in
`debug-logs/tfa-events-20261112-170638.log` at ticks 1764, 1871 and 1973 account for roughly 30
of that phase's 51 reported lost ticks.

Eat delays are **attributed, not erased**. The Gauntlet Performance Tracker does
`previousAttackTick += foodDelay`, which hides the cost entirely and cannot produce a
breakdown. Here a confirmed eat accrues delay ticks, and when a gap is charged, the overlap is
reported as `lostToEating` and excluded from the headline `lostExcludingEating`. An eat that
happens while already on cooldown costs nothing and is counted as a free eat.

An eat is only counted once the inventory slot actually changes (`MenuOptionClicked` pushes the
intent, `ItemContainerChanged` confirms it), so a click swallowed by a server tick doesn't
award a phantom delay.

`RaidFinished` is posted by `RaidState` when the raid ends, carrying whether the loot room was
reached and how long the raid took. It was added to `RaidState` for this and fills the
`// TODO: RaidFinished?` that was already sitting in `RaidState.locationChanged`.

## Files

| file | job |
|---|---|
| `RaidNpcs` | required / ends-fight / phase-label tables. The game knowledge. |
| `FightWindow` | the `COUNTING` or why-not state machine |
| `WeaponSpeeds` | local attack speed, incl. Obligator 3t/5t with the attack-option fallback |
| `NonAttackAnimations` | veto list so eating isn't read as an attack |
| `EatTracker` | confirmed eats and their delay |
| `SegmentStats` | per room/phase counters |
| `TickLossTracker` | orchestration, `@Subscribe(priority = -2)` so it runs after the detectors |

## Checking a rule change against raids that already happened

`FixtureReplayTest` replays nine slices of real raids through the real `FightWindowState` and
`AttackTimeline`. Run it after any change here -- it takes about a second:

```
./gradlew test --tests '*FixtureReplayTest*' -i
```

**There is no longer any dependence on `debug-logs/`.** It reached 3.7 GB across 72 files, which made the
only end-to-end net slow to run, impossible to check in, and gone the moment anyone tidied their disk. The
fixtures in `src/test/resources/tickloss-fixtures/` replace it at 3.6 MB, and they are the authority on
regressions now. The old golden file over the whole corpus is gone with it.

It does three things:

- **Exact expectations.** Each fixture's segments, lost, counted, attacks, early, eat, dud and dud-shot
  count, as it produced them when captured. A rule change that shifts one fails with an expected/got diff,
  so it has to be looked at rather than noticed a week later.
- **Invariants**, across every segment of every fixture: lost exceeding counted, a negative bucket, more
  early attacks than attacks, attribution exceeding the loss, a segment ending before it starts.
- **Named mechanics.** The dud-shot count, the Xarth corpse-freeze resets, the Ocellus settlements, and that
  a death mid-cooldown settles nothing. These assert the thing each fixture exists for, rather than letting
  it hide inside a total.

A fixture cannot be a raw tick range: by the time a fight starts the log has established the room, the
health-bar varbits and which NPCs are alive, and never restates any of it. So each one opens with a
generated checkpoint of that state, written as ordinary events, and `LogFixtures` cuts them. Regenerating
needs logs again:

```
TICKLOSS_FIXTURES=write ./gradlew test --tests '*FixtureReplayTest*' -i
```

To diagnose one moment -- the "why did that say 8" question -- replay a single fixture or log in detail:

```
PROBE=bork-dud-shot-and-eat TRACE_TICKS=4740-4750 ./gradlew test --tests '*replayOneInDetail*' -i
```

The marker hotkey writes `MARK` into a log, so a moment that looked wrong in game can be found rather than
described; markers are pressed a few ticks after the thing they are about.

What the fixtures do not cover: Dreadnoth, Ezekiel and Dagonhai, which no captured slice contains. Nothing
can be measured across a whole corpus any more either -- the sweeps that found the veto-list gaps and
settled the eat model needed all 72 raids, so repeating that kind of audit means recording fresh logs. The
conclusions they reached are written down where the rule lives: `NonAttackAnimations` for the veto list,
`AttackTimeline.addToCooldown` for the eat model, `DudShot` for firing with no ammo.

A useful trick when diagnosing by hand: to tell "I could not attack" from "I did not attack", look at
hitsplats from the **whole team**. That is how the Ocellus P2-to-P3 transition was found and how Ezekiel was
cleared of the same suspicion. But beware the trap it sets -- hitsplats include projectiles already in
flight, so they are evidence that something was reachable, never evidence of an attack on a given tick.
That confusion produced a wrong Archive Guardian marker, a wrong leap boundary, and a fifty-thousand-strong
measurement of a gap that did not exist.

## Where each room stands

> **The replay is local-player-only, and it must stay that way.** `EventLogger` records every
> player's animations, so counting all of them credits the whole team's attacks against your
> cooldown and loss all but disappears: one raid read 97.8% efficiency over 1008 attacks
> team-wide and 87.7% over 256 once scoped to one player. `replay()` now resolves the local
> player itself, from the one line that identifies them -- a hitsplat *on* a player carries
> `mine=true` only for the local one -- so `--player` is only for overriding it. The plugin
> itself was never affected: `TickLossTracker.onAnimationChanged` returns early unless the actor
> is `client.getLocalPlayer()`.
>
> Related trap, same cause: read attack *starts* from `ANIMATION` events, never the `anim=` field
> on the `TICK` line. `TICK` reports whatever animation is currently playing, and a scythe swing
> spans several ticks, so a snapshot read counts one attack three or four times. That artifact
> made the Ocellus leap boundary look two ticks early (an apparent 164 melee attacks at +0 and
> 125 at +1) when the true figure from the events is two attacks across 383 leaps.

Audited against 55 event logs with three tests:

- invariants, which hold across all of them;
- attacks made while the window said you could not attack, which catches a start or end marker that
  is **too late**;
- counted stretches where nobody landed a hit, which catches a window that is **too wide**.

| room | rooms logged | start marker | end marker | confidence |
|---|---|---|---|---|
| Archive Guardian | 220 | `TFA_GUARDIAN` transform, no grace | `ActorDeath` | good |
| Bork | 166 | health bar | `ActorDeath` **and** the extra bar | good |
| Xarth | 149 | health bar | `ActorDeath`, not the bar (14t late) | good |
| Ocellus | 146 | per phase, see above | `ActorDeath` on P3 | good, P1/P2/P3 split |
| Moia | 109 | health bar | `ActorDeath` | good |
| Dreadnoth | 52 | health bar | minions, never `ActorDeath` | fair, none logged since Nov 4 |
| Ezekiel | 11 | health bar | `ActorDeath` | **thin** |
| Dagonhai | 2 | health bar | `ActorDeath` on the leader | **essentially unverified** |

The health bar is a sound start marker where it is used: measured per room against the first
attack anyone makes, it comes up on or one tick *before* the first attack in every room, and is
late only at Bork, in 4 of 143 fights, by at most 8 ticks.

Two findings worth recording because they corrected earlier conclusions:

- **The Guardian is damageable all through its flight.** It was briefly in
  `LEAVES_UNATTACKABLE`. Team hits on it never stop across 353 takeoffs — 25 at +2 rising to
  ~350 a tick for the full 45 ticks. What stops is *melee*, at +2 to +5, because it flies out of
  reach. Leaving it out is correct.
- **The battery phase is not dead time.** The Guardian takes no hits while the team breaks the
  batteries (`TFA_GUARDIAN_BATTERY_ACTIVE`, "Inert Guardian"), which are not required NPCs. The
  window is correctly open and attacks on them count; it was the quiet-segment test that was wrong,
  because it only looked for hits on required NPCs.

## The first attackable tick

Three separate places were charging a tick that could not be taken. They are different mechanisms and
each needed its own fix.

| | the window opened on | it is hittable on | evidence |
|---|---|---|---|
| Archive Guardian | the `TFA_GUARDIAN` transform | +1 | first attack never at +0 across 204 fights |
| Ocellus P2 | the sword hitbox spawn | +1 | nobody in the team at +0 across 135 spawns; 110 at +1 |
| after any leap or walk | the tick it came to rest | +1 | 6 of 360 quadrants at +0; P3 entry rests at 1440, first attack 1441 |

The first two are `RaidNpcs.START_DELAY`, keyed on the NPC that opens the window rather than the room,
because within Ocellus P2 needs a tick and P3 does not. The third is `ARRIVAL_REOPEN_DELAY` in
`FightWindow.checkArrived`, and it is deliberately separate from `reopenDelayTicks`: arrival and
re-targeting are two different signals that happen to be a tick apart. On the P3 entry Ocellus comes to
rest at 1440 and picks a target at 1441, and the first attack is at 1441 -- so arrival wants +1 while
re-targeting wants +0.

> The replay cannot see "at rest" directly, because `MOVE` only fires when the NPC moves and so two
> `MOVE` lines never share a position. It infers it instead: the tick after the last `MOVE` with no
> further `MOVE` is the moment it stopped. Before that it never exercised the arrival path at all.

## Freezing a corpse is just an attack

There is no special path for it any more, and no "swap" bucket. A freeze-type spell cast on a corpse
ignores the cooldown you are currently on but puts you on its own, and autocast is 5 ticks -- which is
exactly what the ordinary attack path already does:

- cast while off cooldown -> a normal attack, `nextAttackTick = tick + 5`
- cast while ON cooldown -> `detectAttack`'s reset branch, which is never charged as loss and commits
  the same `tick + 5`

So `HUMAN_SPELLCAST_GRASP` is no longer vetoed and `UTILITY_CASTS` is gone. This generalises for free:
any freeze spell, on any staff, works without being named, because nothing keys off the animation.
Casting speed comes from `WeaponSpeeds` as it does for every other attack.

The earlier version special-cased undead grasp and then added a 2-tick swap allowance on top to cover
grasp-to-weapon gaps measuring 7 ticks against a 5-tick model. That allowance was a fudge: the cast's
own cooldown is the whole mechanic, and anything past it is ordinary slowness.

Two things had to be right for the reset branch to carry it:

1. **An attack inside the cooldown always re-seeds it.** This used to sit behind a config toggle; the
   toggle is gone. Skipping the commit left the cooldown stale after a mid-cooldown cast, so the next
   real attack read as late -- the end of Ocellus in `tfa-events-20261113-153822.log` reads 5 lost ticks
   when it re-seeds and 12 when it does not.
2. **Picking out a different NPC counts as a new attack, not just a fresh animation.** `AnimationChanged`
   does not re-fire when an animation is re-set to the value it already holds, so the second cast of a
   pair can produce no animation event at all -- 1022 and 1024 in `tfa-events-20261113-153822.log` share
   one, but each picks a different corpse.

   A spotanim was tried here first and was wrong. Plenty of graphics have nothing to do with attacking:
   spotanim 4200 goes off every 5 ticks all through
   `tfa-events-20261113-162514.log`, and during a blowpipe stretch at the Archive Guardian it invented
   three early attacks at 2030, 2035 and 2040 while the real shots were landing on the even ticks.

### How attacktimer does it, and why we differ

`ngraves95/attacktimer` has **no `onAnimationChanged` handler at all**. It polls
`Attacking.isPlayerAttacking` once per tick, counts a holdoff down, and re-arms when the count reaches
zero. A repeated animation id can never confuse it because it never looks at animation changes, and
while the holdoff is positive it *ignores the poll entirely* -- its `onInteractingChanged` edge trigger
fires only from `NOT_ATTACKING`.

Our main path is the same poll. The difference is that we also need to catch attacks made *inside* the
cooldown, because the raid resets it -- freezing a corpse is a real attack that attacktimer would simply
miss, which is fine for a timer and not for a loss figure. That early branch is the only place we look
inside the cooldown, and it is also the only place this has ever gone wrong. Measured over 58 logs:

| early detection | total lost ticks |
|---|---|
| different-NPC retarget (current) | 19208 |
| fresh animation only | 19777 |
| none at all (attacktimer's model) | 20950 |

Freezes come in pairs, and each pair is perfect play. In that log: casts at 960 and 962 then a bow at
967, 980 and 983 then 988, 1001 and 1004 then 1009, 1022 and 1024 then 1029 -- every one exactly 5 ticks
after the second cast, and all four now cost nothing.

## Ocellus P4's void explosion

It goes off six times, two ticks apart, and each one takes three ticks off your attack cooldown if you step
off the tile it lands on -- so a five tick weapon swings every two. Ticks 1596 to 1608 of
`tfa-events-20261115-000949.log` are seven twisted bow shots through one run.

**Watched directly, not predicted.** `OcellusP3Detector` has to work it out from the siphon animation plus a
hit delay, because it is tracking where every raider in the party will be standing. This only cares about
one person, and for that person the explosion is simply visible: `VFX_VOID_CHAIN_EXPLOSION` fires 3257 times
across `debug-logs/`, EVERY gap is exactly 2 ticks, 517 of 571 runs are exactly six long, and every run
starts on a tick the siphon predicts. You cannot see another raider's, so any one of them is yours.

The reduction is applied **whether or not you actually stepped off it**. That is the point: an explosion you
failed to use is tick loss, and modelling the reset as always available is what makes it read as such
without needing a counter of its own. Being dead is already excluded, because the window stops counting.

One subtlety in `AttackTimeline.reduceCooldown`: only a cooldown still RUNNING is reduced. One that has
already expired is left exactly where it is, so idle time you had already accrued stays yours. Clamping it
forward to the current tick would quietly wipe a tick of that each time, and skipping one attack through a
run would read as one lost tick when it is two.

Modelling it moved 707 attacks at Ocellus P3 out of "early, cause unknown" and into "on time": `HUMAN_BOW`
went from 518 early to 56 and `SCYTHE_OF_VITUR_ATTACK` from 354 to 109. The loss those misses represent now
shows up instead of vanishing into an unexplained reset.

## The veto list is a denylist, so something has to find its gaps

`NonAttackAnimations` is 86 names, generated from the gameval list by matching BLOCK and DEFEND. It will
never be complete: weapons arrive with their own block animations and nothing announces them. An unlisted
one silently counts as an attack and re-seeds the cooldown off a tick spent defending, which **hides real
loss**. That is not a hypothetical -- `HUMAN_SHIELD_DEFENCE` is spelled DEFENCE rather than DEFEND, slipped
through the generator, and accounted for 217 "early attacks" before anyone noticed.

So the audit looks for the gaps rather than hoping the list is exhaustive. It is not currently a
test -- the fixtures are nine solo slices and far too thin to separate a block from an attack --
so re-running it means measuring over `debug-logs/` again. **You only block because something hit you**, so a block clusters near 100% of its starts on
a tick you took damage, while a real attack sits far lower:

| | starts | while being hit |
|---|---|---|
| `HUMAN_SCYTHE_BLOCK` | 2950 | 96% |
| `HUMAN_UNARMEDBLOCK` | 8598 | 89% |
| `HUMAN_SHIELD_DEFENCE` | 441 | 85% |
| `HUMAN_ELDER_MAUL_BLOCK` | 99 | 77% |
| `ZCB_ATTACK_PVN` | 2643 | 55% |
| `SNAKEBOSS_BLOWPIPE_ATTACK` | 1444 | 47% |
| `SCYTHE_OF_VITUR_ATTACK` | 11920 | 33% |
| `HUMAN_BOW` | 23385 | 28% |
| `HUMAN_GLAIVE_RALOS01_CHARGED_SPECIAL` | 968 | 15% |

Anything counted as an attack at 70% or above is flagged. Un-veto the shield and it reports at 82% with a
SUSPECT marker, which is the check that it works. The bands do overlap at the edges, so it reports rather
than fails.

A first attempt asked whether the animation ever DEALT damage instead, and failed completely: the shield
block scored 77%, because mid-fight there are always projectiles landing. Hitsplats say what reached a
target, never what you did on a given tick -- the same trap as always.

## Known gaps

- **Zorya's tome is 3t always.** It is 2t for the three attacks after a landed spec, which needs
  the spec and the hit tracked together. A specced Zorya reads as a few ticks of loss.
- **Rapid needs the style index, not just the style.** On a ranged weapon both Accurate and Rapid
  resolve to `RANGING` (they train Ranged only, so neither can be `ACCURATE`, which carries
  `Skill.ATTACK`). Only Rapid is a tick faster. `AttackStyles#isRapid` requires index 1 *and*
  both slot 0 and slot 1 to be `RANGING`, which separates a real ranged weapon from a hybrid with
  a ranged option at index 1. DamageHistory's comment about this is right.
- **Non-attack animation list is generated, not exhaustive.** 80 entries from the `AnimationID`
  gameval names (blocks, defences, deaths, consumables). A missing entry under-reports loss; a
  wrong entry over-reports it, which is worse, so `HUMAN_CASTING_TELE_BLOCK*` and `HUMAN_PUSH_*`
  are excluded despite matching on name.
- **Nothing is persisted and nothing goes over the party.** In-memory for one raid: the summary
  survives into the lobby after `RaidFinished` and is cleared by the next raid's `InRaidChanged`.
- **Only the Ocellus leap is modelled as unattackable.** Other bosses may have their own
  out-of-reach windows; each needs its animation adding to `RaidNpcs.LEAVES_UNATTACKABLE`.
- **Ezekiel and Dagonhai are barely in the logs** — 11 rooms and 2 rooms. Their start and end
  markers are the ordinary health-bar-and-`ActorDeath` pair and nothing contradicts them, but
  neither has been exercised enough to call verified. Dreadnoth has 52 rooms but none since
  Nov 4.
- **Phases are only defined for Ocellus.** Other rooms are one segment. The Archive Guardian
  and Dagonhai shield cycles are available as transforms if per-cycle segments turn out to be
  wanted.

## Testing

Only the user can verify any of this in game — per `AGENTS.md`, never automate game input.
Run `./gradlew run` and enable the plugin. The overlay is top-left and the panel is in the sidebar.

The event logger and the debug overlay are not on `master`: they were removed for release and live on
the local `debug` branch, which is `master` plus one "Re-add debug tools" commit. Recording a raid, or
cutting a new fixture from one, means working there. See `debug-logs/RESTORE-DEBUG-TOOLS.md`.
