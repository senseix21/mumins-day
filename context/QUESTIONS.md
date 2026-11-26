# Open questions

The spec refers to Q1 to Q8 but the file that defined them was not among the five documents
received (gap G2). The wording below is **reconstructed from how each id is used in the
spec**. Replace it with the original text if that file exists.

| Id | Question (reconstructed) | Asked of | Blocks | Where it is used | Status |
|---|---|---|---|---|---|
| Q1 | Team size, budget and launch date | Founder | Planning | `01` constraint table | Open |
| Q2 | Which timetable is the reference for Bangladesh (for example the Islamic Foundation district timetable), and where to get a full year of it | Founder, reviewer | INV-T4, T-FIX-4, F2 failure mode 7 | `00` §2, `01`, `02`, `03`, `04` | Open |
| Q3 | Sehri margin (minutes before Fajr, default 0) and whether to show "Sehri end" (rounded down) and "Fajr begins" (rounded up) as two values | Reviewer | INV-T2, INV-T5 | `01`, `02` | Open |
| Q4 | Source and licence for the 64-district list: names in Bengali and English, latitude, longitude, elevation | Founder | F2 data, `districts.json` | `02`, `04` | Open |
| Q5 | The Adhan recording and short tone: who records or licenses them, in writing | Founder | F1 data, release | `02`, `03`, `04` | Open |
| Q6 | Written legal opinion on shipping an LGPL-3.0 library inside a Play-distributed app | Lawyer | ADR-2; any paid tier | `03`, `04` | Open |
| Q7 | Not referenced anywhere in the five documents. Unknown what it was | Founder | Unknown | none | Open |
| Q8 | Bangladesh device facts: brand and Android-version shares, the minimum device, and the exact models for the test matrix | Founder | Device matrix, minSdk | `01`, `04` | Open |

New questions continue from Q9. A closed question keeps its row, with the answer, who gave it
and the date.
