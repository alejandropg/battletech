# Wire protocol

Discriminator convention and `PROTOCOL_VERSION` detail for `network`'s wire-crossing types. Read
this when adding or changing a wire-crossing `@Serializable sealed` variant, when
`WireDiscriminatorConventionTest` or `WireDiscriminatorGoldenFileTest` fails, or when making a
non-additive change to `GameCommand`/`GameEvent`/`CommandRejection`/`RuleRejection`/etc.

`tactical`'s wire-crossing types carry `kotlinx.serialization` annotations for `network`'s benefit
despite `tactical` otherwise being delivery-agnostic — this is a deliberate exception, a
wire-compatibility constraint on field layout, not a UI concern.

`network/wire/WireJson.kt` sets `classDiscriminator = "type"`. With no `@SerialName`,
kotlinx.serialization falls back to a variant's **fully-qualified class name** as that
discriminator string, which welds the wire format to package layout: moving a variant between
packages — not just adding/removing one — would silently change the wire format, and `network`'s
round-trip tests can't catch that on their own, since they encode and decode with the same code
on both sides of the assertion.

Every `@Serializable sealed` hierarchy that's reachable under `battletech.` therefore carries
`@SerialName` on every concrete variant, by one convention: **the serial name is the variant's
lexical nesting path relative to its package, decapitalized segment by segment and joined with
`.`, dropping only as many leading segments as needed for the name to stay unique among its
hierarchy's other variants.** In practice that means a flat one-level hierarchy (`RuleRejection`,
`CommandRejection`, `CommandResult`) drops the enclosing type entirely (`RuleRejection.NotAdjacent`
→ `"notAdjacent"`), while a hierarchy whose sibling branches would otherwise collide keeps the
disambiguating segment (`GameEvent`'s `UnitStoodUp.Detailed` → `"unitStoodUp.detailed"`, alongside
`AmmoExploded.Detailed` → `"ammoExploded.detailed"` in the same hierarchy). A top-level variant
that implements a bare marker root from a separate file (`CombatUnit`/`ForeignUnit` implementing
`VisibleUnit`) uses its own name unprefixed, the same as any other single-segment case.

This is build-enforced, not hand-maintained discipline, by two tests in
`network/src/test/kotlin/battletech/network/wire/`:

- `WireDiscriminatorConventionTest` discovers every top-level `@Serializable sealed`
  interface/class under `battletech.` via Konsist, walks each down to its concrete leaves via
  `KClass.sealedSubclasses`, and asserts every leaf's `@SerialName` is present, is a decapitalized
  suffix of that leaf's own lexical nesting path (never an arbitrary invented string), and is
  unique among its root's other variants.
- `WireDiscriminatorGoldenFileTest` walks the actual `SerialDescriptor` graph kotlinx builds from
  `ClientMessage`/`ServerMessage` (generics, nesting, and all) and pins the resulting discriminator
  set — together with `PROTOCOL_VERSION` — against a checked-in golden file
  (`network/src/test/resources/wire-discriminators.txt`), so a renamed discriminator or a type that
  stops being reachable from the two message envelopes shows up as a diff there even if it isn't
  caught by the Konsist-driven test above.

