package com.senseix21.muminsday.zakat

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** A sum of money in taka, never negative. Only [of] makes one. */
@JvmInline
public value class Taka private constructor(public val value: BigDecimal) {
    public companion object {
        public val ZERO: Taka = Taka(BigDecimal.ZERO)
        public fun of(value: BigDecimal): Taka? = if (value.signum() >= 0) Taka(value) else null
    }
}

/** A count of one weight unit the user typed (bhori, ana, ratti or grams), never negative. */
@JvmInline
public value class Quantity private constructor(public val value: BigDecimal) {
    public companion object {
        public val ZERO: Quantity = Quantity(BigDecimal.ZERO)
        public fun of(value: BigDecimal): Quantity? = if (value.signum() >= 0) Quantity(value) else null
    }
}

/** What the user has of one metal, in any mix of the four units. */
public data class Weight(
    val bhori: Quantity = Quantity.ZERO,
    val ana: Quantity = Quantity.ZERO,
    val ratti: Quantity = Quantity.ZERO,
    val grams: Quantity = Quantity.ZERO,
)

/** A price the user typed, per bhori, and the day they say it is from. The app never fetches a price. */
public data class MetalPrice(val perBhori: Taka, val date: LocalDate) {
    public companion object {
        public fun of(perBhori: Taka, date: LocalDate): MetalPrice? = if (perBhori.value.signum() > 0) MetalPrice(perBhori, date) else null
    }
}

public data class ZakatInput(
    val cash: Taka = Taka.ZERO,
    val otherAssets: Taka = Taka.ZERO,
    val debts: Taka = Taka.ZERO,
    val gold: Weight = Weight(),
    val silver: Weight = Weight(),
    val goldPrice: MetalPrice? = null,
    val silverPrice: MetalPrice? = null,
    /** The user's own statement; whether it matters is the rules' say. */
    val hawlComplete: Boolean = false,
)

/** One kind of wealth, its value in taka, and whether the signed rules count it. */
/** [value] is null for a metal that is held, not counted, and has no price typed. */
public data class WealthLine(val kind: WealthKind, val value: BigDecimal?, val counted: Boolean)

/** Every number the answer rests on, in the order a person would check it by hand (INV-Z1: no step is hidden). */
public data class Working(
    val goldGrams: BigDecimal,
    val silverGrams: BigDecimal,
    val lines: List<WealthLine>,
    val debts: BigDecimal,
    val debtsDeducted: Boolean,
    val netWealth: BigDecimal,
    val nisabBasis: Metal,
    val nisabGrams: BigDecimal,
    val nisabValue: BigDecimal,
    val nisabPriceDate: LocalDate,
    val ratePercent: BigDecimal,
    val nisabReached: Boolean,
    val requiresHawl: Boolean,
    val hawlComplete: Boolean,
)

public sealed interface ZakatOutcome {
    /** [amount] is rounded up to the paisa, never down (INV-Z1). */
    public data class Due(val amount: BigDecimal, val working: Working) : ZakatOutcome
    public data class BelowNisab(val working: Working) : ZakatOutcome
    public data class HawlNotComplete(val working: Working) : ZakatOutcome

    /** A price the answer needs was not typed. No number is guessed (INV-T7 in spirit). */
    public data class MissingPrice(val metal: Metal) : ZakatOutcome
}

/** Digits kept in intermediate divisions. Not a rule: far more than a taka or a gram can need. */
private const val INTERMEDIATE_SCALE: Int = 30

/** The paisa: two decimal places. Money is shown to this, rounded the way the caller says. */
internal const val PAISA_SCALE: Int = 2

private fun divide(numerator: BigDecimal, denominator: BigDecimal, mode: RoundingMode): BigDecimal =
    numerator.divide(denominator, INTERMEDIATE_SCALE, mode)

/** Bhori that the typed weight amounts to. Divisions round up, so a weight is never counted as less than it is. */
private fun bhoriOf(weight: Weight, conversions: Conversions): BigDecimal {
    val rattiPerBhori = conversions.anaPerBhori.multiply(conversions.rattiPerAna)
    val rattiTotal = weight.bhori.value.multiply(rattiPerBhori) + weight.ana.value.multiply(conversions.rattiPerAna) + weight.ratti.value
    return divide(rattiTotal, rattiPerBhori, RoundingMode.CEILING) + divide(weight.grams.value, conversions.gramsPerBhori, RoundingMode.CEILING)
}

