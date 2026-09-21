package com.seongtae.vlogmaker.fonts

import com.seongtae.vlogmaker.model.SYSTEM_FONT

enum class Script(val label: String) { KO("한국어"), JA("日本語"), ZH("中文"), EN("English") }

/** id 는 Google Fonts 의 글꼴 가족 이름 그대로. 기기에서 필요할 때 내려받는다. */
data class FontDef(val id: String, val script: Script, val label: String = id)

object FontCatalog {
    val fonts: List<FontDef> = buildList {
        add(FontDef(SYSTEM_FONT, Script.KO, "기본"))
        // 한국어
        listOf(
            "Noto Sans KR", "Noto Serif KR", "Nanum Gothic", "Nanum Myeongjo", "Nanum Pen Script", "Nanum Brush Script",
            "Gowun Dodum", "Gowun Batang", "Gothic A1", "IBM Plex Sans KR", "Hahmlet", "Do Hyeon", "Black Han Sans", "Jua",
            "Sunflower", "Gaegu", "Hi Melody", "Poor Story", "Single Day", "Song Myung", "Stylish", "Yeon Sung", "Dokdo",
            "East Sea Dokdo", "Gamja Flower", "Gugi", "Kirang Haerang", "Cute Font", "Orbit", "Diphylleia", "Grandiflora One",
        ).forEach { add(FontDef(it, Script.KO)) }
        // 일본어
        listOf(
            "Noto Sans JP", "Noto Serif JP", "M PLUS 1p", "M PLUS Rounded 1c", "M PLUS 2", "Sawarabi Gothic", "Sawarabi Mincho",
            "Kosugi", "Kosugi Maru", "Zen Kaku Gothic New", "Zen Maru Gothic", "Zen Old Mincho", "Zen Antique", "Zen Kurenaido",
            "Shippori Mincho", "Shippori Antique", "Kaisei Decol", "Kaisei Opti", "Kaisei Tokumin", "Dela Gothic One",
            "Yusei Magic", "Hina Mincho", "Klee One", "Yuji Syuku", "Yuji Boku", "Yuji Mai", "Mochiy Pop One", "Reggae One",
            "RocknRoll One", "Stick", "Train One", "DotGothic16", "Hachi Maru Pop", "Potta One", "Rampart One", "Murecho",
            "BIZ UDPGothic", "BIZ UDPMincho", "Kiwi Maru", "New Tegomin", "Yomogi",
        ).forEach { add(FontDef(it, Script.JA)) }
        // 중국어 (간체·번체)
        listOf(
            "Noto Sans SC", "Noto Serif SC", "Noto Sans TC", "Noto Serif TC", "Noto Sans HK", "ZCOOL XiaoWei",
            "ZCOOL QingKe HuangYou", "ZCOOL KuaiLe", "Ma Shan Zheng", "Zhi Mang Xing", "Long Cang", "Liu Jian Mao Cao",
            "LXGW WenKai TC", "Chocolate Classical Sans", "Cactus Classical Serif",
        ).forEach { add(FontDef(it, Script.ZH)) }
        // 영문
        listOf(
            "Roboto", "Inter", "Montserrat", "Poppins", "Raleway", "Nunito", "Quicksand", "Comfortaa", "Fredoka", "Josefin Sans",
            "Space Grotesk", "Oswald", "Bebas Neue", "Anton", "Playfair Display", "Lora", "Cormorant Garamond", "DM Serif Display",
            "Abril Fatface", "Lobster", "Pacifico", "Dancing Script", "Caveat", "Great Vibes", "Satisfy", "Courgette",
            "Permanent Marker", "Shadows Into Light", "Amatic SC", "Righteous", "Special Elite", "Monoton", "Press Start 2P",
            "VT323", "Space Mono", "JetBrains Mono", "Courier Prime",
        ).forEach { add(FontDef(it, Script.EN)) }
    }

    fun byScript(script: Script): List<FontDef> = fonts.filter { it.script == script }

    fun find(id: String): FontDef? = fonts.find { it.id == id }
}
