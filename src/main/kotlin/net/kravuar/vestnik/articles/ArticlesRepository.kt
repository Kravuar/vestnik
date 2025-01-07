package net.kravuar.vestnik.articles

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository

internal interface ArticlesRepository: JpaRepository<Article, Long> {
    fun findPageSorted(
        page: Int,
        size: Int = PAGE_SIZE
    ): Page<Article> {
        return findAll(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        )
    }

    companion object {
        const val PAGE_SIZE = 10
    }
}