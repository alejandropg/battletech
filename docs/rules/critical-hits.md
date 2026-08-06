# BattleTech Tabletop: Critical Hits & Component Effects

Every time a Mech's Internal Structure takes damage from a weapon hit, the player must roll **2d6 (two
six-sided dice)** on the Critical Hit Table to see if internal equipment is torn apart.

This doc is the canonical home for the critical hit roll, critical-slot determination, and what each
destroyed component does. Damage getting *to* the internal structure in the first place — armor,
blow-through, hit location — is owned by [`armor-damage.md`](armor-damage.md).

---

## 1. The Critical Hit Table

| 2d6 Roll     | Result                | Tactical Effect                                                                               |
|:-------------|:----------------------|:----------------------------------------------------------------------------------------------|
| **2 to 7**   | No Critical Hit       | The structure holds; internal components are unharmed.                                        |
| **8 or 9**   | 1 Critical Hit        | One random component in that location is destroyed.                                           |
| **10 or 11** | 2 Critical Hits       | Two random components in that location are destroyed.                                         |
| **12**       | Head / Limb Blown Off | **Head/Torso:** 3 Critical Hits.<br>**Arm/Leg:** The limb is completely severed and detached. |

> **Crucial Rule:** You must roll on this table *every single time* a location takes internal structure
> damage from a new weapon attack, even if it is only a single point of damage.

A natural 2 on the hit location roll also triggers an automatic critical check even through intact
armor — see [`armor-damage.md`](armor-damage.md), *The Natural 2 Rule*.

---

## 2. Determining Which Component is Hit

Every Mech has a Critical Slot Table on its record sheet split into sections matching its limbs and torso. Each section has 12 slots (divided into two blocks of 6). **The blank framework is identical for every Mech**, but **the items written inside those slots are completely unique** to each specific Mech design and variant.

If you scored a critical hit, you determine the exact item destroyed by rolling dice:

1. Roll a 1d6 to choose the upper block (rolls of 1, 2, 3) or lower block (rolls of 4, 5, 6).
2. Roll a second 1d6 to determine the exact slot (1 through 6) within that block.

**Handing Empty Slots (Roll-Again)**: If you roll a slot that is empty, contains standard structure/armor, or contains a component that has already been destroyed, nothing happens and you roll again until you strike a valid, functioning system.

---

## 3. The Standard Grid Framework (Same for Everyone)

Every standard BattleMech record sheet uses a universal layout consisting of **78 total critical slots** spread across its 8 body locations. To make rolling with six-sided dice easier, any location with 12 slots is physically split into two tables of 6 slots each (Slots 1–6 and Slots 7–12).

The core, unchangeable skeleton framework of a standard Inner Sphere Mech looks like this:

| Mech Location    | Total Slots | Default Pre-Filled Components (Internal Framework)                                    |
|:-----------------|:-----------:|:--------------------------------------------------------------------------------------|
| **Head**         |   6 Slots   | Life Support (1), Sensors (1), Cockpit (1), _empty(1)_, Sensors (1), Life Support (1) |
| **Center Torso** |  12 Slots   | Engine (3), Gyro (4), Engine (3), _empty(2)_                                          |
| **Left Torso**   |  12 Slots   | _Completely empty by default_                                                         |
| **Right Torso**  |  12 Slots   | _Completely empty by default_                                                         |
| **Left Arm**     |  12 Slots   | Shoulder (1), Upper-Arm Actuator (1), Lower-Arm Actuator (1)\*, Hand Actuator (1)\*   |
| **Right Arm**    |  12 Slots   | Shoulder (1), Upper-Arm Actuator (1), Lower-Arm Actuator (1)\*, Hand Actuator (1)\*   |
| **Left Leg**     |   6 Slots   | Hip (1), Upper-Leg Actuator (1), Lower-Leg Actuator (1), Foot Actuator (1)            |
| **Right Leg**    |   6 Slots   | Hip (1), Upper-Leg Actuator (1), Lower-Leg Actuator (1), Foot Actuator (1)            |

_\*Note: Some Mechs omit lower arm or hand actuators by design to save weight or accommodate giant arm-mounted weapons._

---

## 4. The Internal Contents (Unique to Each Mech)

While every Mech shares that exact same grid foundation, the remaining empty spaces are filled in by game designers (or players building custom units) with specific weapons, ammunition bins, heat sinks, and jump jets.

Because items have different physical dimensions, they consume varying amounts of critical slots:

