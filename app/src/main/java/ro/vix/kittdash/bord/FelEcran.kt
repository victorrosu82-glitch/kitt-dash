package ro.vix.kittdash.bord

/**
 * Ecranele bordului, in ordinea in care le parcurge butonul "Ecran" de pe
 * masina.
 */
enum class FelEcran(val titlu: String) {
    BORD("BORD"),
    VITEZA("VITEZA"),
    ENERGIE("ENERGIE"),
    CONSOLA("CONSOLA"),
    HIBRID("HIBRID"),
    ;

    fun urmatorul(): FelEcran = entries[(ordinal + 1) % entries.size]

    companion object {
        fun dupaNume(nume: String?): FelEcran =
            entries.firstOrNull { it.name == nume } ?: BORD
    }
}
