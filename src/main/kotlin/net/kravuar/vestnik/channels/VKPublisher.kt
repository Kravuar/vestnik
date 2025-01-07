package net.kravuar.vestnik.channels

import net.kravuar.vestnik.processor.ProcessedArticle

internal class VKPublisher(
) : ChannelPublisher {
    override fun publish(processedArticle: ProcessedArticle, channel: Channel): MessageId {
        TODO()
    }

    override fun forward(originalChannel: Channel, messageId: MessageId, targetChannel: Channel): MessageId {
        TODO()
    }

    override fun platform(): ChannelPlatform {
        return ChannelPlatform.VK
    }
}