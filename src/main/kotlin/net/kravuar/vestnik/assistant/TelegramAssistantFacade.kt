package net.kravuar.vestnik.assistant

import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.ForwardMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update

internal class TelegramAssistantFacade(
    token: String,
    private val name: String,
    private val adminChannel: Long,
    private val admins: Set<Long>,
    private val owner: Long,
    private val requestProcessor: AssistantRequestProcessor
): TelegramLongPollingBot(token), AssistantFacade {

    override fun sendMessageToAdmins(message: String, replyToMessageId: Int?) {
        sendApiMethod(SendMessage().apply {
            setChatId(adminChannel)
            enableHtml(true)
            disableWebPagePreview()
            replyToMessageId?.let { setReplyToMessageId(it) }
            text = message
        })
    }

    override fun sendMessageToChat(message: String, chatId: Long) {
        sendApiMethod(SendMessage().apply {
            setChatId(chatId)
            enableHtml(true)
            disableWebPagePreview()
            text = message
        })
    }

    override fun forwardMessageToChat(fromChatId: Long, toChatId: Long, messageId: Int) {
        sendApiMethod(ForwardMessage().apply {
            setFromChatId(fromChatId)
            setChatId(toChatId)
            setMessageId(messageId)
        })
    }

    override fun onUpdateReceived(update: Update) {
        if (isAdminCommand(update)) {
            val response = requestProcessor.process(update.message.text)
            val message = SendMessage().apply {
                setChatId(adminChannel)
                enableHtml(true)
                disableWebPagePreview()
                replyToMessageId = update.message.messageId
                text = response
            }
            sendApiMethod(message)
        }
    }

    private fun isAdminCommand(update: Update): Boolean {
        return update.hasMessage()
                && update.message.hasText()
                && update.message.chatId == adminChannel
                && update.message.entities.any {
                    it.type == "mention" && it.user.id == owner
                }
                && admins.contains(update.message.from.id)
    }

    override fun getBotUsername(): String = name
}