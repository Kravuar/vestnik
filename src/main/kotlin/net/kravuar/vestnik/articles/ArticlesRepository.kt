package net.kravuar.vestnik.articles

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository

internal interface ArticlesRepository: JpaRepository<Article, Long> {
    fun findAllByStatus(
        status: Article.Status,
        sort: Sort
    ): List<Article>

    fun findAllByStatus(
        status: Article.Status,
        page: Pageable
    ): Page<Article>

    fun findAllSorted(): List<Article> {
        return findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
    }

    fun findPageSorted(
        page: Int,
        size: Int = PAGE_SIZE
    ): Page<Article> {
        return findAll(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        )
    }

    fun findAllByStatusSorted(status: Article.Status): List<Article> {
        return findAllByStatus(status, Sort.by(Sort.Direction.DESC, "createdAt"))
    }

    fun findPageByStatusSorted(
        status: Article.Status,
        page: Int,
        size: Int = PAGE_SIZE
    ): Page<Article> {
        return findAllByStatus(
            status,
            PageRequest.of(
                1,
                PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt")
            )
        )
    }

    companion object {
        const val PAGE_SIZE = 10
    }
}