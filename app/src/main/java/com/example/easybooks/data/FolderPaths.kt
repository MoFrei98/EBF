package com.example.easybooks.data

/**
 * Ordner werden als Pfade relativ zum Bibliotheksordner beschrieben:
 * "" ist die oberste Ebene, "Warhammer/Horus Heresy" ein Unterordner.
 */
object FolderPaths {
    const val ROOT = ""

    private val forbidden = Regex("""[\\/:*?"<>|]""")

    fun parent(path: String): String = path.substringBeforeLast('/', ROOT)

    fun name(path: String): String = path.substringAfterLast('/')

    fun child(parent: String, name: String): String = if (parent.isEmpty()) name else "$parent/$name"

    fun depth(path: String): Int = if (path.isEmpty()) 0 else path.count { it == '/' } + 1

    /** Liegt [path] in [ancestor] (oder ist es selbst)? Die oberste Ebene enthält alles. */
    fun isInside(path: String, ancestor: String): Boolean =
        ancestor.isEmpty() || path == ancestor || path.startsWith("$ancestor/")

    /** Direkte Unterordner von [parent], alphabetisch. */
    fun subfolders(all: Collection<String>, parent: String): List<String> =
        all.filter { it.isNotEmpty() && parent(it) == parent }.sortedBy { name(it).lowercase() }

    /** Alle Ordner in Baum-Reihenfolge: Eltern vor Kindern, Geschwister alphabetisch. */
    fun treeOrder(all: Collection<String>): List<String> =
        all.filter { it.isNotEmpty() }.sortedBy { it.lowercase().replace('/', '\u0000') }

    /** Macht aus einer Eingabe einen gültigen Ordnernamen (oder "" wenn nichts übrig bleibt). */
    fun cleanName(input: String): String =
        input.replace(forbidden, "_").trim().trim('.').trim().take(80)
}
