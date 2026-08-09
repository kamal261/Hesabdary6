package com.kamal.smsfinance

/**
 * A single, always-visible marker of which branch/build this install came from -- shown at the
 * bottom of Settings. Exists because this app had, at one point, three parallel branches
 * (v2/کلاد۱، Hesabdary6-main، and a third independent "SmsFinance.zip" line) being developed
 * without any way to tell them apart from inside the running app itself. Update BUILD_LABEL and
 * BUILD_DATE by hand whenever a meaningfully different build is installed for testing --
 * there's no CI wiring for this on purpose, it's meant to be a deliberate, visible act, not an
 * automatic timestamp nobody reads.
 */
object BuildInfo {
    const val BRANCH_LABEL = "Hesabdary6-main"
    const val BUILD_LABEL = "rev 12 — فیکس مبلغ لجر (ملی/رسالت) + ادغام فیکس‌های نسخه ۵"
    const val BUILD_DATE = "2026-08-09"
}
