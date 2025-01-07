package net.kravuar.vestnik.post

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
internal interface PostsRepository: JpaRepository<Post, Long> {
    fun findAllByProcessedArticleArticleId(articleId: Long): List<Post>
}