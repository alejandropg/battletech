# BattleTech: Classic Board Game Heat Rules Reference

In Classic BattleTech, managing internal thermal energy is a core strategic element. Firing weapons and moving generates heat, while your Mech's heat sinks attempt to dissipate it. If a Mech generates more heat than it can vent, it climbs a universal tracking scale, incurring severe performance penalties, risking computer shutdown, or triggering ammunition explosions.

---

## 1. The Heat Cycle Phase
At the end of every turn, during the dedicated **Heat Phase**, players calculate their Mechs' new heat levels using the following equation:

$$\text{New Heat Level} = \text{Current Heat} + \text{Heat Generated} - \text{Heat Dissipated}$$

### Heat Generation
Add up all thermal costs incurred during the current turn:
* **Movement:** 
    * **Stationary:** 0 Heat
    * **Walking:** 1 Heat
    * **Running:** 2 Heat
    * **Jumping:** 1 Heat per hex jumped (Minimum of **3 Heat**, even if jumping only 1 or 2 hexes).
* **Weapons:** Every weapon has a fixed heat cost listed on the Mech's record sheet (e.g., Medium Laser = 3 heat, ERPPC = 15 heat). You only pay for weapons that were declared and fired.
* **External Hazards:** Getting hit by enemy plasma weapons, flamers, or standing in a burning terrain hex.

### Heat Dissipation
Subtract your Mech's active cooling capacity from the total generated heat:
* **Standard Heat Sinks (SHS):** Each operational sink dissipates **1 point** of heat.
* **Double Heat Sinks (DHS):** Each operational sink dissipates **2 points** of heat.
* **Engine Freebies:** Every Mech's fusion engine includes 10 "free" heat sinks built-in (providing 10 dissipation for SHS, or 20 dissipation for DHS).
* **Water Bonus:** If a Mech is standing in water, heat sinks located in its legs receive a cooling bonus (typically +1 heat dissipated per sink in shallow water).

---

## 2. The Universal Heat Scale (0 to 30)
This scale applies identically to **all BattleTech Mechs**, regardless of tonnage or faction. When tracking net heat, penalties are enforced immediately upon hitting or exceeding specific thresholds.

To avoid Shutdown or Ammo Explosion penalties, the player must roll **equal to or greater than** the target number using **2D6** during the Heat Phase.

| Heat Level | Movement Modifier | To-Hit Modifier | Shutdown Roll (Avoid On) | Ammo Explosion (Avoid On) |
|:----------:|:-----------------:|:---------------:|:------------------------:|:-------------------------:|
|   **5**    |       -1 MP       |        —        |            —             |             —             |
|   **8**    |         —         |       +1        |            —             |             —             |
|   **10**   |       -2 MP       |        —        |            —             |             —             |
|   **13**   |         —         |       +2        |            —             |             —             |
|   **14**   |         —         |        —        |            4+            |             —             |
|   **15**   |       -3 MP       |        —        |            —             |            4+             |
|   **17**   |         —         |        —        |            6+            |             —             |
|   **18**   |         —         |       +3        |            —             |             —             |
|   **19**   |         —         |        —        |            —             |            6+             |
|   **20**   |       -4 MP       |        —        |            —             |             —             |
|   **22**   |         —         |        —        |            8+            |             —             |
|   **23**   |         —         |        —        |            —             |            8+             |
|   **24**   |         —         |       +4        |            —             |             —             |
|   **25**   |       -5 MP       |        —        |            —             |             —             |
|   **26**   |         —         |        —        |           10+            |             —             |
|   **28**   |         —         |        —        |            —             |            10+            |
|   **30**   |         —         |        —        |    **Auto-Shutdown**     |             —             |

### Rules for Penalties:
* **Modifiers are NOT Cumulative:** You only apply the single worst penalty from a category. For example, at Heat Level 15, your total movement penalty is `-3 MP` (it does not stack with the -1 and -2 from lower levels).
* **Simultaneous Effects:** You suffer the worst penalty from *every active category simultaneously*. At Heat Level 15, you have `-3 MP`, a `+2 To-Hit` penalty, and you must roll a 4+ to avoid an internal ammunition explosion.
* **To-Hit Penalty Application:** Heat-induced To-Hit penalties apply to all weapon attacks made *by* the overheating Mech. They do not make the Mech easier for enemies to hit.

---

## 3. Mech Shutdown Consequences
If a Mech fails its shutdown avoidance roll (or hits Heat Level 30), it immediately powers down:
* The Mech cannot move, jump, rotate its torso, or fire weapons. It also **takes no activation slot** in the Movement Phase sequence — see [`movement.md`](movement.md) §2.
* It becomes an immobile target, granting all attackers a massive **-4 To-Hit bonus**.
* During the Heat Phase of a turn spent entirely shutdown, the Mech cannot perform actions to generate heat, allowing its heat sinks to safely dissipate a large portion of its current heat scale.
* A startup roll can be attempted in subsequent turns according to standard operational rules.
