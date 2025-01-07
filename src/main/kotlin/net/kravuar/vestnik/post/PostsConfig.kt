package net.kravuar.vestnik.post

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class PostsConfig {
    @Bean
    fun postsFacade(
        postsRepository: PostsRepository,
    ): PostsFacade = SimplePostsFacade(postsRepository)
}