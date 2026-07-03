package com.peekaboo.android

import android.content.Context
import com.google.gson.Gson
import com.peekaboo.core.MockRule
import com.peekaboo.core.RuleStorage
import java.io.File

internal class FileRuleStorage(context: Context) : RuleStorage {
    private val gson = Gson()
    private val file = File(context.filesDir, "peekaboo/rules.json")

    override fun load(): List<MockRule> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val stored = gson.fromJson(file.readText(), StoredRules::class.java)
            stored?.rules.orEmpty().filterNotNull().map { it.sanitized() }
        }.getOrElse {
            android.util.Log.w("Peekaboo", "Failed to load mock rules — starting empty", it)
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
            android.util.Log.w("Peekaboo", "Failed to save mock rules", it)
        }
    }

    private data class StoredRules(val version: Int, val rules: List<MockRule?>?)
}
