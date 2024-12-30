package net.kravuar.vestnik.assistant

interface AssistantRequestProcessor {
    fun process(request: String): String
}