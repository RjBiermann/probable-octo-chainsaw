package com.lagradost.common.intelligence

enum class InfluenceLevel(val label: String) {
    STRONG("Strong"),
    MODERATE("Moderate"),
    WEAK("Weak");

    companion object {
        /**
         * Assign influence levels to items based on relative score position.
         * Top 20% = Strong, bottom 40% = Weak, middle = Moderate.
         */
        fun <T> assign(items: List<T>, scoreSelector: (T) -> Float): Map<T, InfluenceLevel> {
            if (items.isEmpty()) return emptyMap()
            if (items.size == 1) return mapOf(items[0] to STRONG)

            val sorted = items.sortedByDescending(scoreSelector)
            val strongCutoff = (sorted.size * 0.2).toInt().coerceAtLeast(1)
            val weakCutoff = sorted.size - (sorted.size * 0.4).toInt().coerceAtLeast(1)

            return sorted.mapIndexed { index, item ->
                val level = when {
                    index < strongCutoff -> STRONG
                    index >= weakCutoff -> WEAK
                    else -> MODERATE
                }
                item to level
            }.toMap()
        }
    }
}
