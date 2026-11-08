package com.inspiredandroid.kai.agent

/** Authorization is derived by the host from the current user command, never model arguments. */
internal object ToolPolicy {
    fun denial(action: PhoneAction, scope: AgentScope, supported: Boolean, enabled: Boolean, foregroundUnlocked: Boolean): String? = when {
        !supported -> "unsupported_platform"
        !enabled -> "tool_disabled"
        scope.origin == AgentOrigin.BACKGROUND -> "background_action_denied"
        action.writes && (scope.flashlightTarget == null || scope.flashlightTarget != (action == PhoneAction.LIGHT_ON)) -> "explicit_user_action_required"
        action.writes && !foregroundUnlocked -> "foreground_unlock_required"
        else -> null
    }
}
