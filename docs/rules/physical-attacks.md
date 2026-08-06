# BattleTech Tabletop: Physical Attacks (Punch & Kick)

Physical attacks resolve in their own phase, after weapon fire. They are made against an **adjacent**
target, use the attacker's **Piloting Skill** as the base target number (not Gunnery), and have their
own hit-location tables. This doc owns punch and kick; the fall a failed kick knockdown produces is
owned by [`pilot.md`](pilot.md) §4.

---

## 1. Per-Turn Limits

Enforced per attacking unit, per turn:

- A unit may **punch with each arm** (up to two punches) **XOR kick once** — never both punch and kick
  in the same turn.
- A unit may **not reuse the same limb**.
- A unit may **not use a destroyed limb** (a limb whose internal structure has reached 0).
- A **prone** unit cannot make any physical attack.

---

## 2. Movement Restrictions

The attacker's movement this turn gates which physical attack it may make:

| Attacker moved | Punch  |  Kick  |
|----------------|:------:|:------:|
| Stood still    |  yes   |  yes   |
| Walked         |  yes   |  yes   |
| Ran            |  yes   | **no** |
| Jumped         | **no** | **no** |

A kick may only follow walking or standing still. A jump permits neither punch nor kick — only a
death-from-above.

---

## 3. Reach

The target must be adjacent, and elevation and water depth further restrict reach:

|           | Elevation of target relative to attacker                  | Target water depth                                                         |
|-----------|-----------------------------------------------------------|----------------------------------------------------------------------------|
| **Punch** | within **one level** either way (−1, 0, or +1)            | must be **below 2** — a fully submerged target's upper body is unreachable |
| **Kick**  | **same level or one level lower** (−1 or 0), never higher | must be **0** — submerged legs (depth ≥ 1) are unreachable                 |

---

## 4. To-Hit

Target number = attacker's **Piloting Skill** + the sum of:

| Factor                                      | Modifier                                            |
|---------------------------------------------|-----------------------------------------------------|
| Attacker movement                           | see [`to-hit-modifiers.md`](to-hit-modifiers.md) §2 |
| Target movement (TMM)                       | see [`to-hit-modifiers.md`](to-hit-modifiers.md) §1 |
| Terrain (intervening woods + partial cover) | see [`line-of-sight.md`](line-of-sight.md)          |
| Prone target                                | see [`to-hit-modifiers.md`](to-hit-modifiers.md)    |
| Attacker heat                               | see [`heat.md`](heat.md) §2                         |
| **Attack kind**                             | punch **+0**, kick **−2**                           |

The kick's −2 is the only modifier this doc owns; the rest are shared with weapon attacks and resolve
through the same routines.

---

## 5. Damage

| Attack | Damage             |
|--------|--------------------|
| Punch  | **⌈tonnage / 10⌉** |
| Kick   | **⌈tonnage / 5⌉**  |

Damage is applied to the rolled location per the normal armor → internal structure sequence
([`armor-damage.md`](armor-damage.md) §1), using rear armor when the attack direction is Rear.

---

## 6. Punch Location Table

Rolled on **1d6**, by the target's struck side. Rear uses the same column as Front.

| 1d6 | Front / Rear | Left         | Right        |
|:---:|--------------|--------------|--------------|
|  1  | Left Arm     | Left Torso   | Right Torso  |
|  2  | Left Torso   | Left Arm     | Right Arm    |
|  3  | Center Torso | Left Torso   | Right Torso  |
|  4  | Right Torso  | Center Torso | Center Torso |
|  5  | Right Arm    | Left Arm     | Right Arm    |
|  6  | Head         | Head         | Head         |

---

## 7. Kick Location Table

Rolled on **1d6**. A side kick always strikes the near leg; a front or rear kick hits the right leg
on 1–3 and the left leg on 4–6.

| 1d6 | Front / Rear | Left     | Right     |
|:---:|--------------|----------|-----------|
| 1–3 | Right Leg    | Left Leg | Right Leg |
| 4–6 | Left Leg     | Left Leg | Right Leg |

---

## 8. Kick Knockdown

A kick forces a Piloting Skill Roll for knockdown:

- On a **hit** — the **target** rolls.
- On a **miss** — the **attacker** rolls (the kick threw it off balance).

An already-prone unit makes no roll. The PSR carries all current PSR modifiers (gyro, leg — see
[`pilot.md`](pilot.md) §3). On failure the unit **falls**: fall damage, 1 pilot hit, and a
consciousness roll ([`pilot.md`](pilot.md) §4 and §2).

> Related: forced PSRs and falls ([`pilot.md`](pilot.md)), damage resolution and hit location
> ([`armor-damage.md`](armor-damage.md)), shared to-hit modifiers
> ([`to-hit-modifiers.md`](to-hit-modifiers.md)).
