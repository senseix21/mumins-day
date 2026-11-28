package com.senseix21.muminsday.zakat

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.int
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** All numbers are the fake rules' (see Support.kt): nisab 100 g of gold = 10 bhori; at 1000 a bhori that is 10,000 taka. */
class CalculatorTest {

    private val rs = RandomSource.seeded(7)
    private val rules = fakeRules()
    private val goldPrice = price("1000")

    private fun run(input: ZakatInput, with: ZakatRules = rules): ZakatOutcome = calculateZakat(with, input.copy(goldPrice = input.goldPrice ?: goldPrice))

    private fun due(outcome: ZakatOutcome): BigDecimal = assertIs<ZakatOutcome.Due>(outcome).amount

    @Test
    fun `at the nisab it is reached, a paisa below it is not`() {
        assertIs<ZakatOutcome.Due>(run(ZakatInput(cash = taka("10000"))))
        val below = run(ZakatInput(cash = taka("9999.99")))
        assertIs<ZakatOutcome.BelowNisab>(below)
        assertEquals(BigDecimal("10000"), below.working.nisabValue.stripTrailingZeros().setScale(0))
    }

    @Test
    fun `the amount is the rate of the net wealth, shown with every step`() {
        val outcome = assertIs<ZakatOutcome.Due>(run(ZakatInput(cash = taka("20000"), debts = taka("5000"), gold = Weight(bhori = amount("2")))))
        // cash 20,000 + gold 2 bhori * 1000 = 22,000; debts 5,000 come off; 17,000 at 10 percent.
        assertEquals(BigDecimal("1700.00"), outcome.amount)
        val working = outcome.working
        assertEquals(BigDecimal("17000"), working.netWealth.stripTrailingZeros().setScale(0))
        assertEquals(BigDecimal("20"), working.goldGrams.stripTrailingZeros().setScale(0))
        assertEquals(listOf(WealthKind.Cash, WealthKind.Gold, WealthKind.Silver, WealthKind.Other), working.lines.map { it.kind })
        assertTrue(working.debtsDeducted)
        assertEquals(price("1000").date, working.nisabPriceDate)
    }

    @Test
    fun `the rules decide what counts, whether debts come off and whether a year must pass`() {
        val cashOnly = fakeRules { copy(wealthCounted = listOf("cash")) }
        val outcome = assertIs<ZakatOutcome.Due>(run(ZakatInput(cash = taka("10000"), gold = Weight(bhori = amount("500"))), cashOnly))
        assertEquals(false, outcome.working.lines.first { it.kind == WealthKind.Gold }.counted)
        assertEquals(BigDecimal("1000.00"), outcome.amount)

        val keepDebts = fakeRules { copy(deductDebts = false) }
        assertEquals(BigDecimal("1000.00"), due(run(ZakatInput(cash = taka("10000"), debts = taka("9000")), keepDebts)))

        val yearNeeded = fakeRules { copy(requiresHawl = true) }
        assertIs<ZakatOutcome.HawlNotComplete>(run(ZakatInput(cash = taka("10000")), yearNeeded))
        assertIs<ZakatOutcome.Due>(run(ZakatInput(cash = taka("10000"), hawlComplete = true), yearNeeded))
    }

    @Test
    fun `a silver basis uses the silver nisab and the silver price`() {
        val silver = fakeRules { copy(nisabBasis = "silver") }
        assertEquals(ZakatOutcome.MissingPrice(Metal.Silver), calculateZakat(silver, ZakatInput(cash = taka("99999"))))
        val outcome = calculateZakat(silver, ZakatInput(cash = taka("700"), silverPrice = price("100")))
        // 700 g of silver is 70 bhori; at 100 a bhori the nisab is 7,000.
        assertIs<ZakatOutcome.BelowNisab>(outcome)
        assertEquals(BigDecimal("7000"), outcome.working.nisabValue.stripTrailingZeros().setScale(0))
    }

    @Test
    fun `no price typed means no answer, never a guess`() {
        assertEquals(ZakatOutcome.MissingPrice(Metal.Gold), calculateZakat(rules, ZakatInput(cash = taka("99999"))))
        // Held silver that counts needs its own price even when the basis price is there.
        assertEquals(
            ZakatOutcome.MissingPrice(Metal.Silver),
            calculateZakat(rules, ZakatInput(silver = Weight(grams = amount("5")), goldPrice = goldPrice)),
        )
        // No silver held: no silver price is needed.
        assertIs<ZakatOutcome.Due>(calculateZakat(rules, ZakatInput(cash = taka("10000"), goldPrice = goldPrice)))
    }

