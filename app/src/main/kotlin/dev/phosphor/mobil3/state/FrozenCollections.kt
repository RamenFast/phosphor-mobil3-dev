package dev.phosphor.mobil3.state

import java.util.Collections

/** Read-only, defensively copied collection values for durable protocol contracts. */
class FrozenList<out T> private constructor(
    private val values: List<T>,
) : List<T> by values {
    override fun equals(other: Any?): Boolean = values == other
    override fun hashCode(): Int = values.hashCode()
    override fun toString(): String = values.toString()

    companion object {
        fun <T> copyOf(values: Iterable<T>): FrozenList<T> = FrozenList(
            Collections.unmodifiableList(values.toList()),
        )
    }
}

class FrozenSet<out T> private constructor(
    private val values: Set<T>,
) : Set<T> by values {
    override fun equals(other: Any?): Boolean = values == other
    override fun hashCode(): Int = values.hashCode()
    override fun toString(): String = values.toString()

    companion object {
        fun <T> copyOf(values: Iterable<T>): FrozenSet<T> = FrozenSet(
            Collections.unmodifiableSet(LinkedHashSet(values.toList())),
        )
    }
}

class FrozenMap<K, out V> private constructor(
    private val entriesByKey: Map<K, V>,
) : Map<K, V> by entriesByKey {
    override fun equals(other: Any?): Boolean = entriesByKey == other
    override fun hashCode(): Int = entriesByKey.hashCode()
    override fun toString(): String = entriesByKey.toString()

    companion object {
        fun <K, V> copyOf(values: Map<K, V>): FrozenMap<K, V> = FrozenMap(
            Collections.unmodifiableMap(LinkedHashMap(values)),
        )
    }
}

fun <T> frozenListOf(vararg values: T): FrozenList<T> = FrozenList.copyOf(values.asList())
fun <T> frozenSetOf(vararg values: T): FrozenSet<T> = FrozenSet.copyOf(values.asList())
fun <K, V> frozenMapOf(vararg values: Pair<K, V>): FrozenMap<K, V> = FrozenMap.copyOf(values.toMap())
