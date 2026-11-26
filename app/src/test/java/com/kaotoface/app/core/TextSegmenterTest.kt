package com.kaotoface.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSegmenterTest {
    @Test
    fun longChineseTextIsSplitIntoPunctuatedChunks() {
        val text = "高频无意义的调用结构发布内容严禁利用接口形式刷屏恶意灌水重复投稿垃圾内容批量推送等扰乱社区支持的行为"
        val chunks = TextSegmenter.split(text, Lang.ZH)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 32 })
        assertTrue(chunks.all { it.last() in "。！？" })
    }

    @Test
    fun longEnglishTextIsSplitIntoPunctuatedChunks() {
        val text = "Posting high-frequency meaningless call structures is strictly prohibited. Using interface formats " +
            "to flood the community with repeated garbage content is also prohibited and may lead to account restrictions."
        val chunks = TextSegmenter.split(text, Lang.EN)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 180 })
        assertTrue(chunks.all { it.last() in ".!?" })
    }

    @Test
    fun shortAmountKeepsItsPunctuation() {
        assertEquals(listOf("三百九十元。"), TextSegmenter.split("三百九十元", Lang.ZH))
    }
}
