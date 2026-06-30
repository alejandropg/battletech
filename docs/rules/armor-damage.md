# BattleTech Tabletop: Armor, Internal Structure & Damage Mechanics

In the BattleTech board game, damage tracking is designed to simulate a highly detailed, realistic mechanical exoskeleton. A BattleMech is split into separate components, each protected by an outer shell of **Armor** and built upon an inner framework called the **Internal Structure**. Damage always flows from the outside in.

---

## 1. The Damage Resolution Process

When a BattleMech is hit by a weapon attack, damage is resolved using a specific, linear sequence for the targeted location:

[ Incoming Damage ] ──> [ Armor Points ] ──> [ Internal Structure ] ──> [ Critical Hits Check ]

Steps:
1. **Check Hit Location:** Roll 2d6 on the appropriate Hit Location Table (determined by the attacker's facing relative to the target: Front, Left Side, Right Side, or Rear) to find out which section takes the hit.
2. **Apply Damage to Armor:** Subtract the incoming damage from the armor points/bubbles of that specific location.
3. **Check for Damage Blow-Through:** * If the armor points are greater than or equal to the weapon damage, the armor absorbs the hit completely.
    * If the weapon damage exceeds the remaining armor, the armor is reduced to 0, and all leftover damage "blows through" into the **Internal Structure**.
4. **Apply Damage to Internal Structure:** Subtract the remaining damage from the Internal Structure points of that location.
5. **Roll for Critical Hits:** The exact moment Internal Structure takes **1 or more points of damage** from a weapon attack, its internal components are exposed. You must immediately roll to see if internal systems are damaged.

---

## Hit Location Tables

When a weapon attack successfully hits a BattleMech, the attacker rolls **2d6** to determine exactly which component is struck. You must select the specific column below that matches the attacker's positioning relative to the target: **Front/Rear Arc**, **Left Side Arc**, or **Right Side Arc**.

| 2d6 Roll | Front / Rear Arc        | Left Side Arc         | Right Side Arc         |
|:--------:|:------------------------|:----------------------|:-----------------------|
|  **2**   | Center Torso [Critical] | Left Torso [Critical] | Right Torso [Critical] |
|  **3**   | Right Arm               | Left Leg              | Right Leg              |
|  **4**   | Right Arm               | Left Arm              | Right Arm              |
|  **5**   | Right Leg               | Left Arm              | Right Arm              |
|  **6**   | Right Torso             | Left Leg              | Right Leg              |
|  **7**   | Center Torso            | Left Torso            | Right Torso            |
|  **8**   | Left Torso              | Center Torso          | Center Torso           |
|  **9**   | Left Leg                | Right Torso           | Left Torso             |
|  **10**  | Left Arm                | Right Arm             | Left Arm               |
|  **11**  | Left Arm                | Right Leg             | Left Leg               |
|  **12**  | Head                    | Head                  | Head                   |

### 1. The Natural 2 Rule (Through-Armor Critical)

If the 2d6 roll results in a natural **2** (snake eyes):
- Apply the weapon's damage to the armor of that location as normal.
- **Bonus Effect:** You get an immediate, automatic **Critical Hit check** against that location's internal components, even if the armor is still fully intact.

### 2. Front vs. Rear Torso Allocation

If the attacker is standing in the target's **Rear Arc**, you use the first column (**Front / Rear Arc**) to find the location, but apply the damage to the sheet differently:
- **Torso Hits (CT, LT, RT):** Damage is deducted from the **Rear Armor** track.
- **Limb/Head Hits (Arms, Legs, Head):** These locations do not have rear tracks. Damage is deducted from their standard armor tracks normally.

### 3. Firing Arc Limitations

Attacking from a side arc biases hits toward the near half of the Mech. Rolls 2–7 from a side arc land on near-side locations (the near torso, arm, and leg); roll 8 is always Center Torso; and rolls 9, 10, and 11 reach the far-side torso, arm, and leg respectively. The far side is still reachable — just less likely. Use this tactically to focus fire on an opponent's already weakened side while knowing that very high rolls can still touch their opposite flank.

---

## 3. Critical Hits: The Danger of Internal Damage

Every time a Mech's Internal Structure takes damage from a weapon hit, the player must roll **2d6 (two six-sided dice)** on the Critical Hit Table to see if internal equipment is torn apart.

### The Critical Hit Table

| 2d6 Roll     | Result                | Tactical Effect                                                                               |
|:-------------|:----------------------|:----------------------------------------------------------------------------------------------|
| **2 to 7**   | No Critical Hit       | The structure holds; internal components are unharmed.                                        |
| **8 or 9**   | 1 Critical Hit        | One random component in that location is destroyed.                                           |
| **10 or 11** | 2 Critical Hits       | Two random components in that location are destroyed.                                         |
| **12**       | Head / Limb Blown Off | **Head/Torso:** 3 Critical Hits.<br>**Arm/Leg:** The limb is completely severed and detached. |

> **Crucial Rule:** You must roll on this table *every single time* a location takes internal structure damage from a new weapon attack, even if it is only a single point of damage.

### Determining Which Component is Hit

Every Mech has a Critical Slot Table on its record sheet split into sections matching its limbs and torso. Each section has 12 slots (divided into two blocks of 6). **The blank framework is identical for every Mech**, but **the items written inside those slots are completely unique** to each specific Mech design and variant.

If you scored a critical hit, you determine the exact item destroyed by rolling dice:

1. Roll a 1d6 to choose the upper block (rolls of 1, 2, 3) or lower block (rolls of 4, 5, 6).
2. Roll a second 1d6 to determine the exact slot (1 through 6) within that block.

**Handing Empty Slots (Roll-Again)**: If you roll a slot that is empty, contains standard structure/armor, or contains a component that has already been destroyed, nothing happens and you roll again until you strike a valid, functioning system.

### The Standard Grid Framework (Same for Everyone)

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

### The Internal Contents (Unique to Each Mech)

While every Mech shares that exact same grid foundation, the remaining empty spaces are filled in by game designers (or players building custom units) with specific weapons, ammunition bins, heat sinks, and jump jets.

Because items have different physical dimensions, they consume varying amounts of critical slots:

- **Small Items (1 Slot):** Medium Lasers, Jump Jets, Standard Heat Sinks, Ammunition Bins (1 ton).
- **Medium Items (2–3 Slots):** Large Lasers (2 slots), PPCs (3 slots), SRM-6 Launchers (2 slots).
- **Massive Items (4–10 Slots):** LRM-20 Launchers (6 slots), Gauss Rifles (7 slots), or the massive Autocannon/20 (10 slots).

### Tactical Impact Example

If you score a critical hit on the **Right Torso** of two different Mechs, your dice roll hits entirely different systems:
- **On a Hunchback (HBK-4G):** The Right Torso holds a massive AC/20 weapon system filling 10 slots. You are almost guaranteed to break his main gun.
- **On an Atlas (AS7-D):** The Right Torso holds an LRM-20 (6 slots) and an active Ammo Bin (1 slot). If you hit that ammo bin, the entire Mech will likely explode.

### Component Destruction & Ammunition Explosions

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
- **Cockpit:** A cockpit critical hit kills the MechWarrior outright. The Mech is immediately destroyed (see [`pilot.md`](pilot.md) and the *Mech Destruction* section below).
- **Actuators:** Limbs carry actuators in their critical slots — Shoulder / Upper-Arm / Lower-Arm / Hand in the arms, and Hip / Upper-Leg / Lower-Leg / Foot in the legs. A destroyed actuator degrades that limb's physical attacks and adds piloting/firing modifiers. A **Hip** hit is the most severe: it halves that leg's Movement Points.


- Life Support: 2 Critical Hits Max:
  - **1st:** The cockpit's internal climate control fails, exposing the MechWarrior to intense internal machinery temperatures. If the Mech's Heat Scale reaches **15 or higher** during a turn, the pilot automatically takes **1 point of Pilot Damage** (wounding the MechWarrior and forcing a consciousness check).
  - **2nd:** Complete environmental systems failure. The cockpit becomes entirely unlivable. **The pilot takes 1 point of Pilot Damage every single turn** during the Heat Phase, regardless of whether the Mech is running hot or cold.

** Quick Reference Summary Table**

| Component        | Hit #1 Penalty                        | Hit #2 Penalty                    | Hit #3 Penalty     |
|:-----------------|:--------------------------------------|:----------------------------------|:-------------------|
| **Engine**       | +5 Heat per turn                      | +10 Heat per turn                 | **Mech Destroyed** |
| **Gyro**         | +3 to all Piloting Skill Rolls        | **Immobilized** (Cannot stand)    | *N/A*              |
| **Sensors**      | +2 to-hit on all attacks              | **Cannot fire weapons**           | *N/A*              |
| **Life Support** | Pilot takes damage if Mech Heat >= 15 | Pilot takes damage **every turn** | *N/A*              |
| **Cockpit**      | **Pilot killed → Mech Destroyed**     | *N/A*                             | *N/A*              |
| **Actuator**     | Degrades physical attacks; piloting/firing modifier (Hip: halves leg MP) | — | *N/A* |

---

## 5. The Damage Transfer Rule (Blow-Through)

When a location’s Internal Structure is reduced to zero, that location is physically destroyed and blown off the Mech. If a massive attack deals more damage than the destroyed location had structure remaining, the leftover damage transfers inward toward the core of the Mech.

Damage transfers along the following strict paths:
- **Left Arm** or **Right Arm** ──> transfers to ──> **Left Torso** or **Right Torso**
- **Left Leg** or **Right Leg** ──> transfers to ──> **Left Torso** or **Right Torso**
- **Left Torso** or **Right Torso** ──> transfers to ──> **Center Torso**

Head and center torso do not transfer (overflow there destroys the unit).

### The Transfer Exception

When damage transfers to an adjacent, inward location, **it must hit the armor of the new location first**, even though the damage originated from an internal explosion or blow-through. If the Center Torso's Internal Structure is ever reduced to 0, the Mech is permanently destroyed.

---

## 6. Step-by-Step Example: Resolving an Autocannon/20 Hit

To see how these rules interact on a standard record sheet, let's look at an **AC/20 (20 points of damage)** hitting a target Mech's **Right Torso (RT)**.

### Target's Initial Status:

- **RT Front Armor:** 12 points remaining
- **RT Internal Structure:** 15 points remaining
- **RT Critical Slots:** Slot 1 contains a *Medium Laser*; Slot 2 contains an *LRM Ammo Bin*.

### Step-by-Step Resolution:

1. **Hit Location:** The attacker rolls an 8 on the front hit location table: **Right Torso**.
2. **Apply to Armor:** $$\text{Remaining Armor} = 12 - 20 = -8$$
   The player crosses off all 12 Armor bubbles. The Right Torso front armor is now 0.
3. **Apply to Structure:** The remaining 8 points of damage blow through to the structure.
   $$\text{Remaining Structure} = 15 - 8 = 7$$
   The player crosses off 8 Internal Structure bubbles. 7 points remain.
4. **Critical Check:** Because the internal structure took damage, the player rolls 2d6 on the Critical Hit Table and gets a **9** ($1\text{ Critical Hit}$).
5. **Determine Slot:** The player rolls a 1d6 to see which item is hit. They roll a **1**.
6. **Result:** The *Medium Laser* in Slot 1 is critically hit and crossed out. It can no longer be fired. The remaining 7 points of structure hold, and the Mech survives to fight another turn!

---

## 7. Mech Destruction (Unit Elimination)

A BattleMech is destroyed and removed from play the instant **any** of the following occurs:

- **Head** Internal Structure reduced to 0.
- **Center Torso** Internal Structure reduced to 0.
- **Both legs** destroyed.
- The **Engine** takes **3** critical hits.
- The **Gyro** is destroyed (2 hits) and the Mech can no longer stand.
- An **ammo explosion** breaches the Center Torso's Internal Structure.
- The **pilot dies** — 6 pilot hits, or a Cockpit critical (see [`pilot.md`](pilot.md)).

A destroyed Mech leaves a wreck in its hex: the hex remains terrain-neutral and passable, and the
wreck cannot be targeted by weapon or physical attacks.

> A **head hit that penetrates to Internal Structure** also injures the pilot (see [`pilot.md`](pilot.md)).

---

## 8. Location Destruction Consequences

Destroying a location has cascading effects beyond the loss of its armor and structure:

- **Arm or Leg destroyed** — all weapons, ammo, and actuators mounted in that limb are lost.
- **Side Torso destroyed** — the **arm attached to that side torso is also destroyed**, taking all of
  its mounted weapons and ammo with it.
- **One Leg destroyed** — the Mech immediately **falls** (taking fall damage and a pilot hit), and for
  the rest of the game it can only "hobble": remaining Movement is **halved**, **jumping is disabled**,
  and it suffers a piloting penalty.
- **Both Legs destroyed** — the Mech is destroyed (see *Mech Destruction* above).

These follow directly from the *Damage Transfer Rule* (§5), per-location weapon mounting (the slot
grid in §3), and the *Critical Hit System* (§3).
