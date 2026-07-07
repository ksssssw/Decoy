package com.decoy.core

/** Persists mock rules across app restarts. Implemented by the runtime artifact. */
public interface RuleStorage {
    public fun load(): List<MockRule>
    public fun save(rules: List<MockRule>)
}
