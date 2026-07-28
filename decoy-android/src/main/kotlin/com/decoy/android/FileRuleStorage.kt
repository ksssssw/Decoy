package com.decoy.android

import com.google.gson.Gson
import com.decoy.core.MockRule
import com.decoy.core.RuleStorage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal class FileRuleStorage(filesDir: File) : RuleStorage {
    private val gson = Gson()
    private val file = File(filesDir, "decoy/rules.json")

    override fun load(): List<MockRule> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val stored = gson.fromJson(file.readText(), StoredRules::class.java)
            stored?.rules.orEmpty().filterNotNull().map { it.sanitized() }
        }.getOrElse {
            android.util.Log.w("Decoy", "Failed to load mock rules — starting empty", it)
            emptyList()
        }
    }

    override fun save(rules: List<MockRule>) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        runCatching {
            file.parentFile?.mkdirs()
            // Write-then-fsync-then-rename: a crash mid-write can't corrupt the
            // rules file, and the sync orders the data before the rename so a
            // power loss right after it can't leave an empty rules.json behind.
            FileOutputStream(tmp).use { out ->
                out.write(gson.toJson(StoredRules(version = 1, rules = rules)).toByteArray())
                out.fd.sync()
            }
            // rename() atomically replaces the target on POSIX. Never delete the
            // original as a fallback — if the rename fails the old rules survive.
            if (!tmp.renameTo(file)) throw IOException("Could not replace ${file.name}")
        }.onFailure {
            tmp.delete()
            android.util.Log.w("Decoy", "Failed to save mock rules", it)
        }
    }

    private data class StoredRules(val version: Int, val rules: List<MockRule?>?)
}
