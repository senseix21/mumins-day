package com.senseix21.muminsday.zakat

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

/*
 * INV-Z1: this package holds no rule. The nisab weights, the rate, the bhori, ana and ratti conversions, what counts as
 * wealth, whether debts come off and whether a year must pass all come from `content/zakat-rules.json`, which the
 * reviewer signs. Everything here is the shape of that file and arithmetic on it. A test reads this package's source and
 * fails if a number appears that is not one of the few scale constants it names.
 */

public enum class Metal { Gold, Silver }

/** What a person can hold. Which of these the rules count is the rules' say, not this package's. */
public enum class WealthKind(public val key: String) {
    Cash("cash"), Gold("gold"), Silver("silver"), Other("other"),
}

/** The weight units the user types in, as the rules relate them. */
public data class Conversions(val gramsPerBhori: BigDecimal, val anaPerBhori: BigDecimal, val rattiPerAna: BigDecimal)

/** Rules that passed the check: every field present, valid and signed. Only [checkZakatRules] makes one. */
@ConsistentCopyVisibility
public data class ZakatRules internal constructor(
    val reviewer: String,
    val reviewDate: LocalDate,
    /** True for a development file with fake numbers. The store build refuses it (INV-Z1); the screen says so. */
    val isTestFixture: Boolean,
    val nisabBasis: Metal,
    val nisabGoldGrams: BigDecimal,
    val nisabSilverGrams: BigDecimal,
    val ratePercent: BigDecimal,
    val conversions: Conversions,
    val conversionSource: String,
    val wealthCounted: Set<WealthKind>,
    val deductDebts: Boolean,
    val requiresHawl: Boolean,
    val notes: String,
) {
    public fun nisabGrams(metal: Metal): BigDecimal = when (metal) {
        Metal.Gold -> nisabGoldGrams
        Metal.Silver -> nisabSilverGrams
    }
}

public enum class ZakatField {
    Version, Reviewer, ReviewDate, Status, TestFixture, NisabBasis, NisabGoldGrams, NisabSilverGrams, RatePercent,
    GramsPerBhori, AnaPerBhori, RattiPerAna, ConversionSource, WealthCounted, DeductDebts, RequiresHawl, Notes,
}

public enum class ZakatProblemKind {
    /** Absent or blank. */
    Missing,

    /** Present but not a legal value: not a plain positive number, unknown name, bad date, a repeated entry. */
    Invalid,

    /** Status `draft`: the reviewer has not signed it (INV-Z1). */
    NotApproved,
}

public data class ZakatProblem(val field: ZakatField, val kind: ZakatProblemKind)

/** The file as read at the edge: text where the file has text, a flag where it has a flag. Nothing is judged yet. */
public data class RawZakatRules(
    val version: String?,
    val reviewer: String?,
    val reviewDate: String?,
    val status: String?,
    val testFixture: Boolean?,
    val nisabBasis: String?,
    val nisabGoldGrams: String?,
    val nisabSilverGrams: String?,
    val ratePercent: String?,
    val gramsPerBhori: String?,
    val anaPerBhori: String?,
    val rattiPerAna: String?,
    val conversionSource: String?,
    val wealthCounted: List<String>?,
    val deductDebts: Boolean?,
    val requiresHawl: Boolean?,
    val notes: String?,
)

public sealed interface ZakatRulesCheck {
    public data class Accepted(val rules: ZakatRules) : ZakatRulesCheck
    public data class Rejected(val problems: List<ZakatProblem>) : ZakatRulesCheck
}

private val PLAIN_NUMBER = Regex("[0-9]+(\\.[0-9]+)?")

/**
 * T-BUILD-4 for the rules file, and the app's own check on the same file. Collects every problem. A rate is a percent
 * from more than 0 up to 100; every other number is more than 0; numbers are text so no float rounds them.
 */
