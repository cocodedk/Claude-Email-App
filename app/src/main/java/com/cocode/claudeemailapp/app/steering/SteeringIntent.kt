package com.cocode.claudeemailapp.app.steering

/**
 * Intents the steering bar can emit. Translated into envelope builders by
 * [AppViewModel.dispatchSteering].
 */
sealed interface SteeringIntent {
    data object Status : SteeringIntent
    data object Cancel : SteeringIntent
    data object CancelDrainQueue : SteeringIntent
    data object Reset : SteeringIntent
    data class Reply(val askId: String, val body: String) : SteeringIntent
}
