package net.kravuar.vestnik.channels

import net.kravuar.vestnik.post.PostsFacade
import net.kravuar.vestnik.processor.ProcessedArticle

internal open class VKPublisher(
    postsFacade: PostsFacade
) : AbstractPostPublisher(postsFacade) {

    override fun sendPost(processedArticle: ProcessedArticle, channel: Channel, mediaList: List<ChannelsFacade.Media>): MessageId {
        TODO("Not yet implemented")
    }

    override fun sendForward(originalChannel: Channel, messageId: MessageId, targetChannel: Channel): MessageId {
        TODO("Not yet implemented")
    }

    override fun platform(): ChannelPlatform {
        return ChannelPlatform.VK
    }
}