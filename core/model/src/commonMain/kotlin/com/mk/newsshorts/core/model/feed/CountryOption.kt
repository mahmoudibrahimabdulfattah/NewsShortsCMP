package com.mk.newsshorts.core.model.feed

enum class CountryOption(
    val displayName: String,
    val code: String,
    val flag: String
) {
    UNITED_KINGDOM(displayName = "United Kingdom", code = "gb", flag = "🇬🇧"),
    EGYPT(displayName = "Egypt", code = "eg", flag = "🇪🇬"),
    SAUDI_ARABIA(displayName = "Saudi Arabia", code = "sa", flag = "🇸🇦"),
    UAE(displayName = "UAE", code = "ae", flag = "🇦🇪"),
    GERMANY(displayName = "Germany", code = "de", flag = "🇩🇪"),
    FRANCE(displayName = "France", code = "fr", flag = "🇫🇷"),
    INDIA(displayName = "India", code = "in", flag = "🇮🇳"),
    CHINA(displayName = "China", code = "cn", flag = "🇨🇳"),
    JAPAN(displayName = "Japan", code = "jp", flag = "🇯🇵"),
    AUSTRALIA(displayName = "Australia", code = "au", flag = "🇦🇺"),
    CANADA(displayName = "Canada", code = "ca", flag = "🇨🇦"),
    BRAZIL(displayName = "Brazil", code = "br", flag = "🇧🇷")
}