    @Test
    fun `unit round trip - the same metal in bhori, ana, ratti or grams is worth the same`() {
        val perBhori = BigDecimal("1000")
        repeat(100) {
            val bhori = Arb.int(1, 40).sample(rs).value
            val inBhori = run(ZakatInput(gold = Weight(bhori = amount("$bhori")))) 
            val inAna = run(ZakatInput(gold = Weight(ana = amount("${bhori * 20}"))))
            val inRatti = run(ZakatInput(gold = Weight(ratti = amount("${bhori * 100}"))))
            val inGrams = run(ZakatInput(gold = Weight(grams = amount("${bhori * 10}"))))
            val mixed = run(ZakatInput(gold = Weight(bhori = amount("$bhori"), ana = amount("20"), ratti = amount("100"), grams = amount("10"))))
            val worth = { outcome: ZakatOutcome -> netWealthOf(outcome) }
            val expected = perBhori * BigDecimal(bhori)
            for (outcome in listOf(inBhori, inAna, inRatti, inGrams)) {
                assertEquals(0, expected.compareTo(worth(outcome)), "$bhori bhori")
            }
            assertEquals(0, (expected + perBhori * BigDecimal(3)).compareTo(worth(mixed)))
        }
    }

    @Test
    fun `a weight in grams that is not a whole number of bhori is never counted as less than it is`() {
        // 1 gram is 0.1 bhori exactly here, but 1 gram of a three-gram bhori is a third: the division rounds up.
        val thirds = fakeRules { copy(gramsPerBhori = "3", nisabGoldGrams = "30") }
        val outcome = calculateZakat(thirds, ZakatInput(gold = Weight(grams = amount("1")), goldPrice = price("1000")))
        val net = netWealthOf(outcome)
        assertTrue(net >= BigDecimal("1000").divide(BigDecimal("3"), 40, RoundingMode.DOWN), "$net")
        assertTrue(net.subtract(BigDecimal("1000").divide(BigDecimal("3"), 40, RoundingMode.DOWN)) < BigDecimal("0.000000001"))
    }

    @Test
    fun `the amount is never below the exact share, even where it is not a whole paisa`() {
        val awkward = fakeRules { copy(ratePercent = "2.5") }
        repeat(200) {
            val cash = Arb.int(10_000, 99_999_999).sample(rs).value
            val money = BigDecimal(cash).movePointLeft(3) // three decimals: a paisa fraction is possible
            val outcome = run(ZakatInput(cash = taka(money.toPlainString())), awkward)
            if (outcome is ZakatOutcome.Due) {
                val exact = money.multiply(BigDecimal("2.5")).divide(BigDecimal(100))
                assertTrue(outcome.amount >= exact, "$money: ${outcome.amount} < $exact")
                assertTrue(outcome.amount.subtract(exact) < BigDecimal("0.01"))
            }
        }
    }

    @Test
    fun `more cash never lowers the amount and never takes the nisab away`() {
        repeat(200) {
            val a = Arb.int(0, 500_000).sample(rs).value
            val b = a + Arb.int(0, 500_000).sample(rs).value
            val low = run(ZakatInput(cash = taka("$a")))
            val high = run(ZakatInput(cash = taka("$b")))
            assertTrue(amountOf(high) >= amountOf(low), "$a -> $b")
            if (low is ZakatOutcome.Due) assertIs<ZakatOutcome.Due>(high)
        }
    }

    @Test
    fun `more debt never raises the amount`() {
        repeat(200) {
            val cash = Arb.int(0, 500_000).sample(rs).value
            val d1 = Arb.int(0, 100_000).sample(rs).value
            val d2 = d1 + Arb.int(0, 100_000).sample(rs).value
            val less = run(ZakatInput(cash = taka("$cash"), debts = taka("$d1")))
            val more = run(ZakatInput(cash = taka("$cash"), debts = taka("$d2")))
            assertTrue(amountOf(more) <= amountOf(less))
        }
    }

    @Test
    fun `a rising gold price never lowers the amount for gold held`() {
        repeat(100) {
            val p1 = Arb.int(100, 5_000).sample(rs).value
            val p2 = p1 + Arb.int(0, 5_000).sample(rs).value
            val held = Weight(bhori = amount("30"))
            val cheap = calculateZakat(rules, ZakatInput(gold = held, goldPrice = price("$p1")))
            val dear = calculateZakat(rules, ZakatInput(gold = held, goldPrice = price("$p2")))
            // The nisab moves with the price too: holding 30 bhori against a 10 bhori nisab is Due at every price.
            assertTrue(amountOf(dear) >= amountOf(cheap))
        }
    }

    @Test
    fun `negative or zero inputs cannot be made`() {
        assertEquals(null, Taka.of(BigDecimal("-0.01")))
        assertEquals(null, Quantity.of(BigDecimal("-1")))
        assertEquals(null, MetalPrice.of(Taka.ZERO, day))
        assertNotEquals(null, Taka.of(BigDecimal.ZERO))
    }

    private fun amountOf(outcome: ZakatOutcome): BigDecimal = (outcome as? ZakatOutcome.Due)?.amount ?: BigDecimal.ZERO

    private fun netWealthOf(outcome: ZakatOutcome): BigDecimal = when (outcome) {
        is ZakatOutcome.Due -> outcome.working.netWealth
        is ZakatOutcome.BelowNisab -> outcome.working.netWealth
        is ZakatOutcome.HawlNotComplete -> outcome.working.netWealth
        is ZakatOutcome.MissingPrice -> error("missing price")
    }
}
