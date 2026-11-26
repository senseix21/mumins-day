---
description: Update the context folder after a task
---

Bring `context/` in line with the work just finished.

1. Run `TZ=Asia/Dhaka date '+%Y-%m-%d %H:%M %z'` and use its output as the timestamp.
2. Rewrite `context/STATE.md`: what exists now, what is next, what is blocked. Set
   "Last updated" to the timestamp.
3. Append one entry to `context/LOG.md` in the shape given at the top of that file. Under
   "Verified", list only what was actually run or read, with the result.
4. In `context/QUESTIONS.md` and `context/GAPS.md`, close anything this task settled (keep
   the row, add the answer and date) and add anything it opened.
5. If a decision changed, update the matching file in `context/spec/` and its ADR.
6. Report in three lines what you changed in `context/`.

$ARGUMENTS
