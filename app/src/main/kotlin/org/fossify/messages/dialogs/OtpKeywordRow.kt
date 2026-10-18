package org.fossify.messages.dialogs

/** One line of the OTP keywords screen: a section header, a built in format, or an editable one. */
data class OtpKeywordRow(val text: String, val kind: Kind) {
    enum class Kind {
        HEADER,
        BUILT_IN,
        CUSTOM,
        ADD,
    }
}
