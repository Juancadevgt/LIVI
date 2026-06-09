package com.livi.maintenance.actions

enum class ActionType {
    CLEAR_CACHE,
    CLEAR_DATA,
    AIRPLANE_TOGGLE
}

object KnownPackages {
    const val POWER_APPS = "com.microsoft.msapps"
    const val TEAMS = "com.microsoft.teams"

    val LABELS = mapOf(
        POWER_APPS to "Power Apps",
        TEAMS to "Microsoft Teams"
    )
}
