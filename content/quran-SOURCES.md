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

## The Bengali translations: QuranEnc.com (ADR-26)

The founder decided on 2026-11-28 (in chat) to bundle both of QuranEnc's Bengali translations and let the user pick, and
not to use github.com/imranpollob/bangla-quran (its text states no source or permission). Both are kept in
`content/quran/translations/<key>/` exactly as QuranEnc served them, hashed below, and staged into the app unchanged.

### The permission: QuranEnc's published terms

**The recorded permission is QuranEnc's published text, not a letter.** No written email or letter from QuranEnc is
held (UNKNOWN whether they would give one; the founder may ask). The text, quoted exactly from the "Terms and Policies"
dialog of https://quranenc.com/en/home/api, fetched with `curl` at 2026-11-28 19:01 +0600 (KNOWN; also linked from
https://quranenc.com/en/home, "Contents of the translations can be downloaded and re-published, subject to these Terms
and Policies"), and copied into `content/quranenc-terms.txt`, the file the `permission` lines below name:

```
Contents of the translations can be downloaded and re-published, with the following terms and conditions:
1. No modification, addition, or deletion of the content.
2. Clearly referring to the publisher and the source (QuranEnc.com).
3. Mentioning the version number when re-publishing the translation.
4. Keeping the transcript information inside the document.
5. Notifying the source (QuranEnc.com) of any note on the translation.
6. Updating the translation according to the latest version issued from the source (QuranEnc.com).
7. Inappropriate advertisements must not be included when displaying translations of the meanings of the Noble Quran.
```

