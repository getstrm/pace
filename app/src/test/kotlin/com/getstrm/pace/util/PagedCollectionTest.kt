package com.getstrm.pace.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

class PagedCollectionTest {

    @Test
    fun map() {
        val p = listOf(1,2,3).withPageInfo()
        p.pageInfo.total shouldBe 3
        p.size shouldBe 3
        val q = p.map{ 2* it}
        q.data shouldBe listOf(2,4,6)
        q.size shouldBe 3
        q.pageInfo.total shouldBe 3
    }

    @Test
    fun firstOrNull() {
        val p = listOf(1,2,3).withPageInfo()
        p.firstOrNull() shouldBe 1
        val q = emptyList<Int>().withPageInfo()
        q.firstOrNull() shouldBe null
    }

    @Test
    fun find() {
        val p = listOf(1,2,3).withPageInfo()
        p.find{ it == 2} shouldBe 2
        p.find{ it == 8} shouldBe null
    }

    @Test
    fun getSize() {
        val p = listOf<Int>().withPageInfo()
        p.size shouldBe 0
        val q = listOf(1,2,3).withPageInfo()
        q.size shouldBe 3
    }
    
    @Test
    fun filter() {
        val p = (1..10).toList().withPageInfo()
        val q = p.filter { it % 2 == 0 }
        q.size shouldBe 5
        q.data shouldBe listOf(2,4,6,8,10)
    }
}