- **Small Items (1 Slot):** Medium Lasers, Jump Jets, Standard Heat Sinks, Ammunition Bins (1 ton).
- **Medium Items (2–3 Slots):** Large Lasers (2 slots), PPCs (3 slots), SRM-6 Launchers (2 slots).
- **Massive Items (4–10 Slots):** LRM-20 Launchers (6 slots), Gauss Rifles (7 slots), or the massive Autocannon/20 (10 slots).

### Tactical Impact Example

If you score a critical hit on the **Right Torso** of two different Mechs, your dice roll hits entirely different systems:
- **On a Hunchback (HBK-4G):** The Right Torso holds a massive AC/20 weapon system filling 10 slots. You are almost guaranteed to break his main gun.
- **On an Atlas (AS7-D):** The Right Torso holds an LRM-20 (6 slots) and an active Ammo Bin (1 slot). If you hit that ammo bin, the entire Mech will likely explode.

---

## 5. Component Destruction & Ammunition Explosions

- **Engine:** 3 Critical Hits Max:
   - **1st:** +5 heat every single turn, added automatically during the Heat Phase.
   - **2nd:** another +5 heat (total +10) every single turn, added automatically during the Heat Phase.
   - **3rd:** causes an immediate engine shutdown, destroying the Mech.
- **Gyro:** 2 Critical Hits Max:
   - **1st:**
      - **Immediate Roll:** The player must immediately make a Piloting Skill Roll (PSR) at the end of the current phase with **+3 penalty**. If failed, the Mech falls over.
      - **Ongoing Movement Penalty:** Every time the Mech attempts to **Run** or **Jump** in future movement phases, the player must pass a PSR with a **+3 modifier** at the end of that movement, or instantly crash to the ground.
      - **General Penalty:** Any standard PSR event (like taking 20+ damage in a turn, or entering water) receives a **+3 penalty**.
   - **2nd:** The gyro is completely shattered. The Mech crashes to the ground instantly and can never stand up again for the rest of the game. It is effectively immobilized and can only fire weapons from the prone position.
- **Sensor**: 2 Critical Hits Max:
  - **1st:** The primary targeting hardware is disrupted. The Mech suffers a permanent **+2 to-hit penalty to all weapon attacks**.
  - **2nd:** All primary targeting suites are completely blinded. **The Mech can no longer fire any of its weapons systems** for the remainder of the match.

- **Weapons:** A critically hit weapon is rendered completely non-functional for the remainder of the game.
- **Ammunition (Ammo Explosion):** If a critical hit strikes a slot containing live ammunition, the entire remaining payload detonates instantly. The total damage of all remaining shots in that bin is applied directly to that location's Internal Structure and damage transfer rule. An ammo explosion also injures the MechWarrior (see [`pilot.md`](pilot.md)).
- **Cockpit:** A cockpit critical hit kills the MechWarrior outright. The Mech is immediately destroyed (see [`pilot.md`](pilot.md) and [`armor-damage.md`](armor-damage.md) §7 *Mech Destruction*).
- **Actuators:** Limbs carry actuators in their critical slots — Shoulder / Upper-Arm / Lower-Arm / Hand in the arms, and Hip / Upper-Leg / Lower-Leg / Foot in the legs. A destroyed actuator degrades that limb's physical attacks and adds piloting/firing modifiers. A **Hip** hit is the most severe: it halves that leg's Movement Points.

- Life Support: 2 Critical Hits Max:
  - **1st:** The cockpit's internal climate control fails, exposing the MechWarrior to intense internal machinery temperatures. If the Mech's Heat Scale reaches **15 or higher** during a turn, the pilot automatically takes **1 point of Pilot Damage** (wounding the MechWarrior and forcing a consciousness check).
  - **2nd:** Complete environmental systems failure. The cockpit becomes entirely unlivable. **The pilot takes 1 point of Pilot Damage every single turn** during the Heat Phase, regardless of whether the Mech is running hot or cold.

### Quick Reference Summary Table

| Component        | Hit #1 Penalty                        | Hit #2 Penalty                    | Hit #3 Penalty     |
|:-----------------|:--------------------------------------|:----------------------------------|:-------------------|
| **Engine**       | +5 Heat per turn                      | +10 Heat per turn                 | **Mech Destroyed** |
| **Gyro**         | +3 to all Piloting Skill Rolls        | **Immobilized** (Cannot stand)    | *N/A*              |
| **Sensors**      | +2 to-hit on all attacks              | **Cannot fire weapons**           | *N/A*              |
| **Life Support** | Pilot takes damage if Mech Heat >= 15 | Pilot takes damage **every turn** | *N/A*              |
| **Cockpit**      | **Pilot killed → Mech Destroyed**     | *N/A*                             | *N/A*              |
| **Actuator**     | Degrades physical attacks; piloting/firing modifier (Hip: halves leg MP) | — | *N/A* |
