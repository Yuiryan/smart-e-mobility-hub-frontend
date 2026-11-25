# JSON demo data and restart persistence

## Current state
- `DataStoreBootstrap` already loads `app.data-file` (default `./data/data.json`) at startup.
- `StateCoordinator.commit(expectedVersion, change)` already increments the snapshot version, saves through `JsonStore`, and rolls RAM back on failure. Startup does not reseed existing data.
- The checked-in version-0 demo contains one hub and three vehicles, without accounts, parking spaces or charging ports. One low-battery vehicle is incorrectly labelled AVAILABLE and another is RESERVED without a reservation.
- Schema documentation describes some future field names instead of the actual Java records. UC-00 login/registration and IoT adapters are not implemented in this checkout.

## Goal
- Provide a usable, linked snapshot with hubs, shared/private vehicles, parking spaces, charging ports, students and operators.
- Provide data compatible with the existing load/commit/restore flow for the team to verify through FE.
- Document current persistence fields and startup/reset instructions.

## Non-goals
- No API/DTO/entity field changes, new dependencies, authentication implementation, simulator, scheduler or database.
- No automatic seed/reset or telemetry timestamp refresh on startup.

## Design decisions
- User request authorizes designing the sample file and filling persistence gaps. Reuse the existing persistence implementation; no new persistence layer is needed.
- `data/data.json` is the mutable working snapshot; `data/data.example.json` is a manually restorable template. Both initially contain the same demo data. Never automatically overwrite an existing snapshot with the template.
- Use exact current JSON record fields (`Account.id/active`, `ParkingSpace.id`, `ChargingPort.id/powerKw`, `chargingCapacityKw`, `batteryCapacityKwh`). Document differences from the target model; do not silently change the public contract.
- Include 3 hubs, 12 vehicles (10 shared, 2 private), 24 parking spaces, 6 ports, 3 student accounts/profiles and 2 operator accounts. Every occupied space links back to its vehicle; free spaces have no occupant or holds.
- Use synthetic identities and salted SHA-256 demo password hashes in `sha256$salt$hex` format, digesting UTF-8 `salt:password`. This is a fixture convention for future UC-00 integration, not a claim of implemented login compatibility. Sessions and transaction collections start empty.
- Keep fixed historical telemetry, including missing readings and battery boundaries 19.9/20.0/20.1. Reading the file never makes telemetry fresh. Missing readings remain null.
- Per the user's latest instruction, leave automated tests and FE integration verification to the team; no new test source files are retained.

## Files affected
- `docs/plans/feature-json-demo-data.md`
- `data/data.json`
- `data/data.example.json`
- `README.md`
- `docs/03-api-contract.md`
- `docs/04-data-schema.md`

## Implementation steps
1. Inspect existing startup/persistence code and actual JSON field names.
2. Generate the linked JSON working snapshot and template using the current record schema.
3. Align persistence documentation and explain demo accounts, telemetry, startup and explicit backup/reset.
4. Check JSON syntax and references and inspect the final diff. Per the user's latest instruction, do not add tests or run further automated test suites; the team will verify through FE later.

## Acceptance criteria
- Sample matches the existing Spring startup loader and includes all requested entities with correct foreign-key links.
- Reuse existing StateCoordinator persistence and startup restore without production code changes.
- Historical/missing telemetry remains unchanged; no automatic timestamp refresh or reseeding.
- No new test files are retained; FE integration and restart verification are left to the team as requested.

## Verification
- Parse JSON and check collection counts and references.
- `git diff --check`
- Automated tests were started before the user requested deferral. The newly created test file has been removed; do not claim a passing test/build run.
