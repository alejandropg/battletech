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

## 3. Critical Hits

Moved to its own doc: **[`critical-hits.md`](critical-hits.md)** — the Critical Hit Table (2d6),
critical-slot determination and the 78-slot grid, and per-component destruction effects
(engine, gyro, sensors, life support, cockpit, actuators, ammo explosions).

The trigger lives here: a location takes **1 or more points of internal structure damage** (§1
step 5), or the hit location roll is a natural 2 (*The Natural 2 Rule* above).

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

These follow directly from the *Damage Transfer Rule* (§5) and, for per-location weapon mounting and
component effects, the slot grid and Critical Hit System in [`critical-hits.md`](critical-hits.md).
