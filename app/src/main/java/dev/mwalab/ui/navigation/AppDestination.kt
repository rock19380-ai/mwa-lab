package dev.mwalab.ui.navigation

enum class AppDestination(val label: String) {
    HOME("Home"), SESSIONS("Sessions"), FAULT_LAB("Fault Lab"),
    LAB_IDENTITY("Test Wallet"), SETTINGS("Settings"), SESSION_DETAIL("Session Detail");

    val isTopLevel: Boolean get() = this != SESSION_DETAIL

    fun backDestination(): AppDestination = when (this) {
        SESSION_DETAIL -> SESSIONS
        else -> HOME
    }

    companion object { val topLevel = entries.filter { it.isTopLevel } }
}
