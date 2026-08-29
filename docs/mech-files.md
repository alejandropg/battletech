# Mech model collection files

Packaged models live under `mech/`; `mech/index.json` names every packaged collection loaded at
startup. An external collection is added with repeatable `--mech <path>` on hot-seat, `host`, and
`server`. A joining client needs no local model files because the host includes the match's
definitions in the same bootstrap as the map and initial state. The client installs them before
decoding compact model references in that state. After joining, each host definition whose variant
also exists in the client's packaged catalog is compared structurally with the local definition.
A difference adds a warning to the local game log, but the host definition remains authoritative;
a variant unavailable locally is accepted silently.

Each file contains a `models` array. A variant is an exact, case-sensitive identifier and may occur
only once across all packaged and external collections. Duplicate definitions fail startup even when
their contents are identical; external files never override packaged content.

```json
{
  "models": [
    {
      "variant": "LCT-1V",
      "name": "Locust LCT-1V",
      "tonnage": 20,
      "walkingMP": 8,
      "runningMP": 12,
      "jumpMP": 0,
      "heatSinks": { "type": "STS", "units": 10 },
      "armor": {
        "head": 8,
        "centerTorso": 10,
        "centerTorsoRear": 2,
        "leftTorso": 8,
        "leftTorsoRear": 2,
        "rightTorso": 8,
        "rightTorsoRear": 2,
        "leftArm": 4,
        "rightArm": 4,
        "leftLeg": 4,
        "rightLeg": 4
      },
      "loadout": [
        { "type": "weapon", "location": "CENTER_TORSO", "weapon": "mediumLaser" },
        { "type": "ammo", "location": "CENTER_TORSO", "ammo": "MG", "tons": 1 }
      ]
    }
  ]
}
```

`jumpMP` defaults to 0, `heatSinks` defaults to 10 standard sinks, `loadout` defaults to empty,
and every armor field is required. Internal structure is derived from tonnage. After the ordered
loadout is applied, heat sinks beyond the first 10 and one jump jet per jump MP are placed using
the engine's standard scan order; the resulting critical layout must fit and validate.

Loadout entries are applied in array order:

- `weapon`: `location` plus a `weapon` identifier.
- `ammo`: `location`, an `ammo` enum, and optional positive `tons` (default 1).
- `omitActuators`: an arm `location` plus `lowerArm`, `hand`, or both; place this before equipment
  that needs the freed slots.

Weapon identifiers are `mediumLaser`, `largeLaser`, `smallLaser`, `machineGun`, `ppc`, `ac5`,
`ac20`, `srm2`, `srm6`, `lrm5`, `lrm10`, and `lrm20`. Locations and ammo types use the uppercase
enum spellings already used by game and engine JSON. Unknown fields are rejected.
