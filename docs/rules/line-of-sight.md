# BattleTech Tabletop: Line of Sight, Terrain & Partial Cover

Terrain between an attacker and its target affects **whether** a shot can be drawn at all and, if it
can, **how much harder** it is to land. Line of Sight (LOS) is traced as a straight line of hexes from
the attacker's hex to the target's hex; the terrain and elevation of the hexes in between — and of the
target's own hex — determine the result.

---

## 1. Intervening Woods (To-Hit Modifiers)

Each hex of woods that the line of sight passes through adds a to-hit penalty, and woods in the
**target's own hex** add the same:

| Terrain     | To-hit modifier (per hex) |
|:------------|:-------------------------:|
| Light woods |            +1             |
| Heavy woods |            +2             |

These accumulate across every intervening woods hex plus the target hex.

**Example.** One intervening heavy-woods hex → +2 to hit (LOS is still drawn).

---

## 2. Blocked Line of Sight

Woods also **block** LOS entirely once enough of it stacks up between attacker and target. As a
working threshold, **two hexes of intervening woods** (e.g. two light, or one heavy plus one light,
depending on edition) accumulate enough cover to break LOS — no shot is possible.

**Elevation** blocks LOS independently: intervening terrain or units **higher** than both the attacker
and the target obstruct the line to a lower target.

**Example.** Two intervening heavy-woods hexes → LOS **blocked**, no shot.

---

## 3. Partial Cover

When a target's lower body is masked by intervening terrain (the target stands one level below an
intervening obstacle, or is otherwise hull-down), it has **partial cover**:

- **+3 to-hit** against the target.
- The target's **legs are protected**: a hit rolled against a leg has no effect (it strikes the
  covering terrain instead) rather than damaging the leg.

Partial cover is also produced by standing in **depth-1 water** — see [`water.md`](water.md).

---

> Related: range and movement modifiers ([`to-hit-modifiers.md`](to-hit-modifiers.md)) feed the same
> target number as the terrain modifiers above.
