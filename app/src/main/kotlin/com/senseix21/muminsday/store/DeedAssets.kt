package com.senseix21.muminsday.store

import android.content.Context
import com.senseix21.muminsday.deed.ContentCheck
import com.senseix21.muminsday.deed.DeedContent
import com.senseix21.muminsday.deed.Problem
import com.senseix21.muminsday.deed.RawDeed
import com.senseix21.muminsday.deed.checkContent
import java.io.FileNotFoundException
import org.json.JSONException
import org.json.JSONObject

/**
 * What reading the bundled deed file gave. There is no fallback card in any case: without [Loaded] the app shows no
 * deed at all (INV-C1).
 */
sealed interface DeedsLoad {
    /** Parsed, and the core review gate accepted every record. This is the only way to hold a [DeedContent]. */
    data class Loaded(val content: DeedContent) : DeedsLoad

    /** The file was read but core's gate refused it. T-BUILD-4 normally stops this shipping. */
    data class Rejected(val problems: List<Problem>) : DeedsLoad

    /** No `deeds.json` asset (release builds have none until the reviewer delivers it, G9), or it is not the expected shape. */
    data object Unavailable : DeedsLoad
}

/**
 * Parse at the edge: the platform's `org.json` (part of android.jar, not a dependency) reads `assets/deeds.json` into
 * [RawDeed]s, and core's [checkContent] judges them, which is the gate the build ran on the same file (ADR-10). Nothing
 * downstream checks again. A value of the wrong type is read as missing, so the gate rejects it.
 */
fun loadDeeds(context: Context): DeedsLoad {
    val text = try {
        context.assets.open(DEEDS_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (_: FileNotFoundException) {
        return DeedsLoad.Unavailable
    }
    val root = try {
        JSONObject(text)
    } catch (_: JSONException) {
        return DeedsLoad.Unavailable
    }
    val version = root.opt("version") as? Int ?: return DeedsLoad.Unavailable
    val list = root.optJSONArray("deeds") ?: return DeedsLoad.Unavailable

    val records = (0 until list.length()).map { index ->
        val entry = list.optJSONObject(index)
        // added_in and retired_in are version numbers: the file may write them as JSON integers.
        fun field(key: String, numeric: Boolean = false): String? = when (val value = entry?.opt(key)) {
            is String -> value
            is Int -> if (numeric) value.toString() else null
            else -> null
        }
        RawDeed(
            id = field("id"),
            textBn = field("text_bn"),
            textEn = field("text_en"),
            sourceBook = field("source_book"),
            sourceNumber = field("source_number"),
            sourceEdition = field("source_edition"),
            grading = field("grading"),
            reviewer = field("reviewer"),
            reviewDate = field("review_date"),
            status = field("status"),
            addedIn = field("added_in", numeric = true),
            retiredIn = field("retired_in", numeric = true),
        )
    }
    return when (val check = checkContent(version, records)) {
        is ContentCheck.Accepted -> DeedsLoad.Loaded(check.content)
        is ContentCheck.Rejected -> DeedsLoad.Rejected(check.problems)
    }
}

private const val DEEDS_ASSET = "deeds.json"
