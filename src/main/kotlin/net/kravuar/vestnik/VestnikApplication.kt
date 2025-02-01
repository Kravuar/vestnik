package net.kravuar.vestnik

import net.kravuar.vestnik.assistant.TelegramAssistantFacade
import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationEvent
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = ["net.kravuar.vestnik"])
@EnableConfigurationProperties
@EnableTransactionManagement
@EnableScheduling
@EntityScan(basePackages = ["net.kravuar.vestnik"])
internal class VestnikApplication

@Configuration
@ConditionalOnProperty("logging.level.net.kravuar.vestnik", havingValue = "DEBUG")
internal class VestnikDebugConfiguration {
	@EventListener
	fun onApplication(event: ApplicationEvent) {
		LOG.debug(event)
	}

	companion object {
		private val LOG = LogManager.getLogger(VestnikApplication::class.java)
	}
}

suspend fun main(args: Array<String>) {
    val context = runApplication<VestnikApplication>(*args)
    val assistant = context.getBean(TelegramAssistantFacade::class.java)
    assistant.start().join()
}
