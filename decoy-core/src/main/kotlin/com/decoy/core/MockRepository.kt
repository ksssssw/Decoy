package com.decoy.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Thread-safe registry of mock rules. For SDK-internal use. */
public object MockRepository {
    // Immutable snapshot swapped in a single assignment — readers (most
    // importantly findMatchingRule, which runs on every intercepted request)
    // can never observe a mid-mutation empty or partially-filled list.
    @Volatile
    private var rules: List<MockRule> = emptyList()
    private var storage: RuleStorage? = null

    // Every mutation is a read-modify-write over the snapshot — serialize them
    // so concurrent web-UI/API calls can't interleave (e.g. a toggle racing a
    // delete would resurrect the deleted rule or drop the toggle).
    private val writeLock = Any()

    // findMatchingRule runs on every intercepted request — compile each distinct
    // pattern once. Grows with distinct patterns ever seen; trivially small.
    private val regexCache = ConcurrentHashMap<String, Regex>()

    // Patterns that blew the match deadline once (catastrophic backtracking) are
    // skipped for the rest of the process — they must never hang request threads.
    private val timedOutPatterns = ConcurrentHashMap.newKeySet<String>()

    private val persistExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "decoy-rule-persist").apply { isDaemon = true }
    }

    /** Attaches persistent storage and replaces current rules with the stored ones. */
    public fun attachStorage(ruleStorage: RuleStorage) {
        synchronized(writeLock) {
            storage = ruleStorage
            rules = runCatching { ruleStorage.load() }.getOrDefault(emptyList())
        }
    }

    public fun addRule(rule: MockRule) {
        synchronized(writeLock) {
            rules = rules + rule
            persist()
        }
    }

    public fun removeRule(id: String) {
        synchronized(writeLock) {
            rules = rules.filterNot { it.id == id }
            persist()
        }
    }

    public fun updateRule(updated: MockRule) {
        synchronized(writeLock) {
            if (rules.none { it.id == updated.id }) return
            rules = rules.map { if (it.id == updated.id) updated else it }
            persist()
        }
    }

    public fun toggleRule(id: String) {
        synchronized(writeLock) {
            if (rules.none { it.id == id }) return
            rules = rules.map { if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it }
            persist()
        }
    }

    /** Appends [newRules] keeping existing ones (import in merge mode). */
    public fun addAll(newRules: List<MockRule>) {
        synchronized(writeLock) {
            rules = rules + newRules
            persist()
        }
    }

    /** Replaces the whole rule set (import in replace mode). */
    public fun replaceAll(newRules: List<MockRule>) {
        synchronized(writeLock) {
            rules = newRules.toList()
            persist()
        }
    }

    /** Enables/disables every rule in [group] at once. */
    public fun setGroupEnabled(group: String, enabled: Boolean) {
        synchronized(writeLock) {
            if (rules.none { it.group == group && it.isEnabled != enabled }) return
            rules = rules.map { if (it.group == group) it.copy(isEnabled = enabled) else it }
            persist()
        }
    }

    /** Enables/disables every rule — master switch. */
    public fun setAllEnabled(enabled: Boolean) {
        synchronized(writeLock) {
            if (rules.none { it.isEnabled != enabled }) return
            rules = rules.map { it.copy(isEnabled = enabled) }
            persist()
        }
    }

    public fun getRules(): List<MockRule> = rules

    /**
     * Returns the first matching enabled rule in list order — the list order
     * (as arranged in the web UI) is the precedence: topmost wins.
     */
    public fun findMatchingRule(url: String, method: String): MockRule? {
        return rules.firstOrNull { rule ->
            rule.isEnabled &&
                (rule.method == "*" || rule.method.equals(method, ignoreCase = true)) &&
                matchesSafely(rule.urlPattern, url)
        }
    }

    /** Compiles once per distinct pattern; invalid patterns never match (and are never cached). */
    private fun compiledPattern(pattern: String): Regex? =
        regexCache[pattern]
            ?: runCatching { Regex(pattern) }.getOrNull()?.also { regexCache[pattern] = it }

    /**
     * Runs the match against a deadline-checking [CharSequence]: catastrophic
     * backtracking re-reads characters endlessly, so the deadline is hit inside
     * the regex engine and the pattern is quarantined instead of hanging the
     * app's network thread. Costs one bounded stall per bad pattern per process.
     */
    private fun matchesSafely(pattern: String, url: String): Boolean {
        if (pattern in timedOutPatterns) return false
        val regex = compiledPattern(pattern) ?: return false
        return try {
            regex.containsMatchIn(DeadlineCharSequence(url, System.nanoTime() + MATCH_BUDGET_NANOS))
        } catch (_: MatchDeadlineExceeded) {
            timedOutPatterns.add(pattern)
            false
        }
    }

    /**
     * Rearranges rules to match [layout] (drag & drop result from the web UI),
     * applying group membership changes at the same time. Rules missing from
     * the layout keep their relative order at the end of the list.
     */
    public fun applyLayout(layout: List<RulePlacement>) {
        synchronized(writeLock) {
            val byId = rules.associateBy { it.id }
            val placed = layout.mapNotNull { p ->
                byId[p.id]?.let { if (it.group == p.group) it else it.copy(group = p.group) }
            }
            val placedIds = placed.mapTo(HashSet()) { it.id }
            val leftover = rules.filter { it.id !in placedIds }
            rules = placed + leftover
            persist()
        }
    }

    /** Renames a group; renaming onto an existing group name merges them. */
    public fun renameGroup(from: String, to: String) {
        synchronized(writeLock) {
            if (rules.none { it.group == from }) return
            rules = rules.map { if (it.group == from) it.copy(group = to) else it }
            persist()
        }
    }

    public fun clear() {
        synchronized(writeLock) {
            rules = emptyList()
            persist()
        }
    }

    private fun persist() {
        val target = storage ?: return
        val snapshot = getRules()
        persistExecutor.execute {
            runCatching { target.save(snapshot) }
        }
    }
}

private const val MATCH_BUDGET_NANOS = 100_000_000L // 100 ms

private class MatchDeadlineExceeded : RuntimeException() {
    // Thrown on the hot path purely for control flow — the stack is never used.
    override fun fillInStackTrace(): Throwable = this
}

private class DeadlineCharSequence(
    private val text: String,
    private val deadline: Long,
) : CharSequence {
    override val length: Int get() = text.length

    override fun get(index: Int): Char {
        if (System.nanoTime() > deadline) throw MatchDeadlineExceeded()
        return text[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        DeadlineCharSequence(text.substring(startIndex, endIndex), deadline)
}
