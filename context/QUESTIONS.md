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
| Q9 | How many approved deeds at launch, and the minimum the build should accept (floor is 4; 30 gives a month without a repeat). Opened 2026-11-26 with the G4/G5 proposal | Founder, reviewer | Phase 5 build gate | `GAPS.md` G5 | **Closed 2026-11-26.** Founder: 30 deeds at launch; the build refuses fewer than 30 approved |

**Leads found by web search, 2026-11-27 (none is an answer yet; every licence below was read on its page but not checked by a lawyer).**

- **Q2 (timetable).** The Dhaka Ramadan 2026 table, 30 days, from https://kivabe.com/ramadan/en/?zilla=1, which says it is "based on information of Islamic Foundation, Bangladesh" and links a scan of the original. It is a secondary copy, not the Foundation's own file, and day 20's Fajr (4:49 am) is probably a typo on the page. The same site has a table per district (`?zilla=N`); https://alormela.org/general/ramadan-calendar offers the Foundation's all-district PDF (not fetched). The Foundation's own site, islamicfoundation.gov.bd, was not reached. **Compared 2026-11-27:** the engine reproduces the Dhaka table's Iftar on 30 of 30 days and Fajr within one minute at a reference longitude of 90.2 E (G48). This is evidence for one city and one month from a secondary copy, not the full T-FIX-4.
- **Q4 (districts).** https://github.com/open-admin-data/bangladesh-administrative-divisions, `data/all-district.json`, 64 districts, Bengali and English names, CC-BY-4.0 (README and LICENSE; attribution required). Provenance UNKNOWN (empty `source` fields). No elevation. The meaning of the coordinates is not stated, and Dhaka's point (23.788, 90.251) is about 16 km west of the usual city centre (ASSUMED, general knowledge), about 40 seconds of prayer time: not good enough without a check against each district's own timetable.
- **Q5 (Adhan).** https://commons.wikimedia.org/wiki/File:Beautiful_adhan.ogg: CC0 1.0, author Adam-synagda, "own work", 2 min 34 s, Ogg Vorbis, 1.17 MB; reciter and place of recording not stated. Alternative: https://archive.org/details/adhan.recordings.from.doha.qatar, one Ogg per prayer (Fajr to Isha), Public Domain Mark 1.0 with no stated basis, creator "Doha, Qatar", uploader unknown. Neither names the reciter, so permission from the recorded person is UNKNOWN. A person should listen before any is chosen.

New questions continue from Q10. A closed question keeps its row, with the answer, who gave it
and the date.
