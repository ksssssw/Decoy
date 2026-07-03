package com.peekaboo.core

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/** Thread-safe registry of mock rules. For SDK-internal use. */
public object MockRepository {
    private val rules = CopyOnWriteArrayList<MockRule>()
    private var storage: RuleStorage? = null
    private val persistExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "peekaboo-rule-persist").apply { isDaemon = true }
    }

    /** Attaches persistent storage and replaces current rules with the stored ones. */
    public fun attachStorage(ruleStorage: RuleStorage) {
        storage = ruleStorage
        val loaded = runCatching { ruleStorage.load() }.getOrDefault(emptyList())
        rules.clear()
        rules.addAll(loaded)
    }

    public fun addRule(rule: MockRule) {
        rules.add(rule)
        persist()
    }

    public fun removeRule(id: String) {
        rules.removeIf { it.id == id }
        persist()
    }

    public fun updateRule(updated: MockRule) {
        val idx = rules.indexOfFirst { it.id == updated.id }
        if (idx != -1) {
            rules[idx] = updated
            persist()
        }
    }

    public fun toggleRule(id: String) {
        val idx = rules.indexOfFirst { it.id == id }
        if (idx != -1) {
            rules[idx] = rules[idx].copy(isEnabled = !rules[idx].isEnabled)
            persist()
        }
    }

    /** Appends [newRules] keeping existing ones (import in merge mode). */
    public fun addAll(newRules: List<MockRule>) {
        rules.addAll(newRules)
        persist()
    }

    /** Replaces the whole rule set (import in replace mode). */
    public fun replaceAll(newRules: List<MockRule>) {
        rules.clear()
        rules.addAll(newRules)
        persist()
    }

    /** Enables/disables every rule in [group] at once. */
    public fun setGroupEnabled(group: String, enabled: Boolean) {
        var changed = false
        for (i in rules.indices) {
            val rule = rules[i]
            if (rule.group == group && rule.isEnabled != enabled) {
                rules[i] = rule.copy(isEnabled = enabled)
                changed = true
            }
        }
        if (changed) persist()
    }

    /** Enables/disables every rule — master switch. */
    public fun setAllEnabled(enabled: Boolean) {
        var changed = false
        for (i in rules.indices) {
            if (rules[i].isEnabled != enabled) {
                rules[i] = rules[i].copy(isEnabled = enabled)
                changed = true
            }
        }
        if (changed) persist()
    }

    public fun getRules(): List<MockRule> = rules.toList()

    /**
     * Returns the first matching enabled rule in list order — the list order
     * (as arranged in the web UI) is the precedence: topmost wins.
     */
    public fun findMatchingRule(url: String, method: String): MockRule? {
        return rules.firstOrNull { rule ->
            rule.isEnabled &&
                (rule.method == "*" || rule.method.equals(method, ignoreCase = true)) &&
                runCatching { Regex(rule.urlPattern).containsMatchIn(url) }.getOrDefault(false)
        }
    }

    /**
     * Rearranges rules to match [layout] (drag & drop result from the web UI),
     * applying group membership changes at the same time. Rules missing from
     * the layout keep their relative order at the end of the list.
     */
    public fun applyLayout(layout: List<RulePlacement>) {
        val byId = rules.associateBy { it.id }
        val placed = layout.mapNotNull { p ->
            byId[p.id]?.let { if (it.group == p.group) it else it.copy(group = p.group) }
        }
        val placedIds = placed.mapTo(HashSet()) { it.id }
        val leftover = rules.filter { it.id !in placedIds }
        rules.clear()
        rules.addAll(placed + leftover)
        persist()
    }

    /** Renames a group; renaming onto an existing group name merges them. */
    public fun renameGroup(from: String, to: String) {
        var changed = false
        for (i in rules.indices) {
            if (rules[i].group == from) {
                rules[i] = rules[i].copy(group = to)
                changed = true
            }
        }
        if (changed) persist()
    }

    public fun clear() {
        rules.clear()
        persist()
    }

    private fun persist() {
        val target = storage ?: return
        val snapshot = getRules()
        persistExecutor.execute {
            runCatching { target.save(snapshot) }
        }
    }
}
