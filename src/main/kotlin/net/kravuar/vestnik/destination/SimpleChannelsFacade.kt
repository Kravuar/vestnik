package net.kravuar.vestnik.destination

import jakarta.transaction.Transactional
import net.kravuar.vestnik.source.SourcesFacade

internal class SimpleChannelsFacade(
    private val destinationRepository: DestinationRepository,
    private val sourcesFacade: SourcesFacade
): ChannelsFacade {

    override fun getChannel(id: String): Channel {
        return destinationRepository
            .findById(id)
            .orElseThrow { IllegalArgumentException("Пункт назначения с id=$id не найден") }
    }

    override fun getChannelByName(name: String): Channel {
        return destinationRepository
            .findByName(name)
    }

    override fun getAllChannels(): List<Channel> {
        return destinationRepository.findAll()
    }

    @Transactional
    override fun addChannel(input: ChannelsFacade.ChannelInput): Channel {
        return destinationRepository.save(Channel(
            input.id,
            input.name,
            input.type,
            input.sources.map { sourcesFacade.getSource(it) }.toSet()
        ))
    }
}