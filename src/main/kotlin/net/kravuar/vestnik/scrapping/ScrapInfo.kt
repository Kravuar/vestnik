package net.kravuar.vestnik.scrapping

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.validation.constraints.NotBlank

@Entity
class ScrapInfo(
    @Column(nullable = false, unique = true, length = 4000)
    @get:NotBlank
    var urlPattern: String,
    @Column(length = 4000)
    @get:NotBlank
    var contentXPath: String,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
) {
    override fun toString(): String {
        return "ScrapInfo(id=$id, urlPattern='$urlPattern', contentXPath='$contentXPath')"
    }
}