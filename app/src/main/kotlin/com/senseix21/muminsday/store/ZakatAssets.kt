package com.senseix21.muminsday.store

import android.content.Context
import com.senseix21.muminsday.zakat.RawZakatRules
import com.senseix21.muminsday.zakat.ZakatProblem
import com.senseix21.muminsday.zakat.ZakatRules
import com.senseix21.muminsday.zakat.ZakatRulesCheck
import com.senseix21.muminsday.zakat.checkZakatRules
import java.io.FileNotFoundException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** What reading the bundled rules gave. Without [Loaded] the screen calculates nothing (INV-Z1). */
sealed interface ZakatRulesLoad {
    data class Loaded(val rules: ZakatRules) : ZakatRulesLoad

    /** The file was read but core's check refused it. T-BUILD-4 normally stops this shipping. */
    data class Rejected(val problems: List<ZakatProblem>) : ZakatRulesLoad

    /** No `zakat-rules.json` asset (a release has none until the reviewer signs the rules), or it is not JSON. */
    data object Unavailable : ZakatRulesLoad
}

/**
 * Parse at the edge: the platform's `org.json` reads `assets/zakat-rules.json` into [RawZakatRules] and core's
 * [checkZakatRules] judges it, which is the gate the build ran on the same file. A value of the wrong type is read as
 * missing, so the check refuses it.
 */
fun loadZakatRules(context: Context): ZakatRulesLoad {
    val text = try {
        context.assets.open(ZAKAT_RULES_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (_: FileNotFoundException) {
        return ZakatRulesLoad.Unavailable
    }
    val root = try {
        JSONObject(text)
    } catch (_: JSONException) {
        return ZakatRulesLoad.Unavailable
    }
    fun string(key: String): String? = root.opt(key).let { if (it is String) it else if (it is Int && key == "version") it.toString() else null }
    fun flag(key: String): Boolean? = root.opt(key) as? Boolean
    val wealth = (root.opt("wealth_counted") as? JSONArray)?.let { array ->
        (0 until array.length()).map { array.opt(it) as? String ?: "" }
    }
    val raw = RawZakatRules(
        version = string("version"),
        reviewer = string("reviewer"),
        reviewDate = string("review_date"),
        status = string("status"),
        testFixture = flag("test_fixture"),
        nisabBasis = string("nisab_basis"),
        nisabGoldGrams = string("nisab_gold_grams"),
        nisabSilverGrams = string("nisab_silver_grams"),
        ratePercent = string("rate_percent"),
        gramsPerBhori = string("grams_per_bhori"),
        anaPerBhori = string("ana_per_bhori"),
        rattiPerAna = string("ratti_per_ana"),
        conversionSource = string("conversion_source"),
        wealthCounted = wealth,
        deductDebts = flag("deduct_debts"),
        requiresHawl = flag("requires_hawl"),
        notes = string("notes"),
    )
    return when (val check = checkZakatRules(raw)) {
        is ZakatRulesCheck.Accepted -> ZakatRulesLoad.Loaded(check.rules)
        is ZakatRulesCheck.Rejected -> ZakatRulesLoad.Rejected(check.problems)
    }
}

private const val ZAKAT_RULES_ASSET = "zakat-rules.json"
