package net.kravuar.vestnik.post

import net.kravuar.vestnik.articles.ArticlesFacade
import net.kravuar.vestnik.destination.ChannelsFacade
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class PostsConfig {
    @Bean
    fun postsFacade(
        postsRepository: PostsRepository,
        articlesFacade: ArticlesFacade,
        channelsFacade: ChannelsFacade,
    ): PostsFacade = SimplePostsFacade(postsRepository, articlesFacade, channelsFacade)
}