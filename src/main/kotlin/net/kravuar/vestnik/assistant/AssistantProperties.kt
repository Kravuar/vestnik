package net.kravuar.vestnik.assistant

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "vestnik.assistant")
internal data class AssistantProperties(
    val articleLifeTime: Duration = Duration.ofDays(3),
    val commandLifeTime: Duration = Duration.ofMinutes(3)
)