package net.kravuar.vestnik.post

import jakarta.transaction.Transactional
import net.kravuar.vestnik.post.PostsFacade.PostInput

internal open class SimplePostsFacade(
    private val postsRepository: PostsRepository,
) : PostsFacade {
    override fun getPost(postId: Long): Post {
        return postsRepository.findById(postId).orElseThrow {
            IllegalStateException("Пост с id=$postId не найден")
        }
    }

    override fun getPostsOfArticle(articleId: Long): List<Post> {
        return postsRepository.findAllByProcessedArticleArticleId(articleId)
    }

    override fun existsPostOfProcessedArticleAndChannel(processedArticleId: Long, channelId: Long): Boolean {
        return postsRepository.existsPostByProcessedArticleIdAndChannelId(processedArticleId, channelId)
    }

    @Transactional
    override fun addPost(postInput: PostInput): Post {
        return postsRepository.save(
            Post(
                postInput.processedArticle.orElseThrow { IllegalArgumentException("Обработанная статья не может отсутствовать") },
                postInput.channel.orElseThrow { IllegalArgumentException("Целевой канал не может отсутствовать") },
                postInput.channelPostId.orElseThrow { IllegalArgumentException("ID Поста в целевом канале не может отсутствовать") },
            ).apply {
                postInput.isForwarded.ifPresent { this.isForwarded = it }
            })
    }
}