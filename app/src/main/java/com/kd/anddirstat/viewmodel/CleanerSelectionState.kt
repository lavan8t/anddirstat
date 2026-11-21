package com.kd.anddirstat.viewmodel

/**
 * The only state shared by the cleaner ViewModels: the selected path set and the sort order.
 * Everything else (undo history, prefs, vibration, operation progress) stays per-screen.
 */
data class CleanerSelectionState<S : Any>(
    val selectedPaths: Set<String> = emptySet(),
    val sortOrder: S
) {
    fun toggle(path: String): CleanerSelectionState<S> = copy(
        selectedPaths = if (selectedPaths.contains(path)) selectedPaths - path else selectedPaths + path
    )

    fun selectAll(paths: Collection<String>): CleanerSelectionState<S> = copy(selectedPaths = paths.toSet())

    fun invert(paths: Collection<String>): CleanerSelectionState<S> = copy(selectedPaths = paths.toSet() - selectedPaths)

    fun clear(): CleanerSelectionState<S> = copy(selectedPaths = emptySet())

    fun setSort(order: S): CleanerSelectionState<S> = copy(sortOrder = order)
}
