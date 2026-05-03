package com.cocode.claudeemailapp.mail

interface ImapIdleSession {
    suspend fun listen(onEvent: suspend () -> Unit)
    fun close()
}
