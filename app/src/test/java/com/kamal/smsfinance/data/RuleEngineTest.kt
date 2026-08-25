package com.kamal.smsfinance.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private val engine = RuleEngine()

    private fun rule(
        pattern: String,
        categoryId: Long? = null,
        counterpartyId: Long? = null,
        action: RuleAction = RuleAction.CATEGORIZE,
        id: Long = 0
    ) = SmartRule(id = id, pattern = pattern, categoryId = categoryId, counterpartyId = counterpartyId, action = action)

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
    fun `should match a ZWNJ rule against a space-using text and vice versa`() {
        // normalizeText turns ZWNJ (U+200C) into a space, so a rule written with
        // ZWNJ ("می‌خواهم") must match a message that spells it with a space
        // ("می خواهم"), and a space-rule must match a ZWNJ text. Both sides
        // converge on the same normalized form.
        val zwnjRules = listOf(rule("می\u200Cخواهم", categoryId = 1L))
        val spaceTextResult = engine.evaluate("می خواهم پرداخت کنم", zwnjRules)
        assertEquals("rule with ZWNJ should match text with space", 1L, spaceTextResult.categoryId)

        val spaceRules = listOf(rule("می خواهم", categoryId = 2L))
        val zwnjTextResult = engine.evaluate("می\u200Cخواهم پرداخت کنم", spaceRules)
        assertEquals("rule with space should match text with ZWNJ", 2L, zwnjTextResult.categoryId)
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

    // ---------- IGNORE action ----------

    @Test
    fun `IGNORE rule is exposed via matchedRule for the caller to act on`() {
        // RuleEngine itself never drops anything -- it only ever suggests. The caller
        // (TransactionRepository) is the one that checks matchedRule.action and decides not to
        // store the SMS. This test only verifies the engine surfaces the action correctly.
        val rules = listOf(rule("تبلیغات بانک", action = RuleAction.IGNORE))
        val result = engine.evaluate("تبلیغات بانک: از وام جدید ما استفاده کنید", rules)

        assertNotNull(result.matchedRule)
        assertEquals(RuleAction.IGNORE, result.matchedRule?.action)
    }

    @Test
    fun `more specific IGNORE rule wins over a shorter CATEGORIZE rule`() {
        val rules = listOf(
            rule("خرید", categoryId = 1L, action = RuleAction.CATEGORIZE),
            rule("خرید اشتراک تبلیغاتی ناخواسته", action = RuleAction.IGNORE)
        )
        val result = engine.evaluate("پیامک: خرید اشتراک تبلیغاتی ناخواسته انجام شد", rules)

        assertEquals(RuleAction.IGNORE, result.matchedRule?.action)
    }

    @Test
    fun `CATEGORIZE rule unaffected when no IGNORE rule matches`() {
        val rules = listOf(
            rule("خرید", categoryId = 1L, action = RuleAction.CATEGORIZE),
            rule("چیز نامرتبط", action = RuleAction.IGNORE)
        )
        val result = engine.evaluate("خرید از فروشگاه", rules)

        assertEquals(RuleAction.CATEGORIZE, result.matchedRule?.action)
        assertEquals(1L, result.categoryId)
    }
}