The page lists seven conditions (an earlier note had five). How the app meets them (ASSUMED reading, not a lawyer's):

1. The files ship byte for byte (T-BUILD-6); the reader shows each ayah's `translation` and `footnotes` strings exactly,
   footnote markers such as `[১]` and the `<b>` markup in Zakaria's footnotes included (G65); normalisation happens only
   in the search index (ADR-23, ADR-26).
2. and 3. Under the translation on the reader, and on the Licences screen: QuranEnc's own title and description of the
   translation, "QuranEnc.com" and the version. `NOTICE` says the same.
4. Rowwad's file keeps its `<meta>` block, and the Licences screen shows it as shipped. Zakaria's sura API responses carry
   no transcript block (KNOWN: keys `result`, `id`, `sura`, `aya`, `arabic_text`, `translation`, `footnotes` only); its
   "transcript information" is recorded here and on the Licences screen instead.
5. Notifying QuranEnc of any note on the translation: the founder's (`PLAN` founder track). The app has no way to send.
6. Updating to the latest version: only by an app update (no network). The founder checks QuranEnc before each release
   (`PLAN` founder track); Rowwad's file names its own check URL in `<meta>`.
7. The app has no advertisements (INV-P1, no network).

### `bengali_rwwad`: Rowwad Translation Center, version 1.1.4

- **As QuranEnc states it** (https://quranenc.com/en/home, read 2026-11-28 19:01 +0600): "23/11/2026 - V1.1.4",
  "Bengali Translation - Rowwad Translation Center", "Translated by the team of Rowwad Translation Center, in cooperation
  with the Dawah Association in Rabwah, the Islamic Content Service Association in Languages, and the IslamHouse.com
  website." In Bengali (https://quranenc.com/bn/home): "বাংলা ভাষায় অনুবাদ - রুওয়াদ অনুবাদ", "রাবওয়ার দাওয়াহ সেন্টার, বহু ভাষায় ইসলামী
  বিষয়সমূহের সেবামূলক সংস্থা এবং IslamHouse.com এর সাথে যৌথ সহযোগিতায় রুওয়াদ অনুবাদ সেন্টার এর একদল আলেমের দ্বারা অনূদিত।"
- **API record** (https://quranenc.com/api/v1/translations/list/bn, fetched 2026-11-28 19:01 +0600): `"key":"bengali_rwwad"`,
  `"version":"1.1.4"`, `"last_update":1795425265` (2026-11-23 15:14:25 +0600), with `xml_url`
  https://quranenc.com/downloads/xml/bengali_rwwad_v1.1.4-xml.1.xml.
- **Downloaded** 2026-11-28 19:04 +0600 with `curl` from that `xml_url` (3,402,353 bytes); the index page's XML button
  (https://quranenc.com/en/home/d/xml/bengali_rwwad) served the same bytes (`shasum`). Shipped as
  `translations/bengali_rwwad/bengali_rwwad_v1.1.4-xml.1.xml`, its served name. Its `<meta>` says
  `<updated_at>2026-11-23 12:14:25 (v1.1.4-xml.1)</updated_at>` and
  `<check_updates>https://quranenc.com/check/bengali_rwwad/v1.1.4-xml.1</check_updates>`.
- **Checked** (Python `xml.etree` in a scratch script, and `QuranEncTest`): 114 suras, 6236 ayahs, every sura's count equal
  to `quran-data.xml`; 1,219 ayahs have footnotes; CRLF line ends only between elements, never inside a translation.

### `bengali_zakaria`: Dr. Abu Bakr Muhammad Zakaria, version 1.1.1

- **As QuranEnc states it** (https://quranenc.com/en/home, read 2026-11-28 19:01 +0600): "22/05/2021 - V1.1.1",
  "Bengali Translation - Abu Bakr Zakaria", "Bengali Translation of Dr. Abu Bakr Muhammad Zakaria". In Bengali
  (https://quranenc.com/bn/home): "বাংলা ভাষায় অনুবাদ- ড. আবূ বকর মুহাম্মাদ যাকারিয়া", "বাংলা ভাষায় অনুবাদ- ড. আবূ বকর মুহাম্মাদ যাকারিয়া কর্তৃক অনূদিত।"
  QuranEnc names no other publisher for it on those pages (the original print publisher is UNKNOWN here; not shown).
- **Not offered for download (KNOWN, and a risk).** On the index page Zakaria has no XML, CSV, Excel, SQLite or API
  button and no "Terms and Policies" link next to it, which Rowwad has; `bengali_zakaria` is absent from
  https://quranenc.com/api/v1/translations/list and `/list/bn` (only `bengali_rwwad` is listed for `bn`); the XML button's
  address (https://quranenc.com/en/home/d/xml/bengali_zakaria) returned an empty body. Its text is served by the
  documented sura API. Whether QuranEnc's terms cover re-publishing a translation it does not offer for download is
  UNKNOWN; ask QuranEnc before a store release (Q13, `PLAN` founder track).
- **Downloaded** with `curl` from `https://quranenc.com/api/v1/translation/sura/bengali_zakaria/{1..114}`: suras 1 to 101
  at 2026-11-28 19:04 +0600 (the run stalled after 101) and 102 to 114 at 20:13 +0600, every one HTTP 200. Stored byte
  for byte as `translations/bengali_zakaria/001.json` to `114.json` (only the file names zero-padded). 30,607,538 bytes
  in all: the API escapes every Bengali character as `\uxxxx`.
- **Version:** V1.1.1 is what the index page shows; the responses carry no version (ASSUMED: the API serves the listed
  version).
- **Checked** (Python `json` in a scratch script, and `QuranEncTest`): 6236 ayahs, every sura's count equal to
  `quran-data.xml`, `sura` and `aya` in order; 3,608 ayahs have footnotes; re-encoding the decoded values in PHP's
  `json_encode` style gives back all 114 files byte for byte. The `arabic_text` field is QuranEnc's Arabic and is never
  shown: the reader shows Tanzil's (above).

### Permission lines (read by T-BUILD-6: `permission <edition key> <file under content/>`)

```
permission bengali_rwwad quranenc-terms.txt
permission bengali_zakaria quranenc-terms.txt
```

## Recorded hashes (read by T-BUILD-6; one `sha256 <hex> <path under content/quran/>` line per file)

Translation hashes computed 2026-11-28 with `shasum -a 256` on the downloaded copies and again in `content/quran/`
(identical, `cmp` of the two lists).

```
sha256 6933e133dd56db778c801bf738848454e43648105a151e8d84d86a7cae39ec5f quran-uthmani.txt
sha256 054b3d9f79c0c2e44df7f9ddf42561797b3b5cb4fbdafbf2e99c805ccf1a6b49 quran-simple-clean.txt
sha256 8867c1d88191472adec9db694b3cd9f135b1a2ef580574d32cf888dcb22c5c7a quran-data.xml
sha256 e2a47644762d16bdfb6d33e0d8db8c6ff30beae84150ef5a705316bbd829455c AmiriQuran.ttf
sha256 72de68e5954f4fdd24702292ef5a32f003ca960ec9330dc86e5eefb5dffb9b22 Amiri-OFL.txt
sha256 e53410ffef86f81ec569445694ccbf45327d8368cd158bc864a8fa826eb31939 translations/bengali_rwwad/bengali_rwwad_v1.1.4-xml.1.xml
sha256 64aa61a8616b5fd2f20dfe39d209964061606cd87ca8a745ded92675396d946c translations/bengali_zakaria/001.json
sha256 85a8097965c0187a53481a21283cd6122dea4dd59d43f8f6be9f3d9af8a84224 translations/bengali_zakaria/002.json
sha256 b07872eb02a98ac1a1700f6988c033d6718bdc4b456889ab4722d1e6c240c9b3 translations/bengali_zakaria/003.json
sha256 876e173cffe70a63022f2b4665983e3a862b314eba9c511a93314711579708aa translations/bengali_zakaria/004.json
sha256 e77108db9630171f6276bd9e83b8925fe530cf93252cafc78a0677563bb959b3 translations/bengali_zakaria/005.json
sha256 7ca1e9e97e0614d2c89e994a7b2f553a9158e2b190aaeb9889e6896ce5582999 translations/bengali_zakaria/006.json
sha256 62c19cdfba1292166b64499484c59e97c20334057233e7513a39a0258b3bf1e8 translations/bengali_zakaria/007.json
sha256 d6fb93220b658d52a8475fab26f14bb0b9867b56de6903e67c57e731fad1436d translations/bengali_zakaria/008.json
sha256 453ea899e90fc5187d01d1696247480486efb925e24b91be4bc18945d3a01c44 translations/bengali_zakaria/009.json
sha256 4d596cec5cef35fe0c20f9ce2cf59c2da2090961f9a21e908273bc1b5b2eac90 translations/bengali_zakaria/010.json
sha256 a37ef69fa39424aba242f2bce92e392d1d7e679c9f63c49a5bc41c72a5588336 translations/bengali_zakaria/011.json
sha256 54f82d60892bb28aacbc80edb2299d527d3823fef095ceb0c2d9570ec6971964 translations/bengali_zakaria/012.json
sha256 3d2fabc33ca4fc55cdafa5746485f2fd2429ed9f70f8ac17cb9f6d990f49fc0d translations/bengali_zakaria/013.json
sha256 44c280f51387da1e8b04f251a0bc1d88b944e5f738bd5f087cced1ad54dbd1ae translations/bengali_zakaria/014.json
sha256 d9226ac2e5ff972b170709a1680ba7ed0f1ec47ef67b1d5e726e8eb0cf34f965 translations/bengali_zakaria/015.json
sha256 8eff4d5e41b305430a34fb1fe6bb90092596f4ded296794c5e3c1724a38bf6f8 translations/bengali_zakaria/016.json
sha256 ca7f2a05455bc7f45431877edb97f087e9c0c6c19be33544dc1ccdc44a286acd translations/bengali_zakaria/017.json
sha256 083d09bcef69103910dc4d21493572bbb92f02693b238f077cd23886df0cd232 translations/bengali_zakaria/018.json
sha256 7fbf0586a750a4d089945aa924bacb0bad5885e33b7c404592c8c2cc6c4c79a8 translations/bengali_zakaria/019.json
sha256 15aa235d0b6895fcefe07f385113e640a5669318646bc99b932b37f5e7d9cbd1 translations/bengali_zakaria/020.json
sha256 8d6fa3c40e056e1be43512a8a75d22ebfd89c00630fdc9cd7a428a2769081243 translations/bengali_zakaria/021.json
sha256 6c41999c94acf0eb5ee58a8d0f11ba72b77faff516432585d9c2c6d2ffc9bf15 translations/bengali_zakaria/022.json
sha256 03d52e219dca7f11b37014832cb51464bf3984087c1d915011f845913d7a45cc translations/bengali_zakaria/023.json
sha256 c925560f06dab925719a82828eddb16031529cd3134076705485ba900adc701b translations/bengali_zakaria/024.json
sha256 ebba5501b7d6dad112679a7080a8464241528775e2a9182a5542c93d89fb4699 translations/bengali_zakaria/025.json
sha256 838327ef69338eb8c51be9709d52bd43cd407a772fca3f055d82e2910f1f9cac translations/bengali_zakaria/026.json
sha256 d5a32d5951e709cc363e2cf9ccfe9c62263d2831498e41a661fa7eb189c82f12 translations/bengali_zakaria/027.json
sha256 28925860fe287352562e65bda7a58aef56f2c9f4b4971e9ba0b80709a36cec37 translations/bengali_zakaria/028.json
sha256 3afa55a0139c26d5c65d5107ceda80405bedec4057c974eb7b9103ecbadedf9a translations/bengali_zakaria/029.json
sha256 8ea25bbef46cc22b2062217b092843910e784c44c65c4626336824ab1f6ff975 translations/bengali_zakaria/030.json
sha256 c4b3446acd652fd8dd26bab8e8b4e873b689daa8e0137185173cab118feaa514 translations/bengali_zakaria/031.json
sha256 2aa8d6aa83d878a905843b50a96324620794fed8b21fe0acf5cd3513a8c0298f translations/bengali_zakaria/032.json
sha256 1523a6f239fa0daa125e64d1c06de65f66649a1c90d342f27cd9912567f62992 translations/bengali_zakaria/033.json
sha256 e193ab7d0b67787b9e2497609959cb25cff641d0ed09e1afcbcfa5263ccac74b translations/bengali_zakaria/034.json
sha256 04de193a57c9c184fc653df4204ad6220565bdc2d661db41aac5b84feb1ba77f translations/bengali_zakaria/035.json
sha256 16f5bdd3e907a6e1948d6642cfa2418f402c347c32702e4b3ecf7fcdc1ec93e1 translations/bengali_zakaria/036.json
sha256 fcb1fe5a76c5ec8bd909139d5ad270d2ec1a89f881c175daaa6dd2349dddad3e translations/bengali_zakaria/037.json
sha256 022d725d10d6e59ec72d500f56e3984ff084ba5c9b6a3d7f2dd239af2a566baa translations/bengali_zakaria/038.json
sha256 6ac83f74f0d24be6b4df660a22b584651795b88cc801033fa897957b91ffa842 translations/bengali_zakaria/039.json
sha256 39decbb29ec305f8cc50726635e0821445d7c7a81900cf6a821f2f2690d543f9 translations/bengali_zakaria/040.json
sha256 03f686e4c1c688c6b4d6e065a51199bb08feeb6960a3e77f4a1aab1aeeee3092 translations/bengali_zakaria/041.json
sha256 dc340131ecc652accdc8cad562530bf386f878032306ec659f47010c5c345250 translations/bengali_zakaria/042.json
sha256 95efd911b7380c2f1dbefe1aca2e652ff7effee47c576a799abd5a8f6d9f15e3 translations/bengali_zakaria/043.json
sha256 14958dab9df873ae79841d64aceeca9e042f9794f039ec1566ddf672e8d3dcc0 translations/bengali_zakaria/044.json
sha256 f5eafd4a5936d509ab4b3c49ac6eb6db1000700173c0fe449c257ca217b4eda9 translations/bengali_zakaria/045.json
sha256 f1a847897619d109e0f0f068dbfe42007ca9e51f5e69513678a1a9361a3b809f translations/bengali_zakaria/046.json
sha256 63bc7cacb8bc85b08929004724aeb432095bd03b5a9553a93da5354e34224b3d translations/bengali_zakaria/047.json
sha256 7215da452332b39bad1c39e152f2a0535d68b70559cf426e1d5b2d62156c26cf translations/bengali_zakaria/048.json
sha256 09c0b5fcd4732191e1d461c7e1acbf09e7f1b4d94a049791e3d48557fa8611af translations/bengali_zakaria/049.json
sha256 84c68f5797c15f280bc155f38c4d3176fae200c94e6d499e7ef5f1716e42fe6c translations/bengali_zakaria/050.json
sha256 fa77abca6011c6c4f759d8e1f549427befcba31ef11ab84ae590ce614f6ec09f translations/bengali_zakaria/051.json
sha256 5df3996b9ace3d5d7a5b556256971d4f3cb995ba25e729f80afa400f22042669 translations/bengali_zakaria/052.json
sha256 4da8e89f036c0dd8531dae648d95600ae250ffa3ae0f281f2fa09a4881334696 translations/bengali_zakaria/053.json
sha256 4ddf92cad77885140096433df47209d00f2129821696e374b54ff58a9c7a9cce translations/bengali_zakaria/054.json
sha256 4c551a5d2b1ef65a7511c53f0ec5c8effb7bd73062610f1847dc35e7bb0f36bf translations/bengali_zakaria/055.json
sha256 986bb8e223e05aa0fc3de192c71159e8b6de236a88df00722ed5feed9c38ff01 translations/bengali_zakaria/056.json
sha256 f086545c6515dc5178e6c7eb222694931e9b6850efc80349465e5b7790667557 translations/bengali_zakaria/057.json
sha256 41d39e585437e7c719e870e78e1d84fb23155b78a0cc7842b9ce9256beb28464 translations/bengali_zakaria/058.json
sha256 2b8b4b26dc9586df42cd544fd4f1074fc830f09327b17d7b70bb36a9fe4c590f translations/bengali_zakaria/059.json
sha256 4681f5eb53480f6b382163d8b4b5751790cdf3a7ddcb4ef9f90803bbbe7270e9 translations/bengali_zakaria/060.json
sha256 7f81dd51579dc988ce61456047c7844c70709884a8ecb17dfe2206cc493cfbeb translations/bengali_zakaria/061.json
sha256 5193565f4763e6c30315aaea16b3db4be3d008df40edbaf5880845f7ff2e6271 translations/bengali_zakaria/062.json
sha256 52627967a6678351eed56f2a60f2285bb66c03b151244d9b256c628c58238652 translations/bengali_zakaria/063.json
sha256 91bf02fb65933e2ad0c91fc26a3c2832581512a80038e50007411a87c6b7d8b6 translations/bengali_zakaria/064.json
sha256 79784c0fc2653a954fcb5a044db1205c75c1b6c869b4c30385613f67184f8ba3 translations/bengali_zakaria/065.json
sha256 08505ed3c52d346126f6b7893065e3ae9a73a078d6ab92b5ddc3dc5806110c42 translations/bengali_zakaria/066.json
sha256 5850a5a7c371e764e3d2560d40472d03f9b1a73ab015263d5dd6e1ed9dfd6804 translations/bengali_zakaria/067.json
sha256 3016432fac1fe5cf0cb507b5b36fd0223598d38f5fe15609ee6053715abc3d97 translations/bengali_zakaria/068.json
sha256 40c78cf70e917333105781f8a6eb93306e1bd2379145d056c80fe39e9e92e744 translations/bengali_zakaria/069.json
sha256 70e9403b0ab723c1b9d445ad7ae2694df3544af2bb28ea49cf46c702ce066939 translations/bengali_zakaria/070.json
sha256 649d89cc1f1921973333fe88e356dac102e0b5216b6fed7f3dcd3be93775d6ac translations/bengali_zakaria/071.json
sha256 dae188eea4e13848d71ffc21246586ce159379f90c85f04a411c715d7ac28106 translations/bengali_zakaria/072.json
sha256 6b6cbb1c1b7e53f6a81a199b1a1e651a76d07c203a5855bc474cb6aaf0e01362 translations/bengali_zakaria/073.json
sha256 0aa751602f5fce525ae72a0cbee42fd6f9d52ee6c9c3fb12b08271ad80f54726 translations/bengali_zakaria/074.json
sha256 bb4c6843b8de6a9ef78a2b01f5dda0292f657b8fea0cbb9de8e0f634e97023f6 translations/bengali_zakaria/075.json
sha256 b73a828e783aaf3778fec0c275c38951a618d36c476f39bc88020e825ceae025 translations/bengali_zakaria/076.json
sha256 1e6e6d642f563be604f456dc196252e76374611102cec207c9d3ab04c14cf571 translations/bengali_zakaria/077.json
sha256 9c9d75660da1dac04a6c7d59d3615470efd86ca093a8047f34baf7d08e22022c translations/bengali_zakaria/078.json
sha256 078d0b008c4b2f165ce203a8050baaae45d07fae170cd617ee5b410c0831fe60 translations/bengali_zakaria/079.json
sha256 456e3d4fb72f3ef685358591f324a5bc08e0edf18c194cf72fcdb63fa30a3ca4 translations/bengali_zakaria/080.json
sha256 5a5594d6213f548a599e2efe2a518ae9cb03664e0d4b378cf35c00d33d55d880 translations/bengali_zakaria/081.json
sha256 a69aa5a8fa8b8f277694988c64d6aefc60041b23d9a5e4888e28d60c0567053e translations/bengali_zakaria/082.json
sha256 0d1f61dcc58c39295ed4b12cfc725b0c3d461b0936dd4220e1e12ccc83676202 translations/bengali_zakaria/083.json
sha256 05de03f06501f77fc1e68cdee77ba8003eb2466cd2fe59c8c9e6a39a8bdb023d translations/bengali_zakaria/084.json
sha256 a8cf896ae7b330cf6bb07aa018bac841a8b7f6566d5cef2b9339b2f34eab2891 translations/bengali_zakaria/085.json
sha256 6afbe132931ec935dd2d513a5f0dd5aa0cd5e9ba0127c70fe70fb11a5e8fba93 translations/bengali_zakaria/086.json
sha256 b5112fe9d609d7883afa917ed547b4bcc3bd4c0ff394a0dfceb82e96ba9cfc55 translations/bengali_zakaria/087.json
sha256 1dcfbfb6ef0be3b870a3d6a8d1a6bd4956af1cd3326e6647eff114773b1e3ee4 translations/bengali_zakaria/088.json
sha256 16c1db0e390b5c310446f2fb1cdaca828afb2608a5a8e3d12cb2d30d250444ac translations/bengali_zakaria/089.json
sha256 94ed03ef697ea4a8c3a8addf7d316d54c6019014d5977de95e355c2221f2c31a translations/bengali_zakaria/090.json
sha256 398fbf8614b7647f46d5ef11a5140517efeab1d01e0ff3e37210ccffee4debe9 translations/bengali_zakaria/091.json
sha256 753f1ed31ba1ce3cf340a781bf10066aca5bc36048992165ce64d27e29a32e63 translations/bengali_zakaria/092.json
sha256 cdadd414bb20acfaff1bdd61b43f72a4dcea5299f2075ce0e2eaf4fc74b6cdef translations/bengali_zakaria/093.json
sha256 835faa076c7e6fb075f149bd2c49b109d83bd9da15835d87a84a2d1e485b9135 translations/bengali_zakaria/094.json
sha256 204e474c113ddc4baccce78be8293a328b1fab3b1833e575b7ce805b3cf0da76 translations/bengali_zakaria/095.json
sha256 35be2249ab8be8905b22d3aa328f1c0edfbda1102d9bd54f31937afe9ef87e7f translations/bengali_zakaria/096.json
sha256 b819039a24015b59752aa504b48a9e17bdec1f4e5f461b92c0d00c5f60cf2cf9 translations/bengali_zakaria/097.json
sha256 c4a8fe7620e941bdc5e766cf6cb8f37d8e6cdbd8ad1b02c4a765d13e64e1417e translations/bengali_zakaria/098.json
sha256 c1c063bb8e9efd97b11f88db77bb4cbf2bdb60cbd53c364ac8b7eb7bf0a996bb translations/bengali_zakaria/099.json
sha256 034f2dc95f0fe38f0d39ec851966d3a7611629d3dde83e03b7e9f5344ee308b8 translations/bengali_zakaria/100.json
sha256 2adb0e8221e3e6ea4afb0f525dc1c98f24f89a241c25a2fb559abc4e7c4d42e0 translations/bengali_zakaria/101.json
sha256 fd33ac88755a2fd5643432bc8af94a43119dddff9a8e72f668a61fa2e58f8937 translations/bengali_zakaria/102.json
sha256 919b8b976bdd42d26071d5b21e6abee6b7fecb5a3b5dbbab64cb68ab50a47072 translations/bengali_zakaria/103.json
sha256 3e7ad9ba6cc55d2a617502a73a4e93ae8fb6d255c12d7ab589b87a0b194ddc71 translations/bengali_zakaria/104.json
sha256 219bcb852eda97af2abc8ab19ee1b9ac10f3665b66abe945068ef2276ccba8ee translations/bengali_zakaria/105.json
sha256 ecabd886e38858d97d425f28fc1650f3acd6f9e2d6d37a569aa3e75559b6e0cd translations/bengali_zakaria/106.json
sha256 723fffd4bf39702f5b0d6a571b29b55d8d4fee5ce60084e78b6c904483f8587c translations/bengali_zakaria/107.json
sha256 7d198ed0b3e1c76932a58fef17110be77c2469e58e0e8bbcbeea2456761daf4b translations/bengali_zakaria/108.json
sha256 9daac2ef3f0d8006f04b70ff262f0c6245b9b151db114474be77a1c6329686f3 translations/bengali_zakaria/109.json
sha256 5856fc6376a9087255f8976b96a44a4cca659239946e2d9dd8dadf3d061b3a6f translations/bengali_zakaria/110.json
sha256 c90ad8ebc46f42ac40d47f237636995d813766361e06b21399d7b1902ecd822d translations/bengali_zakaria/111.json
sha256 6f8314e1f8e67d97d05bad87dd203ecd07fd5481355aece141d10749554e2aaf translations/bengali_zakaria/112.json
sha256 ca31d80e082e76413faf9ad6cb787f162bc2dd66feb68531d029247a196f9abc translations/bengali_zakaria/113.json
sha256 34d1cfbb1847ae9aa182a901aeb25c6106c248108528d75fc322faa43c0557f7 translations/bengali_zakaria/114.json
```
