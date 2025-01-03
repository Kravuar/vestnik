package net.kravuar.vestnik.destination

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import net.kravuar.vestnik.assistant.FunctionCallMeta
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class FunctionsConfig(
    private val channelsFacade: ChannelsFacade,
) {

    @JsonClassDescription("Данные для поиска конкретного канала/паблика")
    data class GetChannelInput(
        @JsonPropertyDescription("Имя канала/паблика")
        val name: String,
    )

    @Bean
    fun getChannel(): FunctionCallMeta<GetChannelInput> = FunctionCallMeta(
        "getChannel",
        "Если нужно получить конкретный канал/паблик",
        "Поиск конкретного канала/паблика",
        GetChannelInput::class.java,
    ) {
        try {
            val destination = channelsFacade.getChannel(it.name)
            return@FunctionCallMeta JsonNodeFactory.instance.objectNode().apply {
                put("id", destination.id)
                put("name", destination.name)
                put("platform", destination.platform.name)
            }
        } catch (e: Exception) {
            return@FunctionCallMeta JsonNodeFactory.instance.objectNode().apply {
                put("error", "Ошибка при получении информации о узле обработки: " + e.message)
            }
        }
    }

    @Bean
    fun getAllDestinations(): FunctionCallMeta<Void> = FunctionCallMeta(
        "getAllDestinations",
        "Если нужно получить список всех узлов обработки",
        "Получение списка всех узлов обработки",
        Void::class.java,
    ) {
        try {
            val destinations = channelsFacade.getAllChannels()
            return@FunctionCallMeta JsonNodeFactory.instance.arrayNode().apply {
                destinations.forEach {
                    add(
                        JsonNodeFactory.instance.objectNode().apply {
                            put("id", it.id)
                            put("name", it.name)
                            put("type", it.platform.name)
                        }
                    )
                }
            }
        } catch (e: Exception) {
            return@FunctionCallMeta JsonNodeFactory.instance.objectNode().apply {
                put("error", "Ошибка при получении списка узлов обработки: " + e.message)
            }
        }
    }

    @JsonClassDescription("Данные для добавления нового канала/паблика")
    data class AddChannelInput(
        @JsonPropertyDescription("ID канала/паблика")
        val id: String,
        @JsonPropertyDescription("Имя канала/паблика")
        val name: String,
        @JsonPropertyDescription("Тип канала/паблика")
        val type: ChannelPlatform,
    )
    @Bean
    fun addChannel(): FunctionCallMeta<AddChannelInput> = FunctionCallMeta(
        "addChannel",
        "Если нужно добавить новый канал/паблик",
        "Добавление нового канала/паблика",
        AddChannelInput::class.java,
    ) {
        try {
            val destination = channelsFacade.addChannel(
                it.id,
                it.name,
                it.type,
            )
            return@FunctionCallMeta JsonNodeFactory.instance.objectNode().apply {
                put("successfullyCreated", true)
                put("id", destination.id)
                put("name", destination.name)
                put("type", destination.platform.name)
            }
        } catch (e: Exception) {
            return@FunctionCallMeta JsonNodeFactory.instance.objectNode().apply {
                put("error", "Ошибка при добавлении нового узла обработки: " + e.message)
            }
        }
    }
}