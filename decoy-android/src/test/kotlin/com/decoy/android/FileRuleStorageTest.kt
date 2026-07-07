package com.decoy.android

import com.decoy.core.MockRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileRuleStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun storage() = FileRuleStorage(tmp.root)

    private fun rulesFile() = File(tmp.root, "decoy/rules.json")

    private fun rule(id: String) = MockRule(
        id = id,
        urlPattern = "/posts",
        method = "GET",
        statusCode = 200,
        responseBody = "{}",
        group = "g",
    )

    @Test
    fun `save and load roundtrip preserves rules and order`() {
        val storage = storage()
        storage.save(listOf(rule("b"), rule("a")))

        assertEquals(listOf("b", "a"), storage.load().map { it.id })
        assertEquals("g", storage.load().first().group)
    }

    @Test
    fun `missing file loads as empty`() {
        assertTrue(storage().load().isEmpty())
    }

    @Test
    fun `corrupt json loads as empty instead of crashing`() {
        rulesFile().parentFile!!.mkdirs()
        rulesFile().writeText("{not json!!!")
        assertTrue(storage().load().isEmpty())
    }

    @Test
    fun `null entries are filtered and sanitized on load`() {
        rulesFile().parentFile!!.mkdirs()
        rulesFile().writeText("""{"version":1,"rules":[null,{"id":"a"}]}""")

        val loaded = storage().load()
        assertEquals(1, loaded.size)
        assertEquals("a", loaded.single().id)
        // sanitized() must have backfilled nulls so copy() is safe
        loaded.single().copy(isEnabled = false)
    }

    @Test
    fun `save leaves no tmp file behind`() {
        storage().save(listOf(rule("a")))
        val leftovers = rulesFile().parentFile!!.listFiles()!!.filter { it.name.endsWith(".tmp") }
        assertTrue(leftovers.isEmpty(), "leftover tmp files: $leftovers")
    }

    @Test
    fun `save creates missing parent directories`() {
        assertTrue(!rulesFile().parentFile!!.exists())
        storage().save(listOf(rule("a")))
        assertTrue(rulesFile().exists())
    }
}
