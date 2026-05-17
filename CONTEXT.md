# i2scim — Project Context

> Stub. Fill this in as terms get resolved (the `/grill-with-docs` skill will create entries lazily).

## What this project is

i2scim is a Quarkus implementation of the IETF SCIM v2 protocol (RFC 7643/7644) where resource types and schema are loaded at startup from JSON files rather than hard-coded. See `CLAUDE.md` for the full architecture overview; this file is a glossary.

## Glossary

### SCIM event
A Security Event Token conforming to RFC 9967 (`urn:ietf:params:scim:event:...`) that describes a SCIM provisioning operation — create, put, patch, or delete. See [RFC 9967](https://www.rfc-editor.org/rfc/rfc9967.txt).

### RISC event
A Security Event Token conforming to the OpenID RISC Profile 1.0 (`https://schemas.openid.net/secevent/risc/event-type/...`) that describes an account-level lifecycle/risk change. In i2scim, RISC events are *derived from* SCIM resource state changes and are emitted only for `User` resources. Distinct from a SCIM event: a SCIM event reports the operation, a RISC event reports an account-state consequence of it.

### pre-image
The state of a SCIM resource immediately *before* an operation modified or deleted it. Used to detect what changed — e.g. an identifier's old value, or an `active` transition on a PUT. Contrast with the post-image, the state after the operation.

### Identifier Changed (RISC)
A RISC event emitted when a configured login identifier of an existing User is changed on a PUT or PATCH. The identifier attribute set is deployment-configurable (typically `userName` and `emails`); for multi-valued identifiers only the primary value is tracked (a lone value is treated as primary). Not emitted on CREATE or DELETE. The old value is conveyed in the subject when the RISC subject format is `email`/`username`/`phone`, otherwise the new value is conveyed in the payload (`new-value`) against a stable `scim`-format subject.

### RISC subject format
The `sub_id` format used for RISC events, chosen per deployment: `scim` (default — the same stable id/uri subject used for SCIM events) or `email`/`username`/`phone` (the RISC-style single-identifier subject, carrying the prior value for Identifier Changed).

### `active` transition
A change to — or the initial value of — a User's `active` attribute that yields a RISC Account Enabled or Account Disabled event. An absent `active` is treated as `true` (Enabled). CREATE always emits, reflecting the resulting `active` value. PATCH emits whenever an `add`/`replace` touches `active` (taking the new value directly) or `remove`s it (Enabled). PUT emits only on a change, detected by diffing pre-image against post-image.
