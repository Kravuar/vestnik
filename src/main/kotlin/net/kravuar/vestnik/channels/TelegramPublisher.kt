package net.kravuar.vestnik.channels

import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.forward
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.RawChatId
import kotlinx.coroutines.runBlocking
import net.kravuar.vestnik.post.PostsFacade
import net.kravuar.vestnik.processor.ProcessedArticle

internal class TelegramPublisher(
    postsFacade: PostsFacade,
    private val telegramBot: TelegramBot
) : AbstractPostPublisher(postsFacade) {
    override fun sendPost(processedArticle: ProcessedArticle, channel: Channel): MessageId {
        return runBlocking {
            telegramBot.send(
                chatId = ChatId(RawChatId(channel.id))
            ) {
                processedArticle.content
            }
        }.messageId.long
    }

    override fun sendForward(originalChannel: Channel, messageId: MessageId, targetChannel: Channel): MessageId {
        return runBlocking {
            if (originalChannel.platform == targetChannel.platform) {
                telegramBot.forward(
                    fromChatId = ChatId(RawChatId(originalChannel.id)),
                    toChatId = ChatId(RawChatId(targetChannel.id)),
                    messageIds = listOf(dev.inmo.tgbotapi.types.MessageId(messageId))
                )[0].long
            } else {
                throw IllegalArgumentException("Forward в другие платформы не поддерживается")
            }
        }
    }

    override fun platform(): ChannelPlatform {
        return ChannelPlatform.TG
    }
}