# Sources of `content/quran/` (phase 14, INV-QT1, ADR-23)

Every file in `content/quran/` is shipped byte for byte as downloaded. Nothing in the repository edits, normalises,
truncates or reorders it; `.gitattributes` stops git converting line endings. The build task
`:tools:content-gate:checkQuranText` (T-BUILD-6) fails if any file's SHA-256 differs from the line recorded below, if a
file is present that is not recorded, or if a recorded file is missing. Hashes were computed with `shasum -a 256` on
the downloaded copies, 2026-11-28, and again after copying into `content/quran/` (identical).

## The Arabic text: Tanzil Quran Text, version 1.1

- **Downloaded** 2026-11-28 17:29 +0600 (Uthmani) and 17:44 +0600 (Simple Clean), with `curl` from the form on
  https://tanzil.net/download/ (KNOWN, the page read the same day; it says "The latest release of the text (Version 1.1)
  is published in February 2021"). Exact URLs:
  - Uthmani: `https://tanzil.net/pub/download/index.php?marks=true&sajdah=true&alef=true&tatweel=true&quranType=uthmani&outType=txt-2&agree=true`
    (the page's default options: pause marks, sajdah signs, superscript alefs and tatweel on; rub-el-hizb signs off).
    Served as `quran-uthmani.txt`. This is the text the reader displays.
  - Simple Clean: the same URL with `quranType=simple-clean`. Served as `quran-simple-clean.txt`. Never displayed: used
    only to build the search index, so that a word typed in ordinary spelling finds the ayah (ADR-23).
- **Format** (`outType=txt-2`, "Text (with aya numbers)"): one line per ayah, `surah|ayah|text`, then the copyright block
  as `#` lines. Both files carry their copyright block at the end; it stays in the shipped files.
- **Version in the files:** "Tanzil Quran Text (Uthmani, Version 1.1)" and "(Simple Clean, Version 1.1)", "Copyright (C)
  2007-2026 Tanzil Project" (KNOWN, read in the files; the licence page says 2007-2021).
- **Licence, quoted exactly from https://tanzil.net/docs/text_license (fetched 2026-11-28 17:29 +0600):**

  ```
  Tanzil Quran Text
  Copyright (C) 2007-2021 Tanzil Project
  License: Creative Commons Attribution 3.0

  This copy of the Quran text is carefully produced, highly
  verified and continuously monitored by a group of specialists
  in Tanzil Project.

  TERMS OF USE:

  - Permission is granted to copy and distribute verbatim copies
    of this text, but CHANGING IT IS NOT ALLOWED.

  - This Quran text can be used in any website or application,
    provided that its source (Tanzil Project) is clearly indicated,
    and a link is made to tanzil.net to enable users to keep
    track of changes.

  - This copyright notice shall be included in all verbatim copies
    of the text, and shall be reproduced appropriately in all files
    derived from or containing substantial portion of this text.

  Please check updates at: http://tanzil.net/updates/
  ```

  The download page repeats the first two terms ("Permission is granted to copy and distribute verbatim copies of the
  Quran text provided here, but changing the text is not allowed. The text can be used in any website or application,
  provided that its source (Tanzil Project) is clearly indicated, and a link is made to tanzil.net to enable users to
  keep track of changes.") and the form requires ticking "I agree with Terms of Use", which the download URL does
  (`agree=true`).
- **How the app meets the terms** (ASSUMED reading, not a lawyer's): the files ship verbatim with their notice; the reader
  and the Licences screen name "Tanzil Project" and "tanzil.net"; the Licences screen shows the copyright block read from
  the shipped file; the search database built on the phone (a derived file) stores the same block in its `notice` table.
  The app has no internet, so "a link" is the address shown as text (the app cannot open a browser to it offline);
  whether that satisfies the term is for the founder and, if needed, a lawyer (Q13).
- **Updates:** https://tanzil.net/updates/ (read 2026-11-28) lists version 1.1 of February 12, 2021 as the latest.
  Tanzil asks users to join its mailing list for critical updates (download page, "What Next?"); not done (founder, Q13).

## The metadata: Tanzil Quran Metadata

- **Downloaded** 2026-11-28 17:29 +0600 from http://tanzil.net/res/text/metadata/quran-data.xml, linked from
  https://tanzil.net/docs/quran_metadata (KNOWN). Its root element says `version="1.0" copyright="(C) 2008-2009
  Tanzil.info" license="cc-by"`.
- **Used for:** the number of ayahs in each surah (`ayas`), checked against the text by T-BUILD-6 and by `QuranTest`, and
  the Arabic surah name (`name`) shown in the surah list. The English and transliterated names in the file are not shown.
- The file mixes CRLF and LF line endings as published; it is kept that way.

## The font: Amiri Quran, version 1.003

- **Downloaded** 2026-11-28 17:31 +0600 (file time of the zip, `ls -lT`) from https://github.com/aliftype/amiri/releases/download/1.003/Amiri-1.003.zip
  (release 1.003, published 2025-06-13 per the GitHub API). Shipped from the zip unchanged: `AmiriQuran.ttf` and
  `OFL.txt` (as `Amiri-OFL.txt`). `OFL.txt` in the zip is identical to https://raw.githubusercontent.com/aliftype/amiri/main/OFL.txt (`diff`).
- **Licence:** SIL Open Font License 1.1, read in full in `Amiri-OFL.txt`. It begins "Copyright 2010-2022 The Amiri
  Project Authors (https://github.com/aliftype/amiri). This Font Software is licensed under the SIL Open Font License,
  Version 1.1." No Reserved Font Name follows the copyright line. Condition 2: "Original or Modified Versions of the Font
  Software may be bundled, redistributed and/or sold with any software, provided that each copy contains the above
  copyright notice and this license." The Licences screen shows `Amiri-OFL.txt` in full.
- **Coverage, measured:** every one of the 69 distinct code points in the Uthmani ayah text is in the font's `cmap`
  (a 40-line Python reader of the `cmap` table, run 2026-11-28: "missing from font: 0"). Whether every combination
  renders well is UNKNOWN until seen on phones (T-UI-3).
- KFGQPC Uthmanic Script was not researched: Amiri Quran's licence was read first and allows bundling.

## No translation

No Bengali or English translation is bundled: no written permission exists (`PLAN` founder track; earlier research says
Tanzil's Bengali translations are non-commercial only). The reader has a translation slot that is empty in a release.
Debug builds fill it with a FAKE generated translation ("TEST TRANSLATION s:a"), never in a release. A real translation
needs a file here, a recorded hash below, and its written permission recorded in `content/` (INV-QT1); T-BUILD-6 refuses
a `translation-*` file without a `permission` line naming an existing file.

## Recorded hashes (read by T-BUILD-6; one `sha256 <hex> <file>` line per file)

```
sha256 6933e133dd56db778c801bf738848454e43648105a151e8d84d86a7cae39ec5f quran-uthmani.txt
sha256 054b3d9f79c0c2e44df7f9ddf42561797b3b5cb4fbdafbf2e99c805ccf1a6b49 quran-simple-clean.txt
sha256 8867c1d88191472adec9db694b3cd9f135b1a2ef580574d32cf888dcb22c5c7a quran-data.xml
sha256 e2a47644762d16bdfb6d33e0d8db8c6ff30beae84150ef5a705316bbd829455c AmiriQuran.ttf
sha256 72de68e5954f4fdd24702292ef5a32f003ca960ec9330dc86e5eefb5dffb9b22 Amiri-OFL.txt
```
