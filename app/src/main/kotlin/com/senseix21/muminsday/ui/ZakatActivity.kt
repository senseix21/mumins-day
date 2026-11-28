package com.senseix21.muminsday.ui

import android.app.DatePickerDialog
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import com.senseix21.muminsday.R
import com.senseix21.muminsday.l10n.parseTypedNumber
import com.senseix21.muminsday.store.Stores
import com.senseix21.muminsday.store.ZakatRulesLoad
import com.senseix21.muminsday.store.loadZakatRules
import com.senseix21.muminsday.zakat.Metal
import com.senseix21.muminsday.zakat.MetalPrice
import com.senseix21.muminsday.zakat.Quantity
import com.senseix21.muminsday.zakat.Taka
import com.senseix21.muminsday.zakat.Weight
import com.senseix21.muminsday.zakat.WealthKind
import com.senseix21.muminsday.zakat.ZakatInput
import com.senseix21.muminsday.zakat.ZakatOutcome
import com.senseix21.muminsday.zakat.ZakatRules
import com.senseix21.muminsday.zakat.Working
import com.senseix21.muminsday.zakat.calculateZakat
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

/**
 * Zakat calculator (INV-Z1). Every rule comes from the reviewer-signed `zakat-rules.json`; this screen only collects what
 * the user types, hands it to `zakat/` in core and prints the working it gets back. Nothing typed is saved or sent: the
 * screen keeps no state outside its own fields, so there is nothing to back up, and the prices are the user's own.
 */
class ZakatActivity : BaseActivity() {

    private lateinit var texts: Texts
    private val fields = mutableMapOf<String, EditText>()
    private val priceDates = mutableMapOf<Metal, LocalDate>()
    private var hawlBox: CheckBox? = null
    private var results = column()

    override fun onResume() {
        super.onResume()
        texts = Texts(this, language)
        render()
    }

    private fun render() {
        val page = column()
        page.addView(title(getString(R.string.zakat_title)))
        when (val load = loadZakatRules(this)) {
            ZakatRulesLoad.Unavailable -> page.addView(body(getString(R.string.zakat_no_rules)))
            is ZakatRulesLoad.Rejected -> page.addView(body(getString(R.string.zakat_rules_rejected)))
            is ZakatRulesLoad.Loaded -> form(page, load.rules)
        }
        show(page)
    }

    private fun form(page: android.widget.LinearLayout, rules: ZakatRules) {
        if (rules.isTestFixture) page.addView(panel().apply { addView(body(getString(R.string.zakat_fixture_banner))) })
        page.addView(body(getString(R.string.zakat_help), muted = true))
        val today = Instant.now().atZone(Stores.settings(this).read().place.zone).toLocalDate()

        fun counted(kind: WealthKind) = kind in rules.wealthCounted
        if (counted(WealthKind.Cash)) page.addView(field("cash", getString(R.string.zakat_cash)))
        if (counted(WealthKind.Other)) page.addView(field("other", getString(R.string.zakat_other)))
        if (rules.deductDebts) page.addView(field("debts", getString(R.string.zakat_debts)))

        val goldNeeded = counted(WealthKind.Gold) || rules.nisabBasis == Metal.Gold
        val silverNeeded = counted(WealthKind.Silver) || rules.nisabBasis == Metal.Silver
        if (counted(WealthKind.Gold)) weightFields(page, "gold", getString(R.string.zakat_gold))
        if (counted(WealthKind.Silver)) weightFields(page, "silver", getString(R.string.zakat_silver))
        if (goldNeeded || silverNeeded) page.addView(body(getString(R.string.zakat_price_note), muted = true))
        if (goldNeeded) priceFields(page, Metal.Gold, getString(R.string.zakat_price_gold), today)
        if (silverNeeded) priceFields(page, Metal.Silver, getString(R.string.zakat_price_silver), today)

        if (rules.requiresHawl) {
            hawlBox = CheckBox(this).apply {
                text = getString(R.string.zakat_hawl)
                textSize = 18f
                minHeight = dp(MIN_TARGET_DP)
            }
            page.addView(hawlBox)
        }
        page.addView(actionButton(getString(R.string.zakat_calculate)) { calculate(rules, today) })
        results = column()
        page.addView(results)
    }

