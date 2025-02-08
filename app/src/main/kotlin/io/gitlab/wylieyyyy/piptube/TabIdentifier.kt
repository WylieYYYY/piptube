package io.gitlab.wylieyyyy.piptube

/**
 * Identifier for a tab, which also determines the tab title.
 * If the identifier is the same for two tabs, the newer tab will replace the old one.
 */
data class TabIdentifier private constructor(private val name: String, private val visibleType: TabType? = null) {
    /** Predefined identifiers that have fixed names. */
    companion object {
        /** Identifier for a page of related videos. */
        public val RELATED = TabIdentifier("~ Related")

        /** Identifier for a settings page tab displaying application settings. */
        public val SETTINGS = TabIdentifier("/ Settings")

        /** Identifier for a subscription page tab displaying subscription video listing. */
        public val SUBSCRIPTION = TabIdentifier("$ Subscription")
    }

    /**
     * Creates a identifier with the given type or name.
     * Identifiers are equal if these are equal.
     *
     * @param[type] Type of the tab which is prefixed to the name.
     * @param[name] Name or the message that describes the content of the tab.
     */
    public constructor(type: TabType, name: String) : this(name, type)

    /**
     * Tab types that are represented as an identifier prefix to a customizable string.
     * Tabs with the same type should have similar layout with variable content.
     *
     * @property[representation] String representation of the tab type, which is the identifier prefix.
     */
    public enum class TabType private constructor(public val representation: String) {
        /** Type for a channel page tab displaying information and video listing of a channel. */
        CHANNEL("@ "),
        /** Type for a search page tab displaying search results. */
        SEARCH("? "),
    }

    /** Composes the identifier by its type representation and name. */
    override fun toString() = "${visibleType?.representation ?: ""}$name"
}
