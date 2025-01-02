package net.kravuar.vestnik.articles

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import net.kravuar.vestnik.source.Source

enum class Status {
    NEW,
    PROCESSED,
    DECLINED,
    POSTED
}

@Entity
class Article(
    @ManyToOne(optional = false)
    @Column(updatable = false)
    var source: Source,
    @Column(nullable = false)
    var title: String,
    @Column(nullable = false)
    var url: String,
    @Column(nullable = false)
    @Lob
    var content: String,
    @Column(nullable = false)
    @Enumerated(EnumType.ORDINAL)
    var status: Status = Status.NEW,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
)