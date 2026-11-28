# Sources for `districts.json`

Retrieved 2026-11-28 14:54 +0600 by Claude, by script, in the cloud session. Nothing in the file was typed from
memory. Re-run the steps below to reproduce or update it.

## What each field is

| Field | Source | Licence |
|---|---|---|
| `id` | Derived from the English name: lowercase, ASCII, hyphens. Never change one after release; settings store it | Ours |
| `name_en` | Wikidata English label of the district item, with " District" removed | CC0 (KNOWN: Wikidata's structured data is CC0) |
| `name_bn` | Wikidata Bengali (`bn`) label, with the suffix " জেলা" (district) removed | CC0 |
| `latitude`, `longitude` | The district's **headquarters town**, not its centroid, because timetables in Bangladesh are given for district headquarters (ASSUMED). Wikidata: the district's "capital" (P36), then that town's coordinates (P625). Checked against the primary coordinate of the town's English Wikipedia article | Wikidata CC0. Where the Wikipedia value was used (rows marked below), a coordinate is a fact; the article text is CC BY-SA (ASSUMED that a bare coordinate carries no licence obligation; part of Q4) |
| `elevation_m` | Open-Meteo elevation API at the chosen point, rounded to whole metres; negatives would be clamped to 0 (none occurred) | Open-Meteo states its data is CC BY 4.0 and the elevation model is Copernicus DEM GLO-90 (ASSUMED, the licence page was not fetched: open-meteo.com is not on the allowed hosts). If CC BY 4.0, the app must credit Open-Meteo and Copernicus: add to the Licences screen before release |

## How it was built

1. Wikidata SPARQL (`https://query.wikidata.org/sparql`): items whose "instance of" is the class
   labelled "district of Bangladesh", excluding dissolved ones (P576), with English and Bengali
   labels, the capital (P36), its coordinates (P625), its English Wikipedia title, and the
   division (P131). Result: 64 districts.
2. English Wikipedia API (`action=query&prop=coordinates&coprimary=primary`) for each
   headquarters town's article.
3. Choice per district: when Wikipedia has a coordinate, use the Wikidata value nearest to it if
   within 0.05 degrees in both axes, otherwise Wikipedia's. When Wikipedia has none, use
   Wikidata's (the most precise one if there are several). Four districts had no usable capital
   on Wikidata or an upazila instead of the town; their town articles were named by Claude:
   Madaripur, Moulvibazar, Jhalokati, Bagerhat.
4. Open-Meteo: one request with all 64 points.

## Checks

- 64 districts, 64 unique ids, 64 unique English names. Per division (KNOWN from P131): Barishal 6,
  Chattogram 11, Dhaka 13, Khulna 10, Mymensingh 4, Rajshahi 8, Rangpur 8, Sylhet 4.
- Every point inside the gate's box (latitude 20.5 to 26.7, longitude 88.0 to 92.8).
- The districts gate passes with the file required (see `context/LOG.md`).

## Needs a person

- **Bengali names:** copied from Wikidata labels, unchecked. A native speaker must read all 64
  (G9). Spellings were not changed by Claude.
- **Rows that did not come straight from Wikidata** (17): listed below with the reason.
  Each is worth one look on a map.
- **Narsingdi elevation 0 m:** the DEM cell may be a river; worth checking. The effect of 10 m
  is under half a minute on sunrise and sunset.
- **Reference point:** the Dhaka timetable comparison (G48) matched best about 0.2 degrees west of
  the city centre, so the point a timetable is computed for may not be the town centre. Revisit
  when T-FIX-4 has more cities.

## Every row

| id | Division | English | Bengali | HQ article | Lat, lon | Elev m | Wikidata | Coordinate source |
|---|---|---|---|---|---|---|---|---|
| `barguna` | Barishal | Barguna | বরগুনা | Barguna | 22.1592, 90.1197 | 9 | Q808172 | Wikidata |
| `barishal` | Barishal | Barishal | বরিশাল | Barisal | 22.7, 90.3667 | 8 | Q1763301 | Wikidata |
| `bhola` | Barishal | Bhola | ভোলা | Bhola (town) | 22.6875, 90.6442 | 8 | Q855042 | Wikidata (nearest of 2 values to Wikipedia) |
| `jhalokati` | Barishal | Jhalokati | ঝালকাঠি | Jhalokati | 22.65, 90.2 | 4 | Q2093327 | Wikidata |
| `patuakhali` | Barishal | Patuakhali | পটুয়াখালী | Patuakhali | 22.3542, 90.3181 | 5 | Q1429761 | Wikidata |
| `pirojpur` | Barishal | Pirojpur | পিরোজপুর | Pirojpur | 22.58, 89.97 | 7 | Q609190 | Wikidata (nearest of 2 values to Wikipedia) |
| `bandarban` | Chattogram | Bandarban | বান্দরবান | Bandarban | 22.1953, 92.2194 | 24 | Q806273 | Wikidata (nearest of 3 values to Wikipedia) |
| `brahmanbaria` | Chattogram | Brahmanbaria | ব্রাহ্মণবাড়িয়া | Brahmanbaria | 23.9723, 91.1099 | 11 | Q897330 | Wikidata |
| `chandpur` | Chattogram | Chandpur | চাঁদপুর | Chandpur, Bangladesh | 23.225, 90.654 | 7 | Q1429697 | Wikidata |
| `chattogram` | Chattogram | Chattogram | চট্টগ্রাম | Chittagong | 22.335, 91.8325 | 17 | Q1074991 | Wikidata |
| `coxs-bazar` | Chattogram | Cox's Bazar | কক্সবাজার | Cox's Bazar | 21.4272, 92.005 | 9 | Q1122278 | Wikipedia (Wikidata differs by 0.156 deg) |
| `cumilla` | Chattogram | Cumilla | কুমিল্লা | Comilla | 23.45, 91.2 | 12 | Q1480778 | Wikidata |
| `feni` | Chattogram | Feni | ফেনী | Feni, Bangladesh | 23.015, 91.3967 | 11 | Q1404741 | Wikidata |
| `khagrachari` | Chattogram | Khagrachari | খাগড়াছড়ি | Khagrachhari | 23.1078, 91.97 | 46 | Q1429685 | Wikidata (nearest of 2 values to Wikipedia) |
| `lakshmipur` | Chattogram | Lakshmipur | লক্ষ্মীপুর | Lakshmipur | 22.9442, 90.83 | 6 | Q1550252 | Wikidata (nearest of 2 values to Wikipedia) |
| `noakhali` | Chattogram | Noakhali | নোয়াখালী | Maijdee Court | 22.8333, 91.1 | 10 | Q68585 | Wikidata (nearest of 2 values to Wikipedia) |
| `rangamati` | Chattogram | Rangamati | রাঙ্গামাটি | Rangamati | 22.6333, 92.1833 | 29 | Q2121686 | Wikidata |
| `dhaka` | Dhaka | Dhaka | ঢাকা | Dhaka | 23.7289, 90.3944 | 11 | Q1850485 | Wikidata |
| `faridpur` | Dhaka | Faridpur | ফরিদপুর | Faridpur, Bangladesh | 23.5986, 89.8353 | 13 | Q2093552 | Wikidata |
| `gazipur` | Dhaka | Gazipur | গাজীপুর | Gazipur | 23.9889, 90.375 | 14 | Q2094101 | Wikidata |
| `gopalganj` | Dhaka | Gopalganj | গোপালগঞ্জ | Gopalganj, Bangladesh | 23.0, 89.8167 | 4 | Q1537813 | Wikidata |
| `kishoreganj` | Dhaka | Kishoreganj | কিশোরগঞ্জ | Kishoreganj | 24.4333, 90.7833 | 13 | Q2344833 | Wikidata |
| `madaripur` | Dhaka | Madaripur | মাদারীপুর | Madaripur | 23.1667, 90.2083 | 11 | Q928836 | Wikipedia (Wikidata has no headquarters coordinate) |
| `manikganj` | Dhaka | Manikganj | মানিকগঞ্জ | Manikganj | 23.8614, 90.0003 | 15 | Q2024719 | Wikidata |
| `munshiganj` | Dhaka | Munshiganj | মুন্সীগঞ্জ | Munshiganj | 23.55, 90.53 | 7 | Q1990519 | Wikidata |
| `narayanganj` | Dhaka | Narayanganj | নারায়ণগঞ্জ | Narayanganj | 23.6167, 90.5 | 12 | Q2208354 | Wikidata (no Wikipedia coordinate) |
| `narsingdi` | Dhaka | Narsingdi | নরসিংদী | Narsingdi | 23.9, 90.7167 | 0 | Q2042638 | Wikidata |
| `rajbari` | Dhaka | Rajbari | রাজবাড়ী | Rajbari, Bangladesh | 23.7642, 89.6475 | 13 | Q2348762 | Wikidata |
| `shariatpur` | Dhaka | Shariatpur | শরীয়তপুর | Shariatpur | 23.2083, 90.35 | 9 | Q253007 | Wikidata |
| `tangail` | Dhaka | Tangail | টাঙ্গাইল | Tangail | 24.2644, 89.9181 | 15 | Q952184 | Wikipedia (Wikidata differs by 0.136 deg) |
| `bagerhat` | Khulna | Bagerhat | বাগেরহাট | Bagerhat | 22.6667, 89.8 | 2 | Q2344861 | Wikidata |
| `chuadanga` | Khulna | Chuadanga | চুয়াডাঙ্গা | Chuadanga | 23.6418, 88.8577 | 14 | Q2094570 | Wikidata |
| `jashore` | Khulna | Jashore | যশোর | Jessore | 23.1667, 89.2167 | 15 | Q1862981 | Wikidata |
| `jhenaidah` | Khulna | Jhenaidah | ঝিনাইদহ | Jhenaidah | 23.5, 89.18 | 8 | Q2188750 | Wikidata |
| `khulna` | Khulna | Khulna | খুলনা | Khulna | 22.8167, 89.55 | 6 | Q2093344 | Wikidata |
| `kushtia` | Khulna | Kushtia | কুষ্টিয়া | Kushtia | 23.9088, 89.1221 | 16 | Q956895 | Wikidata (no Wikipedia coordinate) |
| `magura` | Khulna | Magura | মাগুরা | Magura, Bangladesh | 23.49, 89.42 | 12 | Q2039085 | Wikidata |
| `meherpur` | Khulna | Meherpur | মেহেরপুর | Meherpur | 23.7761, 88.6417 | 17 | Q1474206 | Wikidata |
| `narail` | Khulna | Narail | নড়াইল | Narail | 23.155, 89.4953 | 12 | Q1550260 | Wikidata |
| `satkhira` | Khulna | Satkhira | সাতক্ষীরা | Satkhira | 22.7167, 89.1 | 2 | Q1233680 | Wikidata |
| `jamalpur` | Mymensingh | Jamalpur | জামালপুর | Jamalpur, Bangladesh | 24.92, 89.96 | 21 | Q2039306 | Wikidata (no Wikipedia coordinate, 2 values, most precise used) |
| `mymensingh` | Mymensingh | Mymensingh | ময়মনসিংহ | Mymensingh | 24.75, 90.4167 | 17 | Q1429976 | Wikidata |
| `netrokona` | Mymensingh | Netrokona | নেত্রকোণা | Netrokona | 24.88, 90.73 | 9 | Q2344966 | Wikidata |
| `sherpur` | Mymensingh | Sherpur | শেরপুর | Sherpur, Bangladesh | 25.0192, 90.0136 | 23 | Q2039314 | Wikidata |
| `bogura` | Rajshahi | Bogura | বগুড়া | Bogra | 24.85, 89.3667 | 23 | Q2039066 | Wikidata |
| `chapai-nawabganj` | Rajshahi | Chapai Nawabganj | চাঁপাইনবাবগঞ্জ | Chapai Nawabganj | 24.6, 88.2667 | 16 | Q46043 | Wikidata |
| `joypurhat` | Rajshahi | Joypurhat | জয়পুরহাট | Joypurhat | 25.1014, 89.0272 | 23 | Q2348146 | Wikidata (nearest of 2 values to Wikipedia) |
| `naogaon` | Rajshahi | Naogaon | নওগাঁ | Naogaon | 24.9, 88.75 | 19 | Q2094277 | Wikidata (no Wikipedia coordinate) |
| `natore` | Rajshahi | Natore | নাটোর | Natore | 24.4142, 88.9858 | 18 | Q2093361 | Wikidata |
| `pabna` | Rajshahi | Pabna | পাবনা | Pabna | 24.0078, 89.2389 | 19 | Q1083505 | Wikipedia (Wikidata differs by 0.061 deg) |
| `rajshahi` | Rajshahi | Rajshahi | রাজশাহী | Rajshahi | 24.3667, 88.6 | 25 | Q2344697 | Wikidata |
| `sirajganj` | Rajshahi | Sirajganj | সিরাজগঞ্জ | Sirajganj | 24.45, 89.7167 | 14 | Q2429432 | Wikidata |
| `dinajpur` | Rangpur | Dinajpur | দিনাজপুর | Dinajpur | 25.63, 88.65 | 34 | Q1985120 | Wikidata |
| `gaibandha` | Rangpur | Gaibandha | গাইবান্ধা | Gaibandha | 25.329, 89.5415 | 27 | Q2344595 | Wikidata |
| `kurigram` | Rangpur | Kurigram | কুড়িগ্রাম | Kurigram | 25.8128, 89.6431 | 31 | Q2348751 | Wikidata |
| `lalmonirhat` | Rangpur | Lalmonirhat | লালমনিরহাট | Lalmonirhat | 25.9172, 89.4459 | 38 | Q2373734 | Wikidata |
| `nilphamari` | Rangpur | Nilphamari | নীলফামারী | Nilphamari | 25.9323, 88.8536 | 53 | Q2188627 | Wikidata |
| `panchagarh` | Rangpur | Panchagarh | পঞ্চগড় | Panchagarh | 26.3333, 88.5578 | 78 | Q2367822 | Wikidata |
| `rangpur` | Rangpur | Rangpur | রংপুর | Rangpur, Bangladesh | 25.749, 89.247 | 38 | Q2344570 | Wikidata (nearest of 2 values to Wikipedia) |
| `thakurgaon` | Rangpur | Thakurgaon | ঠাকুরগাঁও | Thakurgaon | 26.0308, 88.47 | 59 | Q2367825 | Wikidata |
| `habiganj` | Sylhet | Habiganj | হবিগঞ্জ | Habiganj | 24.3789, 91.4122 | 12 | Q1438974 | Wikidata |
| `moulvibazar` | Sylhet | Moulvibazar | মৌলভীবাজার | Moulvibazar | 24.4861, 91.7764 | 15 | Q281435 | Wikipedia (Wikidata has no headquarters coordinate) |
| `sunamganj` | Sylhet | Sunamganj | সুনামগঞ্জ | Sunamganj | 25.0667, 91.4 | 11 | Q2093388 | Wikidata |
| `sylhet` | Sylhet | Sylhet | সিলেট | Sylhet | 24.9, 91.8667 | 19 | Q2093352 | Wikidata |
