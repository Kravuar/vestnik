package net.kravuar.vestnik.channels

import net.kravuar.vestnik.commons.Page
import net.kravuar.vestnik.processor.ProcessedArticle
import net.kravuar.vestnik.source.Source
import java.util.Optional

interface ChannelsFacade {
    data class ChannelInput(
        var id: Optional<Long> = Optional.empty(),
        var name: Optional<String> = Optional.empty(),
        var platform: Optional<ChannelPlatform> = Optional.empty(),
        var sources: Optional<MutableSet<Source>> = Optional.empty(),
    )

    /**
     * Find all non deleted
     */
    fun getChannels(): List<Channel>
    /**
     * Find page of non deleted
     */
    fun getChannels(page: Int): Page<Channel>

    /**
     * Find all including deleted
     */
    fun getAllChannels(): List<Channel>
    /**
     * Find page including deleted
     */
    fun getAllChannels(page: Int): Page<Channel>

    fun getChannelByName(name: String): Channel

    fun addChannel(input: ChannelInput): Channel
    fun deleteChannel(name: String): Boolean
    fun postArticle(processedArticle: ProcessedArticle, primaryChannel: Channel, forwardChannels: Collection<Channel>)
}