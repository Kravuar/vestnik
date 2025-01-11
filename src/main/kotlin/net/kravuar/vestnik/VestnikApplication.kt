package net.kravuar.vestnik

import net.kravuar.vestnik.assistant.TelegramAssistantFacade
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = ["net.kravuar.vestnik"])
@EnableConfigurationProperties
@EnableTransactionManagement
@EnableScheduling
@EntityScan(basePackages = ["net.kravuar.vestnik"])
class VestnikApplication

suspend fun main(args: Array<String>) {
	val context = runApplication<VestnikApplication>(*args)
	val assistant = context.getBean(TelegramAssistantFacade::class.java)
	assistant.start().join()
}
