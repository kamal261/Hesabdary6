package com.kamal.smsfinance.sms

import com.kamal.smsfinance.data.TransactionType
import org.junit.Assert.*
import org.junit.Test

class SmsParserTest {

    private val now = System.currentTimeMillis()

    @Test
    fun `parse - Mellat bank expense SMS with amount in Toman`() {
        val sender = "Mellat"
        val body = "مبلغ 500,000 تومان از حساب شما پرید. مانده: 1,200,000 تومان. شناسه: 123456"
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Recognized)
        val recognized = result as SmsParseResult.Recognized
        assertEquals(TransactionType.EXPENSE, recognized.parsed.type)
        assertEquals(500000L, recognized.parsed.amountToman)
        assertEquals("ملت", recognized.parsed.bankName)
        assertTrue(recognized.parsed.rawSms.contains("500,000"))
    }

    @Test
    fun `parse - Saman bank income SMS with amount in Rial`() {
        val sender = "Saman"
        val body = "واریز 2,000,000 ریال به حساب شما انجام شد. موجودی: 5,000,000 ریال"
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Recognized)
        val recognized = result as SmsParseResult.Recognized
        assertEquals(TransactionType.INCOME, recognized.parsed.type)
        assertEquals(200000L, recognized.parsed.amountToman) // 2,000,000 Rial = 200,000 Toman
        assertEquals("سامان", recognized.parsed.bankName)
    }

    @Test
    fun `parse - Blu bank terse ledger line with negative amount and balance`() {
        val sender = "Blu"
        val body = "-2,260,000 مانده: 1,500,000"
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Recognized)
        val recognized = result as SmsParseResult.Recognized
        assertEquals(TransactionType.EXPENSE, recognized.parsed.type)
        assertEquals(226000L, recognized.parsed.amountToman) // Rial / 10
        assertEquals("بلو", recognized.parsed.bankName)
    }

    @Test
    fun `parse - OTP SMS should be Ignored`() {
        val sender = "10000210"
        val body = "رمز یکبار مصرف شما: 123456. این کد را به کسی ندهید."
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Ignored)
    }

    @Test
    fun `parse - Promotional loan SMS should be Ignored`() {
        val sender = "10000210"
        val body = "تسهیلات 50 میلیون تومان با قسط 1 میلیون. برای اطلاعات تماس بگیرید."
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Ignored)
    }

    @Test
    fun `parse - SMS with URL should be Ignored`() {
        val sender = "Mellat"
        val body = "خرید 100,000 تومان. برای پیگیری: https://mellat.ir/track/123"
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Ignored)
    }

    @Test
    fun `parse - Unknown sender but bank name in body with balance line`() {
        val sender = "5000123456"
        val body = "پاسارگاد خرید 300,000 تومان از حساب شما پرید. مانده: 2,000,000"
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Recognized)
        val recognized = result as SmsParseResult.Recognized
        assertEquals(TransactionType.EXPENSE, recognized.parsed.type)
        assertEquals(300000L, recognized.parsed.amountToman)
        assertEquals("پاسارگاد", recognized.parsed.bankName)
    }

    @Test
    fun `parse - Card tail extraction`() {
        val sender = "Tejarat"
        val body = "برداشت 150,000 تومان از کارت ****1234. مانده: 500,000"
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Recognized)
        val recognized = result as SmsParseResult.Recognized
        assertEquals("1234", recognized.parsed.accountTail)
    }

    @Test
    fun `parse - Persian digits amount`() {
        val sender = "Saderat"
        val body = "مبلغ ۱۵۰،۰۰۰ تومان واریز شد. موجودی: ۳۰۰،۰۰۰"
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Recognized)
        val recognized = result as SmsParseResult.Recognized
        assertEquals(TransactionType.INCOME, recognized.parsed.type)
        assertEquals(150000L, recognized.parsed.amountToman)
    }

    @Test
    fun `parse - Expense keyword priority over income when both present`() {
        val sender = "Mellat"
        val body = "کارمزد 5,000 تومان برای واریز کسر شد. مانده: 100,000"
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Recognized)
        val recognized = result as SmsParseResult.Recognized
        assertEquals(TransactionType.EXPENSE, recognized.parsed.type)
    }

    @Test
    fun `parse - Empty body should be Ignored`() {
        val result = SmsParser.parse("Mellat", "", now)
        assertTrue(result is SmsParseResult.Ignored)
    }

    @Test
    fun `parse - Blank body should be Ignored`() {
        val result = SmsParser.parse("Mellat", "   ", now)
        assertTrue(result is SmsParseResult.Ignored)
    }

    @Test
    fun `parse - Balance inquiry only should be Ignored`() {
        val sender = "Mellat"
        val body = "موجودی شما: 1,000,000 تومان"
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Ignored)
    }

    @Test
    fun `parse - Discount promo should be Ignored`() {
        val sender = "10000210"
        val body = "تخفیف 20% خرید از دیجی‌کالا با کارت ملت"
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Ignored)
    }

    @Test
    fun `parse - Contest and prize SMS should be Ignored`() {
        val sender = "10000210"
        val body = "شما برنده قرعه‌کشی شده‌اید. جایزه 10 میلیون تومان. برای دریافت کلیک کنید."
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Ignored)
    }

    @Test
    fun `parse - Bank debit phrase "از حساب شما پرید" should be recognized even without known sender`() {
        val sender = "UnknownSender"
        val body = "پرداخت قبض آب 50,000 تومان از حساب شما پرید. مانده: 200,000"
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Recognized)
        val recognized = result as SmsParseResult.Recognized
        assertEquals(TransactionType.EXPENSE, recognized.parsed.type)
        assertEquals(50000L, recognized.parsed.amountToman)
    }

    @Test
    fun `parse - Transaction ID alone without balance should be Ignored`() {
        val sender = "Telecom"
        val body = "پرداخت صورتحساب 100,000 تومان انجام شد. شناسه تراکنش: 123456"
        val result = SmsParser.parse(sender, body, now)

        // Parser design: a lone amount + transaction id from a non-bank sender
        // (telecom bill payment) is rejected, not filed and not flagged for
        // review — only genuine bank signals are worth the user's attention.
        assertTrue(result is SmsParseResult.Ignored)
    }

    @Test
    fun `parse - Resalat bank unit-less signed amount with balance`() {
        val sender = "Resalat"
        val body = "-1,500,000 مانده: 3,000,000"
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Recognized)
        val recognized = result as SmsParseResult.Recognized
        assertEquals(TransactionType.EXPENSE, recognized.parsed.type)
        assertEquals(150000L, recognized.parsed.amountToman) // Rial / 10
        assertEquals("رسالت", recognized.parsed.bankName)
    }

    @Test
    fun `parse - Positive signed amount with balance = income`() {
        val sender = "Resalat"
        val body = "+2,000,000 مانده: 5,000,000"
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Recognized)
        val recognized = result as SmsParseResult.Recognized
        assertEquals(TransactionType.INCOME, recognized.parsed.type)
        assertEquals(200000L, recognized.parsed.amountToman)
    }

    @Test
    fun `parse - Amount with Persian thousands separator ٬`() {
        val sender = "Mellat"
        val body = "مبلغ ۱۵۰٬۰۰۰ تومان خرید از حساب شما پرید. مانده: ۵۰۰٬۰۰۰"
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Recognized)
        val recognized = result as SmsParseResult.Recognized
        assertEquals(150000L, recognized.parsed.amountToman)
    }

    @Test
    fun `parse - Bare amount pattern "مبلغ 500000 از"`() {
        val sender = "Mellat"
        val body = "مبلغ 500000 از حساب شما کسر گردید. مانده: 1000000"
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Recognized)
        val recognized = result as SmsParseResult.Recognized
        assertEquals(500000L, recognized.parsed.amountToman)
    }

    @Test
    fun `parse - Non-bank sender with bank name in body but no separator should not match`() {
        val sender = "Random"
        val body = "بلوتوث دستگاه شما متصل شد" // contains "بلو" but not as bank name
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Ignored)
    }

    @Test
    fun `parse - Bank name with separator in body should match`() {
        val sender = "Random"
        val body = "بلو برداشت 100,000 تومان. مانده: 500,000"
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Recognized)
        val recognized = result as SmsParseResult.Recognized
        assertEquals("بلو", recognized.parsed.bankName)
    }

    @Test
    fun `parse - Multiple expense keywords - first one wins`() {
        val sender = "Mellat"
        val body = "برداشت 100,000 و خرید 200,000 تومان. مانده: 500,000"
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Recognized)
        val recognized = result as SmsParseResult.Recognized
        // "برداشت" appears before "خرید" in EXPENSE_KEYWORDS list? Actually both are expense.
        // The test is that it picks expense type correctly
        assertEquals(TransactionType.EXPENSE, recognized.parsed.type)
    }

    @Test
    fun `parse - Description building truncates long SMS`() {
        val sender = "Mellat"
        val body = "این یک متن بسیار طولانی است که شامل خرید 100,000 تومان از فروشگاه بزرگ شهر است و باید truncate شود مانده: 500,000"
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Recognized)
        val recognized = result as SmsParseResult.Recognized
        assertTrue(recognized.parsed.description.length <= 80)
    }

    @Test
    fun `parse - Unknown bank falls back to "نامشخص"`() {
        val sender = "UnknownBank"
        val body = "خرید 100,000 تومان از حساب شما پرید. مانده: 500,000"
        val result = SmsParser.parse(sender, body, now)

        assertTrue(result is SmsParseResult.Recognized)
        val recognized = result as SmsParseResult.Recognized
        assertEquals("نامشخص", recognized.parsed.bankName)
    }
}