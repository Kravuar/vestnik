package net.kravuar.vestnik.channels

import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.forward
import dev.inmo.tgbotapi.extensions.api.send.media.sendVisualMediaGroup
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.requests.abstracts.InputFile
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.RawChatId
import dev.inmo.tgbotapi.types.media.TelegramMediaPhoto
import dev.inmo.tgbotapi.types.media.TelegramMediaVideo
import dev.inmo.tgbotapi.types.message.HTML
import kotlinx.coroutines.runBlocking
import net.kravuar.vestnik.post.PostsFacade
import net.kravuar.vestnik.processor.ProcessedArticle

internal open class TelegramPublisher(
    postsFacade: PostsFacade,
    private val telegramBot: TelegramBot
) : AbstractPostPublisher(postsFacade) {
    override fun sendPost(processedArticle: ProcessedArticle, channel: Channel, media: List<ChannelsFacade.Media>): MessageId {
        return runBlocking {
            if (media.isNotEmpty()) {
                telegramBot.sendVisualMediaGroup(
                    chatId = ChatId(RawChatId(channel.id)),
                    media = media.mapIndexed { idx, media ->
                        val caption = if (idx == 0) {
                            processedArticle.content
                        } else { null }
                        when (media.type) {
                            ChannelsFacade.Media.Type.PHOTO -> {
                                TelegramMediaPhoto(
                                    file = InputFile.fromId(media.fileId),
                                    text = caption,
                                    parseMode = HTML
                                )
                            }
                            ChannelsFacade.Media.Type.VIDEO -> {
                                TelegramMediaVideo(
                                    file = InputFile.fromId(media.fileId),
                                    text = caption,
                                    parseMode = HTML
                                )
                            }
                        }
                    }
                )
            } else {
                telegramBot.send(
                    chatId = ChatId(RawChatId(channel.id)),
                    text = processedArticle.content,
                    parseMode = HTML
                )
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