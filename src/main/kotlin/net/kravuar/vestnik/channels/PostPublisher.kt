package net.kravuar.vestnik.channels

import net.kravuar.vestnik.post.Post
import net.kravuar.vestnik.processor.ProcessedArticle

internal typealias MessageId = Long

internal interface PostPublisher {
    fun publish(processedArticle: ProcessedArticle, channel: Channel, media: List<ChannelsFacade.Media>): Post
    fun forward(originalChannel: Channel, messageId: MessageId, targetChannel: Channel): Post
    fun platform(): ChannelPlatform
}