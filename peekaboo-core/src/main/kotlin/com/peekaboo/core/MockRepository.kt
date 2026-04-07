package com.peekaboo.core

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

    fun findMatchingRule(url: String, method: String): MockRule? {
        return rules.firstOrNull { rule ->
            rule.isEnabled &&
            (rule.method == "*" || rule.method.equals(method, ignoreCase = true)) &&
            runCatching { Regex(rule.urlPattern).containsMatchIn(url) }.getOrDefault(false)
        }
    }

    fun clear() = rules.clear()
}
