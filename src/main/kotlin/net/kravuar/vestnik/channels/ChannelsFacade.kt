package net.kravuar.vestnik.channels

import net.kravuar.vestnik.processor.ProcessedArticle
import net.kravuar.vestnik.source.Source
import java.util.Optional

interface ChannelsFacade {
    data class ChannelInput(
        var id: Optional<String> = Optional.empty(),
        var name: Optional<String> = Optional.empty(),
        var platform: Optional<ChannelPlatform> = Optional.empty(),
        var sources: Optional<MutableSet<Source>> = Optional.empty(),
    )

    fun getChannel(id: String): Channel
    fun getChannelByName(name: String): Channel
    fun getAllChannels(): List<Channel>
    fun addChannel(input: ChannelInput): Channel
    fun deleteChannel(name: String): Channel
    fun postArticle(processedArticle: ProcessedArticle, primaryChannel: Channel, forwardChannels: Collection<Channel>)
}