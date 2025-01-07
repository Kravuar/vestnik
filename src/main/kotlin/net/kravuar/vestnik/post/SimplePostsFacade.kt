package net.kravuar.vestnik.post

import jakarta.transaction.Transactional
import net.kravuar.vestnik.post.PostsFacade.PostInput

internal class SimplePostsFacade(
    private val postsRepository: PostsRepository,
) : PostsFacade {
    override fun getPosts(articleId: Long): List<Post> {
        return postsRepository.findAllByProcessedArticleArticleId(articleId)
    }

    @Transactional
    override fun addPost(postInput: PostInput): Post {
        return postsRepository.save(Post(
            postInput.processedArticle.orElseThrow { IllegalArgumentException("Обработанная статья не может отсутствовать") },
            postInput.channel.orElseThrow { IllegalArgumentException("Целевой канал не может отсутствовать") },
            postInput.channelPostId.orElseThrow { IllegalArgumentException("ID Поста в целевом канале не может отсутствовать") },
            postInput.adminId.orElseThrow { IllegalArgumentException("ID Администратора не может отсутствовать") },
        ))
    }
}