package com.senseix21.muminsday.clock

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * T-FIX-4 for Dhaka (INV-T4, ADR-16): the engine with [Calibration.islamicFoundation] against the Islamic Foundation,
 * Bangladesh, permanent Dhaka timetable ("নামায ও রোযার স্থায়ী সময়সূচী").
 *
 * Source: the Foundation's PDF as mirrored at https://kivabe.com/namaz/Calender_permanent_namaz_time.pdf (KNOWN,
 * fetched 2026-11-28; SHA-256 13ced531a4955a546f5edb536559578ee2771867280fbfca7a42d541390da7df; PDF metadata
 * "Microsoft Office Word 2007", 18 Aug 2015). The Foundation's own site did not answer from the cloud environment, so
 * this is a copy: ASSUMED to be unaltered. Rows are days 1, 6, 12, 18, 24 and 30 of every month, valid every year,
 * in minutes after midnight. Values were extracted with `pdftotext -layout`; Bengali headings are SutonnyMJ-encoded
 * and were not needed.
 *
 * Skipped, each a printing error in the table: 30 February (no such day); 24 June Maghrib, printed 5:53, which is
 * 57 minutes before every neighbouring day (6:51 on 18 June, 6:53 on 30 June); its other six times are used.
 *
 * The rule asserted, per prayer, for every row over a full leap cycle (2025 to 2028, so the table's year-independence
 * is tested too): the app is never more than [UNSAFE_LIMIT] minute on the unsafe side of the Foundation, and never
 * more than [SAFE_LIMIT] minutes on the safe side. Unsafe means: Sehri end later than the table, sunrise later than the
 * table, or a prayer beginning earlier than the table (INV-T5).
 */
class IslamicFoundationTableTest {
    private data class Row(val month: Int, val day: Int, val sehri: Int, val fajr: Int, val sunrise: Int, val dhuhr: Int, val asr: Int, val maghrib: Int, val isha: Int)

