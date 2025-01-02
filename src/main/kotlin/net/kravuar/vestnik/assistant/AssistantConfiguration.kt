package net.kravuar.vestnik.assistant

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class AssistantConfiguration {


    @Bean
    fun assistantFacade(
        @Value("\${telegram.bot.name}") name: String,
        @Value("\${telegram.bot.token}") token: String,
        @Value("\${telegram.admin.channel}") adminChannel: Long,
        @Value("\${admins}") admins: Set<Long>,
        @Value("\${owner}") owner: Long
    ) = TelegramAssistantFacade(
        name,
        token,
        adminChannel,
        admins,
        owner,
    )
}