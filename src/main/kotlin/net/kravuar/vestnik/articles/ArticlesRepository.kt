package net.kravuar.vestnik.articles

import org.springframework.data.jpa.repository.JpaRepository

internal interface ArticlesRepository: JpaRepository<Article, Long>