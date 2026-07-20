# BattleTech Tabletop: Weapon To-Hit Modifiers (Movement & Range)

A weapon attack succeeds when the attacker rolls **2d6 ≥ the target number**. The base target number
is the attacker's Gunnery Skill, modified by the range bracket, attacker heat, and other factors. This
document covers three core to-hit modifiers driven by **movement** and **range** that apply on top of
the base gunnery + range calculation.

---

## 1. Target Movement Modifier (TMM)

A target that moved is harder to hit. The attacker adds a to-hit modifier based on the number of
**hexes the target moved** during its Movement Phase this turn:

| Hexes moved | To-hit modifier |
|:-----------:|:---------------:|
|     0–2     |       +0        |
|     3–4     |       +1        |
|     5–6     |       +2        |
|     7–9     |       +3        |
|    10–17    |       +4        |
|    18–24    |       +5        |
|     25+     |       +6        |

A target that **jumped** adds an additional **+1** on top of the band above.

**Examples.**
- Target ran 5 hexes → +2.
- Target jumped 4 hexes → +1 (band) +1 (jumped) = **+2**.
- Target stayed stationary → +0.

---

## 2. Attacker Movement Modifier

The attacker's own movement this turn adds a to-hit penalty to its weapon fire. This is separate from
the heat those movements generate.

| Attacker movement | To-hit modifier |
|:-----------------:|:---------------:|
|    Stationary     |       +0        |
|      Walked       |       +1        |
|        Ran        |       +2        |
|      Jumped       |       +3        |

**Examples.**
- Attacker that ran → +2.
- Attacker that jumped → +3.
- Stationary attacker → +0 (combine with the target's TMM and the range modifier for the full number).

**Note.** Spending MP to turn in place — changing the 'Mech's facing without changing hexes,
distinct from a free torso twist — still counts as Walked/Ran (+1/+2) even though 0 hexes were
moved. Spending **zero** MP is Stationary (+0); this covers both an attacker that chooses not to
move and one that cannot move because heat reduced its MP to 0 — the two are identical for this
modifier. Note that a shutdown or otherwise immobile unit, when it is the *target* rather than the
attacker, gets a much larger bonus than Stationary's +0 — see the −4 immobile-target modifier in
[`heat.md`](heat.md#3-mech-shutdown-consequences).

---

## 3. Minimum-Range Penalty

Some weapons — notably LRMs and the PPC — have a **minimum range** and become less accurate when
firing at targets **inside** that minimum range. When the distance to the target is **less than** the
weapon's minimum range, add:

```
minimum-range penalty = (minimum range − distance + 1)
```

At or beyond the minimum range, the penalty is +0.

**Examples.**
- LRM (minimum range 6) firing at a target **2** hexes away → `6 − 2 + 1 = +5`.
- PPC (minimum range 3) firing at a target **1** hex away → `3 − 1 + 1 = +3`.
- LRM firing at a target 6+ hexes away → +0.

---

> Related: the range-bracket modifier and Line-of-Sight terrain modifiers
> ([`line-of-sight.md`](line-of-sight.md)) also feed the same target number.
