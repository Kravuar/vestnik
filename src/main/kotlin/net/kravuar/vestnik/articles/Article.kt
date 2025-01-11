package net.kravuar.vestnik.articles

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import net.kravuar.vestnik.source.Source
import java.time.OffsetDateTime

@Entity
class Article(
    @ManyToOne(optional = false)
    @Column(updatable = false)
    var source: Source,
    @Column(nullable = false)
    var title: String,
    @Column(nullable = false)
    var description: String,
    @Column(nullable = false)
    var url: String,
    @Column(nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
) {
    override fun toString(): String {
        return "Article(id=$id, source=${source.name}, title='$title', url='$url')"
    }
}