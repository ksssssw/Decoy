package com.peekaboo.debug

import com.peekaboo.core.MockRule
import okhttp3.Request
import java.util.concurrent.CopyOnWriteArrayList

object MockRepository {
    private val rules = CopyOnWriteArrayList<MockRule>()

    fun addRule(rule: MockRule) = rules.add(rule)

    fun removeRule(id: String) = rules.removeIf { it.id == id }

    fun updateRule(updated: MockRule) {
        val idx = rules.indexOfFirst { it.id == updated.id }
        if (idx != -1) rules[idx] = updated
    }

    fun toggleRule(id: String) {
        val idx = rules.indexOfFirst { it.id == id }
        if (idx != -1) rules[idx] = rules[idx].copy(isEnabled = !rules[idx].isEnabled)
    }

    fun getRules(): List<MockRule> = rules.toList()

    fun findMatchingRule(request: Request): MockRule? {
        return rules.firstOrNull { rule ->
            rule.isEnabled &&
            (rule.method == "*" || rule.method.equals(request.method, ignoreCase = true)) &&
            runCatching { request.url.toString().matches(Regex(rule.urlPattern)) }.getOrDefault(false)
        }
    }

    fun clear() = rules.clear()
}