    private fun field(key: String, label: String): android.view.View = column().apply {
        addView(body(label))
        addView(
            EditText(this@ZakatActivity).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                textSize = 20f
                minHeight = dp(MIN_TARGET_DP)
                contentDescription = label
                fields[key] = this
            },
        )
    }

    private fun weightFields(page: android.widget.LinearLayout, prefix: String, heading: String) {
        page.addView(heading(heading))
        page.addView(field("$prefix.bhori", getString(R.string.zakat_unit_bhori)))
        page.addView(field("$prefix.ana", getString(R.string.zakat_unit_ana)))
        page.addView(field("$prefix.ratti", getString(R.string.zakat_unit_ratti)))
        page.addView(field("$prefix.grams", getString(R.string.zakat_unit_grams)))
    }

    private fun priceFields(page: android.widget.LinearLayout, metal: Metal, label: String, today: LocalDate) {
        page.addView(field("price.${metal.name}", label))
        priceDates.putIfAbsent(metal, today)
        val dateButton = actionButton("", primary = false) {}
        fun refreshLabel() { dateButton.text = getString(R.string.zakat_price_date, texts.localDate(priceDates.getValue(metal))) }
        refreshLabel()
        dateButton.setOnClickListener {
            val current = priceDates.getValue(metal)
            DatePickerDialog(this, { _, year, month, day ->
                priceDates[metal] = LocalDate.of(year, month + 1, day)
                refreshLabel()
            }, current.year, current.monthValue - 1, current.dayOfMonth).show()
        }
        page.addView(dateButton)
    }

    /** A blank field is zero; a field with anything else that is not a number is an error that names the field. */
    private fun read(key: String, label: String, problems: MutableList<String>): BigDecimal? {
        val text = fields[key]?.text?.toString().orEmpty()
        if (text.isBlank()) return BigDecimal.ZERO
        return parseTypedNumber(text) ?: null.also { problems += getString(R.string.zakat_bad_number, label) }
    }

    private fun calculate(rules: ZakatRules, today: LocalDate) {
        val problems = mutableListOf<String>()
        fun taka(key: String, label: Int): Taka = Taka.of(read(key, getString(label), problems) ?: BigDecimal.ZERO) ?: Taka.ZERO
        fun quantity(key: String, label: Int): Quantity = Quantity.of(read(key, getString(label), problems) ?: BigDecimal.ZERO) ?: Quantity.ZERO
        fun weight(prefix: String) = Weight(
            bhori = quantity("$prefix.bhori", R.string.zakat_unit_bhori),
            ana = quantity("$prefix.ana", R.string.zakat_unit_ana),
            ratti = quantity("$prefix.ratti", R.string.zakat_unit_ratti),
            grams = quantity("$prefix.grams", R.string.zakat_unit_grams),
        )
        fun price(metal: Metal, label: Int): MetalPrice? {
            val field = fields["price.${metal.name}"] ?: return null
            if (field.text.isNullOrBlank()) return null
            val typed = read("price.${metal.name}", getString(label), problems) ?: return null
            return Taka.of(typed)?.let { MetalPrice.of(it, priceDates[metal] ?: today) }
        }
        val input = ZakatInput(
            cash = taka("cash", R.string.zakat_cash),
            otherAssets = taka("other", R.string.zakat_other),
            debts = taka("debts", R.string.zakat_debts),
            gold = weight("gold"),
            silver = weight("silver"),
            goldPrice = price(Metal.Gold, R.string.zakat_price_gold),
            silverPrice = price(Metal.Silver, R.string.zakat_price_silver),
            hawlComplete = hawlBox?.isChecked == true,
        )
        results.removeAllViews()
        if (problems.isNotEmpty()) {
            problems.forEach { results.addView(body(it)) }
            return
        }
        when (val outcome = calculateZakat(rules, input)) {
            is ZakatOutcome.MissingPrice -> results.addView(body(getString(R.string.zakat_missing_price, texts.metal(outcome.metal))))
            is ZakatOutcome.BelowNisab -> working(outcome.working, getString(R.string.zakat_below_nisab))
            is ZakatOutcome.HawlNotComplete -> working(outcome.working, getString(R.string.zakat_hawl_pending))
            is ZakatOutcome.Due -> working(outcome.working, getString(R.string.zakat_amount, texts.decimal(outcome.amount, TAKA_PLACES, RoundingMode.CEILING)))
        }
    }

    /** Every step, in the order a person would check it by hand, then the outcome. Rounding directions are said on screen. */
    private fun working(working: Working, outcome: String) {
        results.addView(heading(getString(R.string.zakat_working_title)))
        if (working.goldGrams.signum() > 0) results.addView(body(getString(R.string.zakat_step_weight, texts.metal(Metal.Gold), texts.decimal(working.goldGrams, WEIGHT_PLACES, RoundingMode.CEILING))))
        if (working.silverGrams.signum() > 0) results.addView(body(getString(R.string.zakat_step_weight, texts.metal(Metal.Silver), texts.decimal(working.silverGrams, WEIGHT_PLACES, RoundingMode.CEILING))))
        for (line in working.lines) {
            val value = line.value ?: continue
            val text = getString(R.string.zakat_step_line, texts.wealthKind(line.kind), texts.decimal(value, TAKA_PLACES, RoundingMode.CEILING))
            results.addView(body(if (line.counted) text else text + " " + getString(R.string.zakat_step_not_counted)))
        }
        if (working.debtsDeducted) results.addView(body(getString(R.string.zakat_step_debts, texts.decimal(working.debts, TAKA_PLACES, RoundingMode.CEILING))))
        results.addView(body(getString(R.string.zakat_step_net, texts.decimal(working.netWealth, TAKA_PLACES, RoundingMode.CEILING))))
        results.addView(
            body(
                getString(
                    R.string.zakat_step_nisab,
                    texts.decimal(working.nisabGrams, WEIGHT_PLACES, RoundingMode.CEILING),
                    texts.metal(working.nisabBasis),
                    texts.decimal(working.nisabValue, TAKA_PLACES, RoundingMode.FLOOR),
                    texts.localDate(working.nisabPriceDate),
                ),
            ),
        )
        results.addView(body(getString(if (working.nisabReached) R.string.zakat_reached_yes else R.string.zakat_reached_no)))
        results.addView(body(getString(R.string.zakat_step_rate, texts.decimal(working.ratePercent, WEIGHT_PLACES, RoundingMode.CEILING))))
        results.addView(heading(outcome))
        results.addView(body(getString(R.string.zakat_rounding_note), muted = true))
    }

    private companion object {
        /** Display precision only: money to the paisa, weights to a ten-thousandth of a gram. The calculation keeps more. */
        const val TAKA_PLACES = 2
        const val WEIGHT_PLACES = 4
    }
}