    private val rows = listOf(
        Row(1, 1, 316, 322, 401, 726, 946, 1047, 1125),
        Row(1, 6, 318, 324, 402, 728, 950, 1050, 1128),
        Row(1, 12, 320, 326, 403, 730, 954, 1054, 1131),
        Row(1, 18, 320, 326, 403, 733, 958, 1058, 1135),
        Row(1, 24, 319, 325, 401, 734, 963, 1063, 1139),
        Row(1, 30, 318, 324, 400, 736, 967, 1067, 1143),
        Row(2, 1, 318, 324, 399, 736, 968, 1068, 1144),
        Row(2, 6, 316, 322, 397, 736, 971, 1072, 1147),
        Row(2, 12, 313, 319, 393, 736, 975, 1075, 1150),
        Row(2, 18, 309, 315, 389, 736, 978, 1079, 1153),
        Row(2, 24, 305, 311, 385, 735, 980, 1082, 1156),
        Row(2, 30, 302, 308, 381, 735, 982, 1084, 1157),
        Row(3, 1, 301, 307, 380, 734, 982, 1085, 1158),
        Row(3, 6, 297, 303, 376, 733, 984, 1087, 1160),
        Row(3, 12, 291, 297, 370, 732, 986, 1090, 1163),
        Row(3, 18, 285, 291, 364, 730, 987, 1092, 1165),
        Row(3, 24, 279, 285, 358, 728, 988, 1094, 1168),
        Row(3, 30, 271, 277, 352, 726, 989, 1097, 1172),
        Row(4, 1, 269, 275, 350, 726, 989, 1098, 1173),
        Row(4, 6, 264, 270, 345, 725, 989, 1100, 1175),
        Row(4, 12, 258, 264, 339, 723, 990, 1103, 1178),
        Row(4, 18, 251, 257, 334, 721, 990, 1105, 1182),
        Row(4, 24, 245, 251, 329, 720, 990, 1108, 1185),
        Row(4, 30, 240, 246, 324, 719, 991, 1110, 1189),
        Row(5, 1, 239, 245, 324, 719, 991, 1111, 1190),
        Row(5, 6, 233, 239, 320, 719, 991, 1113, 1194),
        Row(5, 12, 229, 235, 317, 718, 992, 1116, 1198),
        Row(5, 18, 226, 232, 314, 718, 993, 1119, 1201),
        Row(5, 24, 222, 228, 312, 719, 994, 1122, 1206),
        Row(5, 30, 220, 226, 310, 719, 995, 1125, 1209),
        Row(6, 1, 219, 225, 310, 720, 995, 1126, 1211),
        Row(6, 6, 218, 224, 310, 721, 996, 1127, 1213),
        Row(6, 12, 218, 224, 310, 722, 998, 1130, 1216),
        Row(6, 18, 218, 224, 311, 723, 999, 1131, 1218),
        Row(6, 24, 219, 225, 311, 724, 1000, 1073, 1219),
        Row(6, 30, 223, 228, 314, 726, 1002, 1133, 1220),
        Row(7, 1, 222, 228, 314, 726, 1002, 1134, 1220),
        Row(7, 6, 224, 230, 316, 727, 1003, 1134, 1220),
        Row(7, 12, 228, 234, 318, 728, 1003, 1133, 1218),
        Row(7, 18, 231, 237, 321, 728, 1003, 1132, 1215),
        Row(7, 24, 235, 241, 324, 728, 1003, 1129, 1212),
        Row(7, 30, 239, 245, 326, 728, 1002, 1126, 1207),
        Row(8, 1, 240, 246, 327, 728, 1002, 1125, 1205),
        Row(8, 6, 244, 250, 329, 728, 1001, 1122, 1201),
        Row(8, 12, 247, 253, 332, 727, 999, 1118, 1196),
        Row(8, 18, 251, 257, 334, 725, 997, 1113, 1190),
        Row(8, 24, 254, 260, 336, 724, 994, 1108, 1184),
        Row(8, 30, 257, 263, 339, 722, 990, 1102, 1177),
        Row(9, 1, 258, 264, 339, 722, 989, 1100, 1175),
        Row(9, 6, 261, 267, 341, 720, 985, 1095, 1170),
        Row(9, 12, 263, 269, 343, 718, 981, 1089, 1163),
        Row(9, 18, 266, 272, 346, 716, 977, 1083, 1156),
        Row(9, 24, 268, 274, 347, 714, 972, 1077, 1150),
        Row(9, 30, 270, 276, 349, 712, 967, 1070, 1143),
        Row(10, 1, 271, 277, 349, 711, 966, 1069, 1142),
        Row(10, 6, 273, 279, 351, 710, 962, 1064, 1137),
        Row(10, 12, 275, 281, 354, 709, 957, 1059, 1132),
        Row(10, 18, 277, 283, 357, 707, 953, 1053, 1127),
        Row(10, 24, 280, 286, 360, 706, 948, 1048, 1122),
        Row(10, 30, 282, 288, 363, 705, 944, 1044, 1119),
        Row(11, 1, 283, 289, 364, 705, 943, 1043, 1118),
        Row(11, 6, 286, 292, 367, 705, 941, 1040, 1115),
        Row(11, 12, 289, 295, 371, 706, 938, 1037, 1113),
        Row(11, 18, 292, 298, 375, 707, 936, 1035, 1112),
        Row(11, 24, 296, 300, 379, 709, 935, 1034, 1111),
        Row(11, 30, 299, 305, 383, 711, 935, 1034, 1112),
        Row(12, 1, 300, 306, 384, 711, 935, 1034, 1112),
        Row(12, 6, 303, 309, 388, 713, 935, 1034, 1113),
        Row(12, 12, 307, 313, 391, 716, 937, 1036, 1115),
        Row(12, 18, 310, 316, 395, 718, 939, 1038, 1117),
        Row(12, 24, 313, 319, 398, 722, 942, 1041, 1120),
        Row(12, 30, 315, 321, 400, 724, 945, 1045, 1124)
    )

