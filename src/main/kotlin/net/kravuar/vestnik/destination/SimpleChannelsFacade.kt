package net.kravuar.vestnik.destination

import jakarta.transaction.Transactional

internal class SimpleChannelsFacade(
    private val channelRepository: ChannelRepository
): ChannelsFacade {

    override fun getChannel(id: String): Channel {
        return channelRepository
            .findById(id)
            .orElseThrow { IllegalArgumentException("Канал с id=$id не найден") }
    }

    override fun getChannelByName(name: String): Channel {
        return channelRepository
            .findByName(name)
    }

    override fun getAllChannels(): List<Channel> {
        return channelRepository.findAll()
    }

    @Transactional
    override fun addChannel(input: ChannelsFacade.ChannelInput): Channel {
        return channelRepository.save(Channel(
            input.id.orElseThrow { IllegalArgumentException("При создании канала id обязательно") },
            input.name.orElseThrow { IllegalArgumentException("При создании канала имя обязательно") },
            input.platform.orElseThrow { IllegalArgumentException("При создании канала платформа обязательно") },
        ).apply {
            input.sources.ifPresent { sources = it }
        })
    }

    override fun deleteChannel(name: String): Channel {
        return channelRepository.deleteByName(name)
    }
}