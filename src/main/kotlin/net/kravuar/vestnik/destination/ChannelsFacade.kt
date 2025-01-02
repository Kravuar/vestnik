package net.kravuar.vestnik.destination

interface ChannelsFacade {
    data class ChannelInput(
        val id: String,
        val name: String,
        val type: ChannelPlatform,
        val sources: Set<String>
    )
    fun getChannel(id: String): Channel
    fun getChannelByName(name: String): Channel
    fun getAllChannels(): List<Channel>
    fun addChannel(input: ChannelInput): Channel
}