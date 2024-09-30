package io.gitlab.wylieyyyy.piptube

data class TabIdentifier private constructor(private val name: String, private val visibleType: TabType? = null) {
    companion object {
        public val RELATED = TabIdentifier("~ Related")
    }

    public constructor(type: TabType, name: String) : this(name, type)

    public enum class TabType(public val representation: String) {
        CHANNEL("@ "),
        SEARCH("? "),
    }

    override fun toString() = "${visibleType?.representation ?: ""}$name"
}
