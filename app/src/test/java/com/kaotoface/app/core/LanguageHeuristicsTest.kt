package com.kaotoface.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageHeuristicsTest {
    @Test
    fun englishPhoneticHallucinationHasNoAmount() {
        assertFalse(LanguageHeuristics.hasAmount("standby to syria"))
    }

    @Test
    fun chineseAmountsAreDetected() {
        assertTrue(LanguageHeuristics.hasAmount("390元"))
        assertTrue(LanguageHeuristics.hasAmount("三百九十元"))
    }

    @Test
    fun englishAmountsAreDetected() {
        assertTrue(LanguageHeuristics.hasAmount("three hundred ninety yuan"))
        assertTrue(LanguageHeuristics.hasAmount("390 yuan"))
    }
}