A wire change that isn't purely additive should bump `PROTOCOL_VERSION` (`network/wire/Messages.kt`)
in the same diff that updates the golden file — a mismatched client already rejects cleanly via
`JoinRejectionReason.INCOMPATIBLE_PROTOCOL`. `PROTOCOL_VERSION` is currently 10. Version 6 moved
`GameMap` out of every `GameSnapshot` and into the one-time `ServerMessage.JoinAccepted`. Version 7
made match mech definitions host-authoritative. Version 8 unified those definitions, the map, and
the player's initial projected snapshot and log into one `MatchBootstrap` carried by
`ServerMessage.JoinAccepted`. Its `CombatUnit.model` fields and all later snapshots remain compact
variant strings. The connection-scoped decoder stages the bootstrap: it validates and installs the
embedded catalog before decoding the snapshot, then retains that resolver for later pushes. Adding
a packaged variant is therefore not itself a protocol change. Version 9 replaced that per-client
model list with one match-scoped, server-authoritative `AssetRegistry` (MAP and MECH content):
`ClientMessage.Join` gained a `content: AssetBundle` field (defaulted, so an old-shape `Join` still
decodes far enough to be rejected with `INCOMPATIBLE_PROTOCOL`) carrying everything a joining seat
has registered, and `MatchBootstrap.mechModels` was replaced by `MatchBootstrap.registry`. The host
merges every seat's bundle in join order (first-registrant wins); the decoder now stages the
bootstrap's `registry` field (not `mechModels`) to build its resolver. A collision is reported as an
`AssetConflict` event through the normal (shared) game log rather than a client-local check — the
client no longer inspects host content against its own catalog at all.
`network/src/test/kotlin/battletech/network/wire/WireFormatRoundTripTest.kt` additionally pins the
literal JSON for one flat (`RuleRejection.NoAmmo`) and one nested (`GameEvent`'s
`UnitStoodUp.Undisclosed`) variant, to catch a discriminator change that round-tripping alone would
miss (encoding and decoding with the same code on both sides of an assertion agrees with itself
even after a rename).

Version 10 added the pre-match lobby: `ClientMessage` is unchanged, but a `ClientMessage.Join`
against an uncommitted match (interactive `host`, before the user commits) now gets a different
reply sequence than an already-committed one, via three new `ServerMessage` variants —
`"lobbyJoined"`, `"lobbySelections"`, `"lobbyCommitted"` (all top-level `ServerMessage` variants,
so each keeps its bare decapitalized name per the convention above):

```
client                                   server
  |--- Join(sessionId, version, bundle) -->|
  |                                        |  match already committed?
  |<-- JoinAccepted(bootstrap) ------------|  yes -> today's path, unchanged
  |                                        |
  |<-- LobbyJoined(catalog) ---------------|  no  -> parked
  |<-- LobbySelections(plan) --------------|  (repeated on every host change, no debounce)
  |<-- LobbyCommitted ---------------------|  host committed
  |--- Join(same message again) ---------->|  client re-sends its original Join
  |<-- JoinAccepted(bootstrap) ------------|
```

A join that arrives once the host has already committed always takes the top branch, even when it
reaches a `LobbyHost` rather than a `GameServer` — the lobby forwards it to the committed match
untouched. An interactive host's acceptor is built over the lobby and never rebuilt, so this is
the path every mid-match reconnect takes.

`LobbyJoined.catalog` is a `battletech.tactical.model.content.ContentSummary` (id-only: which maps
and mechs are registered, nothing else) — the merged registry as of park time, including the
joiner's own `--add-map`/`--add-mech` contribution. `LobbySelections.plan` is a `battletech.
tactical.model.content.MatchPlan` (map name + per-player roster counts), sent once per host
change with no debounce — the parked client always mirrors whatever the host's setup screen shows
right now, never a stale intermediate. `LobbyCommitted` carries no payload; it is the signal to
re-send the exact same `ClientMessage.Join` object already sent once.

**The re-sent `Join` is load-bearing — do not replace it with a latch or a poll.** It is what lets
a parked connection keep exactly ONE reader thread for its whole life (`network/client/
LobbyClient.kt`'s reader thread exits the instant it forwards the re-`Join` and receives
`JoinAccepted`, handing the connection to a fresh `ClientGameSession` reader) while still
detecting a host that vanishes while parked (a blocked `receive()` returns null on EOF, same as
every other reader in this module). A second reader on the same connection, at any point, is the
one fatal bug `LobbyClient` is built to avoid — see its KDoc and the round-trip proof in
`LobbyClientTest`'s "commit fires onCommitted, and the resulting session plays normally with no
duplicate reader" test.

See `docs/architecture.md`'s "The lobby: one commit path for hot-seat and host" for how
`LobbyHost`/`LobbyClient`/`ConnectionSink` fit together, and why `LobbyHost.commit` takes an
`onServerReady` callback rather than trusting "call `connectLocal()` immediately after, same
thread" alone.
