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

    @Test
    fun `failed rename keeps the previous rules and removes the tmp file`() {
        val storage = storage()
        storage.save(listOf(rule("original")))

        // Make rules.json un-replaceable: a non-empty directory defeats rename()
        val target = rulesFile()
        val backup = File(tmp.root, "backup.json")
        target.copyTo(backup)
        target.delete()
        target.mkdirs()
        File(target, "occupied").writeText("x")

        storage.save(listOf(rule("new"))) // must not throw

        assertTrue(target.isDirectory, "failed save must not delete the existing target")
        val leftovers = target.parentFile!!.listFiles()!!.filter { it.name.endsWith(".tmp") }
        assertTrue(leftovers.isEmpty(), "leftover tmp files: $leftovers")

        // And with the obstruction removed, the original content is recoverable
        // (the pre-fix fallback deleted the target before retrying the rename,
        // which could lose both the old and the new rules at once).
        target.deleteRecursively()
        backup.copyTo(target)
        assertEquals(listOf("original"), storage.load().map { it.id })
    }
}