public fun checkZakatRules(raw: RawZakatRules): ZakatRulesCheck {
    val problems = mutableListOf<ZakatProblem>()
    fun report(field: ZakatField, kind: ZakatProblemKind) {
        problems += ZakatProblem(field, kind)
    }

    fun text(field: ZakatField, value: String?): String? =
        value?.takeUnless { it.isBlank() } ?: null.also { report(field, ZakatProblemKind.Missing) }

    fun positive(field: ZakatField, value: String?): BigDecimal? {
        val given = text(field, value) ?: return null
        val number = if (PLAIN_NUMBER.matches(given)) BigDecimal(given) else null
        if (number == null || number.signum() <= 0) {
            report(field, ZakatProblemKind.Invalid)
            return null
        }
        return number
    }

    fun flag(field: ZakatField, value: Boolean?): Boolean? = value ?: null.also { report(field, ZakatProblemKind.Missing) }

    val version = text(ZakatField.Version, raw.version)?.toIntOrNull()
    if (raw.version != null && raw.version.isNotBlank() && (version == null || version < 1)) report(ZakatField.Version, ZakatProblemKind.Invalid)
    val reviewer = text(ZakatField.Reviewer, raw.reviewer)
    val reviewDate = text(ZakatField.ReviewDate, raw.reviewDate)?.let { given ->
        try {
            LocalDate.parse(given)
        } catch (_: DateTimeParseException) {
            null.also { report(ZakatField.ReviewDate, ZakatProblemKind.Invalid) }
        }
    }
    val signed = when (text(ZakatField.Status, raw.status)) {
        null -> null
        "approved" -> true
        "draft" -> null.also { report(ZakatField.Status, ZakatProblemKind.NotApproved) }
        else -> null.also { report(ZakatField.Status, ZakatProblemKind.Invalid) }
    }
    val fixture = flag(ZakatField.TestFixture, raw.testFixture)
    val basis = text(ZakatField.NisabBasis, raw.nisabBasis)?.let { given ->
        Metal.entries.firstOrNull { it.name.lowercase() == given } ?: null.also { report(ZakatField.NisabBasis, ZakatProblemKind.Invalid) }
    }
    val goldGrams = positive(ZakatField.NisabGoldGrams, raw.nisabGoldGrams)
    val silverGrams = positive(ZakatField.NisabSilverGrams, raw.nisabSilverGrams)
    val ratePercent = positive(ZakatField.RatePercent, raw.ratePercent)
    if (ratePercent != null && ratePercent > BigDecimal(PERCENT_SCALE)) report(ZakatField.RatePercent, ZakatProblemKind.Invalid)
    val gramsPerBhori = positive(ZakatField.GramsPerBhori, raw.gramsPerBhori)
    val anaPerBhori = positive(ZakatField.AnaPerBhori, raw.anaPerBhori)
    val rattiPerAna = positive(ZakatField.RattiPerAna, raw.rattiPerAna)
    val source = text(ZakatField.ConversionSource, raw.conversionSource)
    val counted: Set<WealthKind>? = when {
        raw.wealthCounted == null -> null.also { report(ZakatField.WealthCounted, ZakatProblemKind.Missing) }
        else -> {
            val kinds = raw.wealthCounted.map { given -> WealthKind.entries.firstOrNull { it.key == given } }
            if (kinds.isEmpty()) report(ZakatField.WealthCounted, ZakatProblemKind.Missing)
            if (kinds.any { it == null } || kinds.distinct().size != kinds.size) report(ZakatField.WealthCounted, ZakatProblemKind.Invalid)
            kinds.filterNotNull().toSet()
        }
    }
    val deduct = flag(ZakatField.DeductDebts, raw.deductDebts)
    val hawl = flag(ZakatField.RequiresHawl, raw.requiresHawl)
    val notes = raw.notes.orEmpty()

    if (problems.isNotEmpty() || signed == null || reviewer == null || reviewDate == null || fixture == null || basis == null ||
        goldGrams == null || silverGrams == null || ratePercent == null || gramsPerBhori == null || anaPerBhori == null ||
        rattiPerAna == null || source == null || counted == null || deduct == null || hawl == null
    ) {
        return ZakatRulesCheck.Rejected(problems)
    }
    return ZakatRulesCheck.Accepted(
        ZakatRules(
            reviewer, reviewDate, fixture, basis, goldGrams, silverGrams, ratePercent,
            Conversions(gramsPerBhori, anaPerBhori, rattiPerAna), source, counted, deduct, hawl, notes,
        ),
    )
}

/** A percent is out of this many. A unit of the notation, not a rule. */
internal const val PERCENT_SCALE: Int = 100
