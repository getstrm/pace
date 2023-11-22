package com.getstrm.pace.processing_platforms.h2

import build.buf.gen.getstrm.pace.api.entities.v1alpha.DataPolicy
import com.getstrm.pace.namedField
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class H2ClientTest {

    private lateinit var underTest: H2Client

    @BeforeEach
    fun setUp() {
        underTest = H2Client()
    }

    @AfterEach
    fun tearDown() {
        underTest.close()
    }

    @Test
    fun `insert and retrieve basic CSV`() {
        // Given
        val dataPolicy = DataPolicy.newBuilder().apply {
            sourceBuilder.addAllFields(
                listOf(
                    namedField("id", "int"),
                    namedField("name", "string"),
                    namedField("age", "float"),
                    namedField("subscribed", "boolean")
                )
            )
        }.build()
        val csv = """
            id,name,age,subscribed
            1,John,23.5,true
            2,Jane,24.5,false
            
        """.trimIndent()

        // When
        underTest.insertCSV(dataPolicy, csv, "test_table")

        // Then the values are inserted with the correct types
        val results = underTest.jooq.select().from("test_table").fetch()
        results.size shouldBe 2
        results.first().apply {
            get("id") shouldBe 1
            get("name") shouldBe "John"
            get("age") shouldBe 23.5f
            get("subscribed") shouldBe true
        }
        results.last().apply {
            get("id") shouldBe 2
            get("name") shouldBe "Jane"
            get("age") shouldBe 24.5f
            get("subscribed") shouldBe false
        }
        // And the results formatted as CSV are the same as the input
        results.formatCSV() shouldBe csv
    }
}