private fun gramsOf(bhori: BigDecimal, conversions: Conversions): BigDecimal = bhori.multiply(conversions.gramsPerBhori)

/**
 * The calculation, as the signed [rules] say, on what the user typed. Pure: no clock, no locale, no network.
 *
 * Safe side (INV-Z1: never round against the payer's obligation): every division that raises what is owed rounds up and
 * the one that raises the bar (the nisab's value) rounds down, so a tie goes to "reached"; the amount is rounded up to
 * the paisa. Reaching the nisab means at least it (ASSUMED reading, reviewer to confirm in the rules' notes).
 */
public fun calculateZakat(rules: ZakatRules, input: ZakatInput): ZakatOutcome {
    val conversions = rules.conversions
    val basisPrice = when (rules.nisabBasis) {
        Metal.Gold -> input.goldPrice
        Metal.Silver -> input.silverPrice
    } ?: return ZakatOutcome.MissingPrice(rules.nisabBasis)

    val goldBhori = bhoriOf(input.gold, conversions)
    val silverBhori = bhoriOf(input.silver, conversions)

    /** The metal's value (nothing, if none is held), or null when it is held and no price was typed. Never a guessed price. */
    fun valueOf(bhori: BigDecimal, price: MetalPrice?): BigDecimal? =
        if (bhori.signum() == 0) BigDecimal.ZERO else price?.let { bhori.multiply(it.perBhori.value) }

    val goldValue = valueOf(goldBhori, input.goldPrice)
    val silverValue = valueOf(silverBhori, input.silverPrice)
    val lines = listOf(
        WealthLine(WealthKind.Cash, input.cash.value, WealthKind.Cash in rules.wealthCounted),
        WealthLine(WealthKind.Gold, goldValue, WealthKind.Gold in rules.wealthCounted),
        WealthLine(WealthKind.Silver, silverValue, WealthKind.Silver in rules.wealthCounted),
        WealthLine(WealthKind.Other, input.otherAssets.value, WealthKind.Other in rules.wealthCounted),
    )
    // A metal that counts and is held needs its price.
    lines.firstOrNull { it.counted && it.value == null }?.let { missing ->
        return ZakatOutcome.MissingPrice(if (missing.kind == WealthKind.Gold) Metal.Gold else Metal.Silver)
    }
    val counted = lines.filter { it.counted }.fold(BigDecimal.ZERO) { sum, line -> sum + (line.value ?: BigDecimal.ZERO) }
    val netWealth = if (rules.deductDebts) counted - input.debts.value else counted

    val nisabGrams = rules.nisabGrams(rules.nisabBasis)
    val nisabBhori = divide(nisabGrams, conversions.gramsPerBhori, RoundingMode.FLOOR)
    val nisabValue = nisabBhori.multiply(basisPrice.perBhori.value)
    val reached = netWealth >= nisabValue

    val working = Working(
        goldGrams = gramsOf(goldBhori, conversions),
        silverGrams = gramsOf(silverBhori, conversions),
        lines = lines,
        debts = input.debts.value,
        debtsDeducted = rules.deductDebts,
        netWealth = netWealth,
        nisabBasis = rules.nisabBasis,
        nisabGrams = nisabGrams,
        nisabValue = nisabValue,
        nisabPriceDate = basisPrice.date,
        ratePercent = rules.ratePercent,
        nisabReached = reached,
        requiresHawl = rules.requiresHawl,
        hawlComplete = input.hawlComplete,
    )
    return when {
        !reached -> ZakatOutcome.BelowNisab(working)
        rules.requiresHawl && !input.hawlComplete -> ZakatOutcome.HawlNotComplete(working)
        else -> {
            val owed = netWealth.multiply(rules.ratePercent).divide(BigDecimal(PERCENT_SCALE))
            ZakatOutcome.Due(owed.setScale(PAISA_SCALE, RoundingMode.CEILING), working)
        }
    }
}
