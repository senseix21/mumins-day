# Sources of `content/local/hadith/` (ADR-28)

**No hadith text is in this repository.** The files live in `content/local/hadith/`, which `.gitignore` excludes, on
the machine that fetched them. This record holds only where they came from, when, how many hadiths there are and each
file's SHA-256, so any copy can be checked byte for byte. The repository is public: committing the text would republish
it, and nobody has given permission for that.

## What and from where

- **API:** fawazahmed0's Hadith API, version 1 (`https://github.com/fawazahmed0/hadith-api`, branch `1`), served by
  jsDelivr at `https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/`. The founder named its demo site
  `https://fawazahmed0.github.io/hadiths/` (2026-11-28, in chat: "use it"); the site is listed in the API's README as a
  "Community Project using Hadith API" (KNOWN, README fetched below).
- **Books (8), each two editions:** the Bengali `ben-<key>` and the Arabic `ara-<key>`, for `bukhari`, `muslim`,
  `abudawud`, `tirmidhi`, `nasai`, `ibnmajah`, `malik`, `nawawi` (KNOWN: these are every book with a Bengali edition in
  `editions.min.json`; `dehlawi` and `qudsi` have none).
- **Files fetched** (`curl -sSfL`, 2026-11-28 21:44 to about 21:47 +0600, KNOWN: `TZ=Asia/Dhaka date` before and the
  background job's end), each from `https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/<path>`:
  - `editions.min.json`, `info.min.json`
  - `editions/ben-<key>.min.json` and `editions/ara-<key>.min.json` for each of the 8 keys
- **The `.min.json` variant is kept.** For every kept file the `.json` variant was fetched too and parses to the same
  JSON value (KNOWN: `python3 -I`, `json.load(a) == json.load(b)` true for all 18 pairs; `ara-nawawi1.json` returned 403,
  and that edition is not used). The minified files are smaller (`ben-bukhari` 14,712,741 bytes against 15,073,177).
- Downloaded into a new, empty scratch directory, then copied into `content/local/hadith/` on 2026-11-28 21:52 +0600;
  `cmp` against the download: identical for all 18 files.
- **Not kept:** the `.json` variants, the `ara-<key>1` editions, `LICENSE`, `README.md`, `References.md` and four
  `sections/<n>.json` responses fetched only to learn how the API groups sections.

## Which Arabic edition, and why

- `ara-<key>` is shown. `editions.min.json` describes `ara-<key>1` as "Diacritics removed for easier searching" and gives
  `ara-<key>` no comment (KNOWN, read in the file). Counted in the files (`python3 -I`, U+064B to U+0652): `ara-bukhari`
  1,665,790 such marks, `ara-bukhari1` 0; every `ara-<key>1` has 0, every `ara-<key>` has more than 8,000.
- Every edition's `author` is "Unknown" except an-Nawawi's ("Imam Nawawi"); every `source` is empty (KNOWN, same file).

## Who wrote it, and the permission

- **Licence of the repository:** its `LICENSE` file (fetched from
  `https://raw.githubusercontent.com/fawazahmed0/hadith-api/1/LICENSE`, 2026-11-28 21:44 +0600) is the Unlicense. It
  begins: "This is free and unencumbered software released into the public domain." and "In jurisdictions that recognize
  copyright laws, the author or authors of this software dedicate any and all copyright interest in the software to the
  public domain." and ends "For more information, please refer to <https://unlicense.org>". It speaks of "software" and
  of its own authors' interest; it does not say who translated the Bengali or that the translator gave it away.
- **What the README says about sources:** "References: All the open source projects and dawah/Islamic organizations.
  Please see References". `References.md` ("a list of websites that helped me either directly or indirectly") names, among
  others, `https://github.com/alQuranBD/Bangla-Hadith-api`, `https://www.hadithbd.com`, `https://sunnah.com/` and, for the
  grades, al-maktaba.org books per grader (KNOWN, both fetched 2026-11-28). It does not say which source each edition came from.
- **Bengali translation: translator UNKNOWN.** `editions.min.json` says "Unknown". Some Bengali texts end with a note
  naming other publishers' numbering (in Bukhari, Adhunik Prokashoni's and the Islamic Foundation's), which is a lead, not an
  attribution. How to find out: compare with printed Bengali editions or ask the API's author and the `Bangla-Hadith-api` authors.
- **Founder's decision, 2026-11-28, in chat:** "https://fawazahmed0.github.io/hadiths/ use it"; earlier the same day
  he said licensing is his to settle later. The founder owns the permission question (Q17). Until it is settled the text
  stays local and out of every release (ADR-28).
- **Not reviewed.** No reviewer has read any of it, the grades included (INV-C1, INV-C6). It is not the reviewed hadith
  kind of ADR-22.

## Sections

The API's own section files (`editions/<edition>/sections/<n>.json`) hold exactly the hadiths whose `reference.book` is
`n`, not the ranges in `metadata.section_details` (KNOWN: fetched `ben-bukhari` sections 0 and 6, `ben-muslim` 2,
`ben-nasai` 0, `ben-malik` 1 and compared with both rules; the ranges disagree for 4 of them). The app groups the same
way. Section names are the source's, in English; section 0 of Bukhari (311 hadiths), an-Nasai (82) and Malik (18) has the
empty name. `info.min.json` holds the same sections and, for every hadith, the same grades and reference as the editions
(KNOWN, compared with `python3 -I`); it also has `last_hadithnumber`. Neither `info` nor `editions` is read by the app.

## Counts (KNOWN, counted in the fetched files; checked by `checkHadithBooks` and `HadithBooksRealFilesTest`)

`hadiths`: numbers in either edition. `bengali`, `arabic`: hadiths whose text in that edition is not blank. `graded`:
hadiths with at least one grade. `sections`: sections holding a hadith. `unpaired`: numbers in only one edition.

| Book | hadiths | sections | Bengali | Arabic | graded | unpaired |
|---|---|---|---|---|---|---|
| bukhari | 7589 | 98 | 7529 | 7580 | 0 | 0 |
| muslim | 7563 | 57 | 7360 | 7360 | 0 | 0 |
| abudawud | 5274 | 43 | 5270 | 5272 | 5274 | 0 |
| tirmidhi | 3998 | 49 | 3895 | 3924 | 3954 | 0 |
| nasai | 5765 | 52 | 5656 | 5679 | 5758 | 0 |
| ibnmajah | 4343 | 38 | 4336 | 4338 | 4341 | 0 |
| malik | 1858 | 62 | 1749 | 1829 | 1858 | 0 |
| nawawi | 42 | 1 | 42 | 42 | 0 | 0 |

Some numbers have no text in either edition (Bukhari 6, Muslim 167, Tirmidhi 48, an-Nasai 83, Ibn Majah 2, Malik 20);
the screen says the source has no text for them. Numbers like `402.2` are the source's own. Muslim's `arabicnumber` is a
text such as "8.01" and 344 of its hadiths have none. One Arabic text (an-Nawawi 2) holds a balanced, empty RLE PDF pair.

count bukhari hadiths 7589
count bukhari sections 98
count bukhari bengali 7529
count bukhari arabic 7580
count bukhari graded 0
count bukhari unpaired 0
count muslim hadiths 7563
count muslim sections 57
count muslim bengali 7360
count muslim arabic 7360
count muslim graded 0
count muslim unpaired 0
count abudawud hadiths 5274
count abudawud sections 43
count abudawud bengali 5270
count abudawud arabic 5272
count abudawud graded 5274
count abudawud unpaired 0
count tirmidhi hadiths 3998
count tirmidhi sections 49
count tirmidhi bengali 3895
count tirmidhi arabic 3924
count tirmidhi graded 3954
count tirmidhi unpaired 0
count nasai hadiths 5765
count nasai sections 52
count nasai bengali 5656
count nasai arabic 5679
count nasai graded 5758
count nasai unpaired 0
count ibnmajah hadiths 4343
count ibnmajah sections 38
count ibnmajah bengali 4336
count ibnmajah arabic 4338
count ibnmajah graded 4341
count ibnmajah unpaired 0
count malik hadiths 1858
count malik sections 62
count malik bengali 1749
count malik arabic 1829
count malik graded 1858
count malik unpaired 0
count nawawi hadiths 42
count nawawi sections 1
count nawawi bengali 42
count nawawi arabic 42
count nawawi graded 0
count nawawi unpaired 0

## How to fetch again

`tools/fetch-hadith-books.sh` downloads the 18 files into a new scratch folder, checks each against the lines below and
only then copies them into `content/local/hadith/`. It is never run by the build: the app and the build make no network
request (INV-P1). Run it by hand.

## SHA-256 of every file, path relative to `content/local/hadith/` (`shasum -a 256`, 2026-11-28)

sha256 0dd43ab2a1c7ab2a3010c1bedaccea2977af6128f3a9402c075ea64d908ad190 editions.min.json
sha256 6f5eade2d7774643066213137b19b6076e4386c0c235917c91be2dec6694d134 info.min.json
sha256 2886f6ec0af0833a7529ff63c608e035e61045b81788c08d2d23755f2df553ef editions/ben-bukhari.min.json
sha256 f887d8724f75fb6e1d5d29342a173af5346ad9b933173c85e6e3cb570063ea33 editions/ara-bukhari.min.json
sha256 f24bf6d942fd96290abe37458214cb30f98b3f5f25109e388d3b04e5028d6ad0 editions/ben-muslim.min.json
sha256 2cb296f3455ff8a3da9f6a0ca3de75da675788b2fb446ad316df297838e48217 editions/ara-muslim.min.json
sha256 4dedfec0135b153cbc2a029b3419ea3f4f52a4c73266e161e660c0fc983ee31d editions/ben-abudawud.min.json
sha256 30dd3792ea65abe7fd0be8d568809d4eac04e38cbbd588b9c6055218fe43675f editions/ara-abudawud.min.json
sha256 4a02895ef4db491c6cc27396007f0bb7779198d27895afa0992ba2931e2f0f19 editions/ben-tirmidhi.min.json
sha256 22b7ffbbca9beb5d4f108cc0389a3cd16917876b3ceb32993c46e1554afd871b editions/ara-tirmidhi.min.json
sha256 97adbefc3f3f9a1286fdc1cb954927da75112858e36bc6b3752a3a39bba222e3 editions/ben-nasai.min.json
sha256 18294d8da9ec9a735719f26709858ac30657c1122f6a39d179f128bfc59ecb80 editions/ara-nasai.min.json
sha256 6f613e145084705f57c11586cf2e8eb399775fdf503e0d1ae017235f873d63ef editions/ben-ibnmajah.min.json
sha256 495fcccdea5f2d157794d2b1bd1eaa0e0a5ff5aeb6be2d7817de8f25e3b3349c editions/ara-ibnmajah.min.json
sha256 ef722d6a5a678e350157d3b55ece9dd74d3a63bdac5e286739b7c64470594a1d editions/ben-malik.min.json
sha256 8bcc8001df35cfff1697f792d99d9fa5a865e7d89734ff8a77bd49d12f7465b7 editions/ara-malik.min.json
sha256 f0b3847f1a29e3c9d11a3f292021963140cf56604357709b3a9cf09e1a914db6 editions/ben-nawawi.min.json
sha256 862c35fbc6af4bc8c1e50e776d37b67a7f6720ee540b30d2747987ec22047b6b editions/ara-nawawi.min.json
