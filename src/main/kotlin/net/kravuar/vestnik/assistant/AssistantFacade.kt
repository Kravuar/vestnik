package net.kravuar.vestnik.assistant

interface AssistantFacade {
    fun sendMessageToAdmins(message: String, replyToMessageId: Int?)
    fun sendMessageToChat(message: String, chatId: Long)
    fun forwardMessageToChat(fromChatId: Long, toChatId: Long, messageId: Int)
}