package com.senseix21.muminsday.store

import android.content.Context
import com.senseix21.muminsday.BuildConfig
import com.senseix21.muminsday.content.ContentKind
import com.senseix21.muminsday.content.KindCheck
import com.senseix21.muminsday.content.KindContent
import com.senseix21.muminsday.content.KindField
import com.senseix21.muminsday.content.KindProblem
import com.senseix21.muminsday.content.NUMERIC_FIELDS
import com.senseix21.muminsday.content.RawKindFile
import com.senseix21.muminsday.content.checkKind
import com.senseix21.muminsday.content.fields
import com.senseix21.muminsday.content.key
import java.io.FileNotFoundException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** What reading one kind's bundled file gave. Without [Loaded] the screen shows no record of that kind (INV-C1). */
sealed interface KindLoad {
    data class Loaded(val content: KindContent) : KindLoad

    /** The file was read but core's gate refused it. T-BUILD-4 normally stops this shipping. */
    data class Rejected(val problems: List<KindProblem>) : KindLoad

    /** No asset (a release has none until the reviewer delivers the file), or it is not JSON. */
    data object Unavailable : KindLoad
}

/**
 * Parse at the edge: `org.json` reads `assets/<kind's file>` into a [RawKindFile] with the key table core holds, and
 * [checkKind] judges it, the gate the build ran on the same file. A test fixture is allowed in a debug build only: the
 * release build packages `content/` alone, whose gate already refuses a fixture, and refuses one here too. A value of the
 * wrong type is read as missing, so the gate rejects it.
 */
fun loadKind(context: Context, kind: ContentKind): KindLoad {
    val text = try {
        context.assets.open(kind.fileName).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (_: FileNotFoundException) {
        return KindLoad.Unavailable
    }
    val root = try {
        JSONObject(text)
    } catch (_: JSONException) {
        return KindLoad.Unavailable
    }
    val list = root.optJSONArray(kind.listKey) ?: return KindLoad.Unavailable
    val sources = (root.opt("sources") as? JSONArray)?.let { array -> (0 until array.length()).map { array.opt(it) as? String } }
    val records = (0 until list.length()).map { index ->
        val entry = list.optJSONObject(index)
        buildMap<KindField, String> {
            for (field in kind.fields()) {
                val key = kind.key(field) ?: continue
                when (val value = entry?.opt(key)) {
                    is String -> put(field, value)
                    is Int -> if (field in NUMERIC_FIELDS) put(field, value.toString())
                    else -> Unit
                }
            }
        }
    }
    val file = RawKindFile(root.opt("version") as? Int, root.opt("test_fixture") as? Boolean, sources, records)
    return when (val check = checkKind(kind, file, allowTestFixture = BuildConfig.DEBUG)) {
        is KindCheck.Accepted -> KindLoad.Loaded(check.content)
        is KindCheck.Rejected -> KindLoad.Rejected(check.problems)
    }
}
