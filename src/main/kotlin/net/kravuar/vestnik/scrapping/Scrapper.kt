package net.kravuar.vestnik.scrapping

interface Scrapper {
    fun scrap(url: String, xpath: String): String
}