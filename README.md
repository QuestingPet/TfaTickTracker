# Tfa Tick Tracker

Measures the attack ticks you did not use, room by room, in The Fractured Archives.

A tick you were off cooldown, in a live fight, with something to hit, and did not attack is a tick
lost. The plugin counts those, tells you what each attack cost the moment it lands, and gives you a
per-room summary afterwards — in an overlay while you raid, and in a sidebar panel that breaks the
raid down by raider and by room.

It measures **your own** attacks only. Attack speed, weapon and attack option are not readable for
other players, so everyone else's numbers are self-reported over the RuneLite party rather than
observed. Room totals only ever leave your client, never anything per-attack.

## What counts as lost

The cooldown is the whole model. Every attack commits you for its weapon's speed; being past that
with a target up and nothing fired is a tick lost. Eating adds to the cooldown and is charged
separately so you can see what the food cost. Drinking does not. Some things take the chance to
attack away rather than wasting it — the boss leaping out of reach, a phase handing over — and
those are not charged.

A gap is only ever billed when something closes it: the next attack, the boss dying, or your own
death. Until then the number is not known, so nothing is claimed.

## Credit

The raid plumbing — room detection, the raider list, the raid lifecycle events — comes from
[Tfa Mistake Tracker](https://github.com/QuestingPet/TfaMistakeTracker), where this started life as a
prototype before moving here.
