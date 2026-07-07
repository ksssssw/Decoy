package com.decoy.android

import com.google.gson.Gson
import com.decoy.core.MockRule
import com.decoy.core.RuleStorage
import java.io.File

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
        runCatching {
            file.parentFile?.mkdirs()
            // Write-then-rename so a crash mid-write can't corrupt the rules file.
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(gson.toJson(StoredRules(version = 1, rules = rules)))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }.onFailure {
            android.util.Log.w("Decoy", "Failed to save mock rules", it)
        }
    }

    private data class StoredRules(val version: Int, val rules: List<MockRule?>?)
}
