package net.kravuar.vestnik.articles

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import net.kravuar.vestnik.source.Source
import java.time.OffsetDateTime

@Entity
class Article(
    @ManyToOne(optional = false)
    var source: Source,
    @Column(nullable = false)
    var sourceGuid: String,
    @Column(nullable = false, length = 4000)
    var title: String,
    @Column
    @Lob
    var description: String? = null,
    @Column(nullable = false, unique = true, length = 4000)
    var url: String,
    @Column(nullable = false)
    var createdAt: OffsetDateTime,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
) {
    override fun toString(): String {
        return "Article(id=$id, guid=${sourceGuid}, source=${source.name}, title='$title', url='$url')"
    }
}