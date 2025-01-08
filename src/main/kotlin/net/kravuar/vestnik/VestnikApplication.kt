package net.kravuar.vestnik

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = ["net.kravuar.vestnik"])
@EnableTransactionManagement
@EnableScheduling
@EntityScan(basePackages = ["net.kravuar.vestnik"])
class VestnikApplication

fun main(args: Array<String>) {
	runApplication<VestnikApplication>(*args)
}
