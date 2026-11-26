package com.kaotoface.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ChineseTextNormalizerTest {
    @Test
    fun repairsBaijiuAsHundredNineInsideAnAmount() {
        assertEquals("三百九十元", ChineseTextNormalizer.normalize("三 白酒 十元"))
    }

    @Test
    fun doesNotTurnMeasureWordsIntoNumbers() {
        assertEquals("三杯白酒十元", ChineseTextNormalizer.normalize("三杯 白酒 十元"))
    }

    @Test
    fun removesTokenSpacesButKeepsPunctuation() {
        assertEquals("好的，下午发给大家。", ChineseTextNormalizer.normalize("好 的 ， 下午 发给 大家 。"))
    }
}