    /** Dhaka as shipped in content/districts.json (the headquarters town's Wikidata point). */
    private val dhakaPlace = Place(Latitude.of(23.7289)!!, Longitude.of(90.3944)!!, Meters.of(11.0)!!, dhaka)

    private fun minuteOfDay(instant: Instant): Int = instant.atZone(dhaka).toLocalTime().let { it.hour * 60 + it.minute }

    /** Table minus app, in minutes, per prayer, for every usable row and year. */
    private fun differences(settings: ClockSettings): Map<Prayer, List<Int>> {
        val out = Prayer.entries.associateWith { mutableListOf<Int>() }
        for (year in 2025..2028) for (r in rows) {
            if (r.month == 2 && r.day == 30) continue
            val t = computed(clock.dayTimes(LocalDate.of(year, r.month, r.day), dhakaPlace, settings))
            fun add(prayer: Prayer, table: Int) { out.getValue(prayer) += table - minuteOfDay(t[prayer]) }
            add(Prayer.SehriEnd, r.sehri)
            add(Prayer.Fajr, r.fajr)
            add(Prayer.Sunrise, r.sunrise)
            add(Prayer.Dhuhr, r.dhuhr)
            add(Prayer.Asr, r.asr)
            if (!(r.month == 6 && r.day == 24)) add(Prayer.Maghrib, r.maghrib)
            add(Prayer.Isha, r.isha)
        }
        return out
    }

    /** Positive table-minus-app is unsafe for a "may begin" time; negative is unsafe for Sehri end and sunrise. */
    private fun unsafeWhenPositive(prayer: Prayer): Boolean = when (prayer) {
        Prayer.SehriEnd, Prayer.Sunrise -> false
        Prayer.Fajr, Prayer.Dhuhr, Prayer.Asr, Prayer.Maghrib, Prayer.Isha -> true
    }

    @Test
    fun T_FIX_4_dhaka_calibrated_never_more_than_a_minute_on_the_unsafe_side() {
        val diffs = differences(ClockSettings(calibration = Calibration.islamicFoundation))
        for ((prayer, values) in diffs) {
            val unsafe = values.map { if (unsafeWhenPositive(prayer)) it else -it }
            println("$prayer table-minus-app ${values.minOrNull()}..${values.maxOrNull()} " + values.groupingBy { it }.eachCount().toSortedMap())
            assertTrue(unsafe.max() <= UNSAFE_LIMIT, "$prayer: up to ${unsafe.max()} min on the unsafe side")
            assertTrue(-unsafe.min() <= SAFE_LIMIT, "$prayer: up to ${-unsafe.min()} min on the safe side")
        }
    }

    @Test
    fun T_FIX_4_every_row_is_used() {
        val diffs = differences(ClockSettings(calibration = Calibration.islamicFoundation))
        // 72 rows minus 30 February, times four years; Maghrib also loses the 24 June misprint.
        assertTrue(diffs.getValue(Prayer.Fajr).size == 71 * 4)
        assertTrue(diffs.getValue(Prayer.Maghrib).size == 70 * 4)
    }

    /**
     * Why the calibration exists: without it Dhuhr and Maghrib come out about three minutes before the Foundation, and
     * Sehri end about five minutes after it. If this ever stops holding the calibration must be re-derived.
     */
    @Test
    fun T_FIX_4_raw_engine_shows_the_foundations_precaution_margins() {
        val diffs = differences(ClockSettings())
        fun median(prayer: Prayer) = diffs.getValue(prayer).sorted().let { it[it.size / 2] }
        assertTrue(median(Prayer.Dhuhr) == 3, "Dhuhr median ${median(Prayer.Dhuhr)}")
        assertTrue(median(Prayer.Maghrib) in 2..3, "Maghrib median ${median(Prayer.Maghrib)}")
        assertTrue(median(Prayer.SehriEnd) in -6..-4, "Sehri end median ${median(Prayer.SehriEnd)}")
    }

    private companion object {
        const val UNSAFE_LIMIT = 1
        const val SAFE_LIMIT = 4
    }
}
