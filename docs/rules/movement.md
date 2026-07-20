# BattleTech: Movement Phase Reference

The Movement Phase is where units reposition on the map. Players do not move all
their units at once — they **alternate**, activating one unit at a time, so each
side reacts to the other's moves as they happen. This document owns the movement
**sequence** (who activates, in what order), the rule for **which units are
activated at all**, the **movement modes** and their MP, and the **cost** of
entering a hex. Heat and damage penalties to MP are owned by their own docs and
are only linked here.

---

## 1. Movement Sequence (Alternating Activation)

Activation order is fixed at the start of the phase from that turn's initiative
result. Players take turns activating **one unit at a time**, and the **initiative
loser activates first** — moving last is the advantage, since you get to react.

When the two sides have unequal numbers of units, the sequence keeps them in step
so both finish together:

* The **loser** activates exactly **one unit per impulse**.
* The **winner** spreads its units across the loser's impulses: **⌊winner ÷
  loser⌋** units per impulse, with any remainder **front-loaded** into the earliest
  impulses.
* If one side has **0** activatable units, the other activates **all** of its units
  in a single impulse.

**Example** — loser has 2 units, winner has 5 (5 ÷ 2 = 2 remainder 1, so the extra
unit lands in the first winner impulse):

| Order | Side   | Units activated |
|:-----:|:-------|:---------------:|
|   1   | Loser  |        1        |
|   2   | Winner |        3        |
|   3   | Loser  |        1        |
|   4   | Winner |        2        |

---

## 2. Activation vs. Movement — Which Units Are in the Sequence

**Activating a unit is not the same as moving it.** When a unit is activated, its
owner *may* declare no movement — a functioning unit that stays put still consumes
its activation. Every powered, pilot-conscious unit takes **exactly one** activation
slot per turn, whether or not it can actually travel.

**Excluded from the sequence entirely** — they take **no** activation slot and do
**not** count toward the loser/winner ratio in §1:

* **Destroyed** units (removed from play).
* **Shutdown** units (powered down — see [`heat.md`](heat.md) §3).
* Units whose **pilot is unconscious**.

**Still activated** (keeps its slot; the owner simply declares no move) — a unit
that is powered on with a conscious pilot but **cannot travel this turn**:

* Reduced to **0 MP by heat** (a heat penalty that zeroes MP does not remove the
  unit from the sequence — see §5).
* **Cannot stand** after a second gyro critical (permanently prone — see §6).

Being immobile is not the same as being out of the sequence: only destroyed,
shutdown, and unconscious-pilot units leave it. Everything else is activated.

> **Timing:** shutdown is resolved during the **Heat Phase**, which runs *after*
> the Movement Phase. The set of excluded units is therefore fixed for the whole
> Movement Phase.

---

## 3. Movement Modes & MP

Each unit has a Movement Point (MP) allowance per mode, taken from its record sheet.
A mode is only available if the unit has a positive MP allowance for it.

* **Walk** — the base allowance.
* **Run** — Walking MP × 1.5, **rounded up** (e.g. Walk 4 → Run 6; Walk 5 → Run 8).
* **Jump** — an independent allowance from jump jets; ignores terrain movement cost
  and can cross elevation freely.

---

## 4. Movement Cost (Terrain & Elevation)

Ground movement (Walk/Run) spends MP to **enter** each hex, based on the terrain
being entered plus any elevation climbed:

| Terrain      | MP to enter |
|:-------------|:-----------:|
| Clear        |      1      |
| Light Woods  |      2      |
| Heavy Woods  |      3      |
| Water        |      2      |

* **Elevation:** add **+1 MP per level of elevation gained** entering the hex.
  Descending costs no extra MP.
* **Facing:** turning to change facing costs **1 MP per hexside** turned, whether
  or not the unit also changes hex.
* **Jumping** ignores this table entirely — it pays from the jump allowance by
  straight-line hex distance.

---

## 5. MP Penalties

Penalties reduce a unit's effective MP but, on their own, never remove it from the
activation sequence (see §2). Their values are owned by other docs:

* **Heat** reduces **walking** MP (and the run derived from it); **jump is
  unaffected**. See [`heat.md`](heat.md) §2 for the −1 … −5 MP table. A heat penalty
  that drops effective MP to 0 still leaves the unit activatable — it simply
  declares no move.
* **Destroyed leg** halves walking MP (floor) and forbids running and jumping. See
  [`armor-damage.md`](armor-damage.md) §8.

---

## 6. Prone & Standing Up

A prone unit cannot move. On its activation it may instead attempt to **stand up**,
which requires a Piloting Skill Roll (see [`pilot.md`](pilot.md) §3):

* **Success** — the unit rises and **may still move** in the same impulse with its
  remaining MP.
* **Failure** — the unit stays prone and its activation is spent.

A unit that **cannot stand** — after a second gyro critical hit (see
[`armor-damage.md`](armor-damage.md) §3) — remains prone for the rest of the game.
It is still activated each turn (§2), but can neither move nor rise.
