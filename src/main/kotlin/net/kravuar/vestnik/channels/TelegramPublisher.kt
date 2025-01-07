package net.kravuar.vestnik.channels

import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.forward
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.RawChatId
import kotlinx.coroutines.runBlocking
import net.kravuar.vestnik.processor.ProcessedArticle

internal class TelegramPublisher(
    private val telegramBot: TelegramBot
) : ChannelPublisher {
    override fun publish(processedArticle: ProcessedArticle, channel: Channel): MessageId {
        return runBlocking {
            telegramBot.send(
                chatId = ChatId(RawChatId(channel.id.toLong()))
            ) {
                processedArticle.content
            }
        }.messageId.toString()
    }

    override fun forward(originalChannel: Channel, messageId: MessageId, targetChannel: Channel): MessageId {
        return runBlocking {
            if (originalChannel.platform == targetChannel.platform) {
                telegramBot.forward(
                    fromChatId = ChatId(RawChatId(originalChannel.id.toLong())),
                    toChatId = ChatId(RawChatId(targetChannel.id.toLong())),
                    messageIds = listOf(dev.inmo.tgbotapi.types.MessageId(messageId.toLong()))
                )[0].toString()
            } else {
                throw IllegalArgumentException("Forward в другие платформы не поддерживается")
            }
        }
    }

    override fun platform(): ChannelPlatform {
        return ChannelPlatform.TG
    }
}