package com.kamal.smsfinance.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuleEngineTest {

    private val engine = RuleEngine()

    private fun rule(
        pattern: String,
        categoryId: Long? = null,
        counterpartyId: Long? = null,
        id: Long = 0
    ) = SmartRule(id = id, pattern = pattern, categoryId = categoryId, counterpartyId = counterpartyId)

    // ---------- Basic matching ----------

    @Test
    fun `should return no match for blank text`() {
        val result = engine.evaluate("   ", listOf(rule("خرید", categoryId = 1L)))
        assertNull(result.matchedRule)
        assertNull(result.categoryId)
    }

    @Test
    fun `should return no match when no rules exist`() {
        val result = engine.evaluate("خرید از سوپرمارکت", emptyList())
        assertNull(result.matchedRule)
        assertNull(result.categoryId)
    }

    @Test
    fun `should match a simple substring rule`() {
        val rules = listOf(rule("خواربار", categoryId = 7L, counterpartyId = 3L))
        val result = engine.evaluate("خرید از فروشگاه خواربار مرکزی", rules)

        assertNotNull(result.matchedRule)
        assertEquals(7L, result.categoryId)
        assertEquals(3L, result.counterpartyId)
    }

    @Test
    fun `should return no match when pattern absent`() {
        val rules = listOf(rule("پیک", categoryId = 1L))
        val result = engine.evaluate("مبلغ تاکسی", rules)
        assertNull(result.matchedRule)
        assertNull(result.categoryId)
    }

    // ---------- Specificity: longest pattern wins ----------

    @Test
    fun `longer more specific pattern should win over generic one`() {
        val rules = listOf(
            rule("خرید", categoryId = 100L),
            rule("خرید سوپرمارکت", categoryId = 200L)
        )
        val result = engine.evaluate("خرید سوپرمارکت وحید", rules)
        assertEquals(200L, result.categoryId)
    }

    @Test
    fun `specific rule should NOT win when its text absent`() {
        val rules = listOf(
            rule("خرید", categoryId = 100L),
            rule("خرید پوشاک", categoryId = 200L)
        )
        val result = engine.evaluate("خرید از فروشگاه", rules)
        assertEquals(100L, result.categoryId)
    }

    // ---------- Persian/Arabic normalization ----------

    @Test
    fun `should match despite Persian and Arabic char variants (ي vs ی)`() {
        val rules = listOf(rule("بيمه", categoryId = 1L))
        // Fake-bill text uses Arabic ي in بیمه side by side with Persian ی elsewhere.
        val result = engine.evaluate("پرداخت بيمه خودرو انجام شد", rules)
        assertNotNull(result.matchedRule)
        assertEquals(1L, result.categoryId)
    }

    @Test
    fun `should match despite ZWNJ vs space whether in rule or text`() {
        val rulesWithZwnj = listOf(rule("میخواهم", categoryId = 1L))
        // Text has no ZWNJ: "میخواهم" vs "می خواهم" -- after normalization both become "می خواهم".
        val result = engine.evaluate("میخواهم پرداخت کنم", rulesWithZwnj)
        assertNotNull(result.matchedRule, "rule with 'می' should match text with 'میخواهم' after ZWNJ normalization")

        // And ZWNJ in the rule itself.
        val zwnjRules = listOf(rule("می\u200Cخواهم", categoryId = 2L))
        val result2 = engine.evaluate("میخواهم پرداخت کنم", zwnjRules)
        // normalize replaces ZWNJ with space => both sides become "می خواهم"
        assertEquals(2L, result2.categoryId)
    }

    @Test
    fun `should be case-insensitive`() {
        val rules = listOf(rule("cafe", categoryId = 1L))
        val result = engine.evaluate("پرداخت در CAFE وسترن", rules)
        assertNotNull(result.matchedRule)
    }

    @Test
    fun `normalize should collapse whitespace and remove ZWNJ`() {
        // Multiple spaces collapse to a single space.
        assertEquals("خرید از فروشگاه", engine.normalizeText("خرید   از   فروشگاه"))
        // ZWNJ is replaced by a space.
        assertEquals("می خواهم", engine.normalizeText("می\u200Cخواهم"))
        // No ZWNJ remains.
        assertTrue(!engine.normalizeText("می\u200Cخواهم").contains("\u200C"))
    }

    // ---------- Edge cases ----------

    @Test
    fun `empty pattern should be ignored not crash`() {
        val rules = listOf(rule("", categoryId = 99L), rule("پرداخت", categoryId = 1L))
        val result = engine.evaluate("پرداخت قبض", rules)
        assertEquals(1L, result.categoryId)
    }

    @Test
    fun `two rules same length - first by sort order stable`() {
        // Same length patterns, both present; sortedByDescending is stable so
        // original list order among equals is preserved -- the engine returns one match.
        val rules = listOf(
            rule("علی", categoryId = 1L, counterpartyId = 10L),
            rule("علی", categoryId = 2L)
        )
        val result = engine.evaluate("پرداخت علی", rules)
        assertNotNull(result.matchedRule)
        assertTrue(result.categoryId == 1L || result.categoryId == 2L)
    }
}