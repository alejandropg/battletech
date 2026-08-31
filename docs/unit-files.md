# Unit collection files

A unit file describes only the immutable starting roster — no board. Runtime state such as armor
damage, heat, ammunition, and destroyed equipment is constructed from each registered mech
variant. The board is a separate selection made at launch (`--map`), so one unit file is playable
on any registered map — see below for what that means for a position that doesn't fit.

```json
{
  "units": [
    {
      "id": "A1",
      "player": 1,
      "variant": "AS7-D",
      "gunnerySkill": 4,
      "pilotingSkill": 5,
      "position": { "col": 2, "row": 2 },
      "facing": "SE"
    },
    {
      "id": "W1",
      "player": 2,
      "variant": "WVR-6R",
      "gunnerySkill": 4,
      "pilotingSkill": 5,
      "position": { "col": 14, "row": 16 },
      "facing": "NW"
    }
  ]
}
```

Coordinates are one-based. Facing is one of `N`, `NE`, `SE`, `S`, `SW`, or `NW`. Unit IDs must be
nonblank and unique within the file; `player` must be `1` or `2`; both skills must be between 0
and 8 inclusive. These checks run when the file is read, independent of any map or mech catalog —
they are also what backs `--list-units`, so a broken file is caught by listing it, not only by
launching it. Unknown JSON fields are rejected.

Every unit's variant, position, and player is checked again once a map is chosen for it — this
only happens at launch, once `--map`/`--unit` (or `ContentCatalog.resolveGame`) pairs the two:

- The variant must exist in the startup mech catalog.
- The position must fall inside the chosen map; a roster authored against a small map can be
  rejected by a smaller one it's paired with later. The error names both the unit and the map.
- No two units may share a position, and both players must be represented.

The packaged `default` collection is used when `--unit` is omitted, on whichever map `--map`
selects (`battletech-classic` by default). Register an external collection with repeatable
`--add-unit <path>`, then select it by its filename minus `.json`:

```shell
battletech-tui --add-unit ./units/duel.json hot-seat --map river-valley --unit duel
```

`--add-unit` may be repeated. External names are exact and case-sensitive; startup fails if two
external files produce the same name or if an external name collides with a packaged collection.
Unlike maps and mechs, one unit file is selected wholesale — `--unit` does not compose a roster
from several registered files the way `--add-mech` composes variants from several collections.

External mech variants are registered before a unit collection is resolved, so a roster can
reference them:

```shell
battletech-tui --add-mech ./mechs/custom.json hot-seat --unit duel
```

`--add-mech` may be repeated. See `docs/mech-files.md` for the collection format and collision
rules, and the root's `--help` for the full `--add-map`/`--add-mech`/`--add-unit` option list —
they are root options and must precede the command name.
