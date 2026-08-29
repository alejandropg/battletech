# Game definition files

A game file describes only the immutable starting setup. Runtime state such as armor damage,
heat, ammunition, and destroyed equipment is constructed from each registered mech variant.

```json
{
  "map": "battletech-classic",
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

Coordinates are one-based. Facing is one of `N`, `NE`, `SE`, `S`, `SW`, or `NW`. Unit IDs must
be nonblank and unique, positions cannot overlap, both players must own at least one unit, and
both skills must be between 0 and 8 inclusive. The map name must exist in the startup catalog and
the variant must exist in the engine's `MechModels` registry. Unknown JSON fields are rejected.

The packaged `default` game is used when `--game` is omitted. To use an external game with an
external map, register the map path and reference its filename without the final `.json`:

```shell
battletech-tui --map ./maps/arena.json --game ./games/arena-duel.json
```

Here the game file uses `"map": "arena"`. `--map` may be repeated. External names are exact and
case-sensitive; startup fails if two external files produce the same name or if an external name
collides with a packaged map.
