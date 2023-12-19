package com.getstrm.pace.util

import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

class UtilsKtTest {

    @Test
    fun orDefault() {
        val v = null
        v.orDefault(123) shouldBe 123
    }
    @Test
    fun orDefault2() {
        val v = 1
        v.orDefault(123) shouldBe 1
    }
    @Test
    fun orDefault3() {
        val v = 0
        v.orDefault(123) shouldBe 123
    }
}
