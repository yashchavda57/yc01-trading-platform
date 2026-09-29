# Diagrams

Two folders, two different questions:

- **`target-state/`** — what the *finished* project (all of `CLAUDE.md`,
  Phases 1-5) is supposed to look like. This barely changes; it's the map.
- **`current-state/`** — what's *actually built and verified* right now. This
  changes after nearly every step.

All diagrams are Mermaid, in plain `.md` files — GitHub renders them natively,
no extra tooling needed to view them.

| Diagram | Target state | Current state |
|---|---|---|
| Overall architecture | [architecture.md](target-state/architecture.md) | [architecture.md](current-state/architecture.md) |
| Kafka topic flow | [kafka-topics.md](target-state/kafka-topics.md) | [kafka-topics.md](current-state/kafka-topics.md) |
| Order lifecycle (sequence) | [order-lifecycle-sequence.md](target-state/order-lifecycle-sequence.md) | [order-lifecycle-sequence.md](current-state/order-lifecycle-sequence.md) |
| Data store ownership | [data-stores.md](target-state/data-stores.md) | [data-stores.md](current-state/data-stores.md) |

## Maintenance policy

`current-state/*` gets updated as part of the same session that lands a step
in `dev-journal/steps/`, not as an afterthought — a step isn't "done" until
the diagram reflects it. `target-state/*` only changes if the plan itself
changes (a new pattern gets added, a service's scope shifts) — it should
almost never need touching just because another step landed.

The root `README.md` is kept in sync with both at the same time: its
architecture snapshot and "what's built" list should never contradict what's
in `current-state/`.
