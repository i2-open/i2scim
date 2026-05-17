# Pre-image carried on RequestCtx for event derivation

To derive RISC events (Account Enabled/Disabled, Identifier Changed) from SCIM
state changes, the event mapper needs the resource's state *before* the
operation. The backend providers already load that pre-image for free during
`patch`/`put`/`delete`, so each provider stashes it on the per-request
`RequestCtx` (`setPreImageResource`), and the mapper reads it via
`op.getRequestCtx()`. This adds zero backend round-trips and therefore zero
latency to the SCIM operation itself.

## Status

accepted

## Considered Options

- **Pre-image field on `Operation`** — domain-cleaner, but the provider SPI
  (`IScimProvider.patch/put/delete`) does not receive the `Operation`. Wiring it
  through would mean either widening the SPI or having the op class issue its
  own pre-read — an extra backend round-trip on the SCIM critical path.
- **Plugin pre-op hook** (`IScimPlugin`) snapshotting the resource — also an
  extra read, and more moving parts.

## Consequences

`RequestCtx`, a protocol-layer object, now carries a domain `ScimResource`
snapshot — a deliberate layering compromise accepted because `RequestCtx`
already accumulates per-request state and is the only object the provider can
reach that also flows to the event publication path. `delete()` must now retain
the removed resource (previously discarded) so the pre-image is available for
Account Purged. Event publication remains fully asynchronous and non-blocking:
the SCIM response returns before any event mapping runs.
