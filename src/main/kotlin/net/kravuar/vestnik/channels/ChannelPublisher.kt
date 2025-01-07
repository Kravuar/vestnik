package net.kravuar.vestnik.channels

import net.kravuar.vestnik.processor.ProcessedArticle

internal typealias MessageId = String

internal interface ChannelPublisher {
    fun publish(processedArticle: ProcessedArticle, channel: Channel): MessageId
    fun forward(originalChannel: Channel, messageId: MessageId, targetChannel: Channel): MessageId
    fun platform(): ChannelPlatform
}