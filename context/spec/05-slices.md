# 05 · Slice plan

Written 2026-11-26. Proposed by Claude, accepted by the founder ("go with both"). A slice is done
when its tests are green and, from slice 3 on, when it runs on a phone.

| Slice | Builds | Module | Proves | Tests | Needs |
|---|---|---|---|---|---|
| 1 | Gradle scaffold, vendored engine with one change, `clock` wrapper | `:engine:calctimes`, `:core` | Times are ordered, deterministic, safely rounded, never invented | T-PROP-1, 3, 6; T-UNIT-2, 5, 11 | Nothing |
| 2 | `planner`: now + settings to a list of alarm specs; `l10n` Bengali digits | `:core` | Right instants, 24-hour window, idempotent plan | T-PROP-7, T-UNIT-9 | G8, G16 settled |
| 3 | Android shell: apply the plan, receivers, playback, "Test Adhan" | `:app` | An Adhan rings on a real phone with the screen off | T-INST-1…10, T-BUILD-1…3 | Android SDK and a phone (G13); G6, G7; Q5 for the real recording |
| 4 | Home in Bengali, district picker, onboarding, health warnings | `:app` | First run works offline end to end | T-UI-1, T-BUILD-5, M-4 | Q4 districts; native speaker (G9); UI design (G12) |
| 5 | Deed card and the review gate | `:core`, `:app` | Unique content ships only when approved | T-BUILD-4, T-PROP-8, T-UI-2 | Reviewer (G9); G4, G5 settled |
| 6 | Qibla | `:core`, `:app` | Bearing is right; the needle admits doubt | T-UNIT-10, T-INST-12, 13, M-3 | A phone. First to move to v1.1 |

T-FIX-4 (match the reference timetable) attaches to slice 1's code but stays blocked on Q2.

ADR-1's fallback applies to slice 1: more than two weeks, fork upstream instead.
