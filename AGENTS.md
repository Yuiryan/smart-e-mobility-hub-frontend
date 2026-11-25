# Frontend working rules

- Read README.md and docs/ARCHITECTURE.md before changing architecture.
- Implement web UI only. Spring Boot owns auth, authorization, allocation, persistence and IoT simulator.
- Treat Figma as the visual reference; do not claim exact fidelity for provisional tokens.
- Keep identifiers in English, UI copy in the vi/en dictionary pattern, documentation in Vietnamese.
- Preserve UC00–UC15 as separate features. No invented backend endpoints presented as confirmed.
- Never store passwords or session tokens in localStorage. VITE_* is public.
- Distinguish mock/demo outcomes from real backend-confirmed outcomes.
- Run npm run check before handoff. Do not deploy or invite collaborators unless requested.
