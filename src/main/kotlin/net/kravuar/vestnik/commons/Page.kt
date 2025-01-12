package net.kravuar.vestnik.commons

data class Page<T>(
    val totalPages: Int,
    val content: List<T>
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 3
    }
}