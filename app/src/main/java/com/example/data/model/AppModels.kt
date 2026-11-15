package com.example.data.model

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.example.R

enum class AppCategory(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val titleEn: String,
    val iconEmoji: String,
    val accentHex: Long,
    val iconResId: Int
) {
    GAMES(R.string.cat_games, R.string.cat_group_games, "Games", "🎮", 0xFFA855F7, R.drawable.ic_cat_games),
    APPS(R.string.cat_apps, R.string.cat_group_apps, "Apps", "🤖", 0xFF22C55E, R.drawable.ic_cat_apps),
    POPULAR(R.string.cat_popular, R.string.cat_group_general, "Most Popular", "🔥", 0xFFF97316, R.drawable.ic_cat_popular),
    NEW_GAMES(R.string.cat_new_games, R.string.cat_group_games, "New Games", "⭐", 0xFFEAB308, R.drawable.ic_cat_new_games),
    ENTERTAINMENT(R.string.cat_entertainment, R.string.cat_group_apps, "Entertainment", "▶️", 0xFFEC4899, R.drawable.ic_cat_entertainment),
    SPORTS(R.string.cat_sports, R.string.cat_group_apps, "Sports", "⚽", 0xFF38BDF8, R.drawable.ic_cat_sports),
    BUSINESS(R.string.cat_business, R.string.cat_group_apps, "Business", "💼", 0xFFF59E0B, R.drawable.ic_cat_business),
    EDUCATION(R.string.cat_education, R.string.cat_group_apps, "Education", "🎓", 0xFF6366F1, R.drawable.ic_cat_education),
    MUSIC(R.string.cat_music, R.string.cat_group_apps, "Music & Audio", "🎵", 0xFF06B6D4, R.drawable.ic_cat_music),
    TOOLS(R.string.cat_tools, R.string.cat_group_apps, "Tools", "🔧", 0xFF8B5CF6, R.drawable.ic_cat_tools),
    PRODUCTIVITY(R.string.cat_productivity, R.string.cat_group_apps, "Productivity", "📊", 0xFF10B981, R.drawable.ic_cat_productivity),
    PERSONALIZATION(R.string.cat_personalization, R.string.cat_group_apps, "Personalization", "🎨", 0xFFF43F5E, R.drawable.ic_cat_personalization),
    MEDIA(R.string.cat_media, R.string.cat_group_apps, "Media & Video", "🎬", 0xFFEC4899, R.drawable.ic_cat_media),
    SOCIAL(R.string.cat_social, R.string.cat_group_apps, "Social", "💬", 0xFF3B82F6, R.drawable.ic_cat_social),
    ACTION(R.string.cat_action, R.string.cat_group_games, "Action", "⚔️", 0xFFEF4444, R.drawable.ic_cat_action),
    RPG(R.string.cat_rpg, R.string.cat_group_games, "RPG", "🛡️", 0xFF8B5CF6, R.drawable.ic_cat_rpg),
    SIMULATION(R.string.cat_simulation, R.string.cat_group_games, "Simulation", "✈️", 0xFF06B6D4, R.drawable.ic_cat_simulation),
    EMULATORS(R.string.cat_emulators, R.string.cat_group_games, "Emulators", "🕹️", 0xFFD97706, R.drawable.ic_cat_emulators),
    RETRO(R.string.cat_retro, R.string.cat_group_games, "Retro & Classic", "👾", 0xFF6366F1, R.drawable.ic_cat_retro),
    ROOT_TOOLS(R.string.cat_root_tools, R.string.cat_group_apps, "Root Tools", "⚡", 0xFFDC2626, R.drawable.ic_cat_root_tools),
    OPEN_SOURCE(R.string.cat_open_source, R.string.cat_group_apps, "Open Source", "🔓", 0xFF3B82F6, R.drawable.ic_cat_open_source),
    VIDEO(R.string.cat_video, R.string.cat_group_apps, "Video", "📹", 0xFFEC4899, R.drawable.ic_cat_video),
    MORE(R.string.cat_more, R.string.cat_group_general, "More", "⊞", 0xFFA855F7, R.drawable.ic_cat_more)
}

@Immutable
data class CategoryItem(
    val key: String,
    @StringRes val titleRes: Int,
    val titleEn: String,
    val emoji: String,
    @StringRes val groupRes: Int,
    val iconResId: Int = R.drawable.ic_cat_apps,
    val titleAr: String = titleEn,
    val type: String = StoreCategories.ITEM_TYPE_APP,
    val isHidden: Boolean = false,
    val sortOrder: Int = 0,
    val isCustomName: Boolean = false
)

@Immutable
data class InstalledPackageSummary(
    val packageName: String,
    val versionCode: Long,
    val versionName: String
)

object StoreCategories {
    const val ITEM_TYPE_GAME = "game"
    const val ITEM_TYPE_APP = "app"

    private val GAME_KEY_SET = setOf(
        "games", "action", "rpg", "simulation", "emulators", "retro", "new_games", "mods",
        "game_action", "game_adventure", "game_arcade", "game_racing", "game_sports",
        "game_football", "game_shooter", "game_battle_royale", "game_survival",
        "game_strategy", "game_war", "game_puzzle", "game_board", "game_card",
        "game_casino", "game_simulation", "game_role_playing", "game_mmorpg",
        "game_fighting", "game_horror", "game_platformer", "game_open_world",
        "game_music", "game_educational", "game_trivia", "game_word", "game_casual",
        "game_idle", "game_tower_defense", "game_stealth", "game_sandbox",
        "game_fishing", "game_hunting", "game_cooking", "game_dress_up",
        "game_kids", "game_offline", "game_multiplayer"
    )

    private val GAME_CATEGORY_SET = setOf(
        AppCategory.GAMES, AppCategory.NEW_GAMES, AppCategory.ACTION,
        AppCategory.RPG, AppCategory.SIMULATION, AppCategory.EMULATORS, AppCategory.RETRO
    )

    val GENERAL = listOf(
        CategoryItem("all", R.string.cat_all, "All", "🌐", R.string.cat_group_general, R.drawable.ic_cat_more, "الكل", ITEM_TYPE_APP),
        CategoryItem("trending", R.string.cat_popular, "Trending", "🔥", R.string.cat_group_general, R.drawable.ic_cat_popular, "الأكثر شهرة", ITEM_TYPE_APP),
        CategoryItem("games", R.string.cat_games, "Games", "🎮", R.string.cat_group_general, R.drawable.ic_cat_games, "ألعاب", ITEM_TYPE_GAME)
    )

    // 40 Games Categories
    val GAMES = listOf(
        CategoryItem("game_action", R.string.cat_action, "Action", "⚔️", R.string.cat_group_games, R.drawable.ic_cat_action, "أكشن", ITEM_TYPE_GAME, sortOrder = 0),
        CategoryItem("game_adventure", R.string.cat_game_adventure, "Adventure", "🧭", R.string.cat_group_games, R.drawable.ic_cat_games, "مغامرة", ITEM_TYPE_GAME, sortOrder = 1),
        CategoryItem("game_arcade", R.string.cat_game_arcade, "Arcade", "🕹️", R.string.cat_group_games, R.drawable.ic_cat_games, "أركيد", ITEM_TYPE_GAME, sortOrder = 2),
        CategoryItem("game_racing", R.string.cat_game_racing, "Racing", "🏎️", R.string.cat_group_games, R.drawable.ic_cat_games, "سباق", ITEM_TYPE_GAME, sortOrder = 3),
        CategoryItem("game_sports", R.string.cat_sports, "Sports", "🏆", R.string.cat_group_games, R.drawable.ic_cat_sports, "رياضة", ITEM_TYPE_GAME, sortOrder = 4),
        CategoryItem("game_football", R.string.cat_game_football, "Football & Soccer", "⚽", R.string.cat_group_games, R.drawable.ic_cat_sports, "كرة القدم", ITEM_TYPE_GAME, sortOrder = 5),
        CategoryItem("game_shooter", R.string.cat_game_shooter, "Shooter", "🎯", R.string.cat_group_games, R.drawable.ic_cat_action, "إطلاق نار", ITEM_TYPE_GAME, sortOrder = 6),
        CategoryItem("game_battle_royale", R.string.cat_game_battle_royale, "Battle Royale", "🪂", R.string.cat_group_games, R.drawable.ic_cat_action, "باتل رويال", ITEM_TYPE_GAME, sortOrder = 7),
        CategoryItem("game_survival", R.string.cat_game_survival, "Survival", "🏕️", R.string.cat_group_games, R.drawable.ic_cat_games, "البقاء على قيد الحياة", ITEM_TYPE_GAME, sortOrder = 8),
        CategoryItem("game_strategy", R.string.cat_game_strategy, "Strategy", "🏰", R.string.cat_group_games, R.drawable.ic_cat_games, "استراتيجية", ITEM_TYPE_GAME, sortOrder = 9),
        CategoryItem("game_war", R.string.cat_game_war, "War & Military", "🪖", R.string.cat_group_games, R.drawable.ic_cat_action, "حرب ومعارك", ITEM_TYPE_GAME, sortOrder = 10),
        CategoryItem("game_puzzle", R.string.cat_game_puzzle, "Puzzle", "🧠", R.string.cat_group_games, R.drawable.ic_cat_games, "ألغاز", ITEM_TYPE_GAME, sortOrder = 11),
        CategoryItem("game_board", R.string.cat_game_board, "Board", "🎲", R.string.cat_group_games, R.drawable.ic_cat_games, "ألعاب لوحية", ITEM_TYPE_GAME, sortOrder = 12),
        CategoryItem("game_card", R.string.cat_game_card, "Card", "🃏", R.string.cat_group_games, R.drawable.ic_cat_games, "ورق", ITEM_TYPE_GAME, sortOrder = 13),
        CategoryItem("game_casino", R.string.cat_game_casino, "Casino", "🎰", R.string.cat_group_games, R.drawable.ic_cat_games, "كازينو", ITEM_TYPE_GAME, sortOrder = 14),
        CategoryItem("game_simulation", R.string.cat_simulation, "Simulation", "🛫", R.string.cat_group_games, R.drawable.ic_cat_simulation, "محاكاة", ITEM_TYPE_GAME, sortOrder = 15),
        CategoryItem("game_role_playing", R.string.cat_game_role_playing, "Role Playing", "🛡️", R.string.cat_group_games, R.drawable.ic_cat_rpg, "تقمص الأدوار", ITEM_TYPE_GAME, sortOrder = 16),
        CategoryItem("game_mmorpg", R.string.cat_game_mmorpg, "MMORPG", "🐉", R.string.cat_group_games, R.drawable.ic_cat_rpg, "تقمص أدوار جماعي", ITEM_TYPE_GAME, sortOrder = 17),
        CategoryItem("game_fighting", R.string.cat_game_fighting, "Fighting", "🥊", R.string.cat_group_games, R.drawable.ic_cat_action, "قتال", ITEM_TYPE_GAME, sortOrder = 18),
        CategoryItem("game_horror", R.string.cat_game_horror, "Horror", "👻", R.string.cat_group_games, R.drawable.ic_cat_games, "رعب", ITEM_TYPE_GAME, sortOrder = 19),
        CategoryItem("game_platformer", R.string.cat_game_platformer, "Platformer", "🍄", R.string.cat_group_games, R.drawable.ic_cat_games, "منصات", ITEM_TYPE_GAME, sortOrder = 20),
        CategoryItem("game_open_world", R.string.cat_game_open_world, "Open World", "🗺️", R.string.cat_group_games, R.drawable.ic_cat_games, "عالم مفتوح", ITEM_TYPE_GAME, sortOrder = 21),
        CategoryItem("game_music", R.string.cat_game_music, "Music Games", "🎼", R.string.cat_group_games, R.drawable.ic_cat_music, "ألعاب موسيقى", ITEM_TYPE_GAME, sortOrder = 22),
        CategoryItem("game_educational", R.string.cat_game_educational, "Educational", "🧩", R.string.cat_group_games, R.drawable.ic_cat_education, "تعليمية", ITEM_TYPE_GAME, sortOrder = 23),
        CategoryItem("game_trivia", R.string.cat_game_trivia, "Trivia", "❓", R.string.cat_group_games, R.drawable.ic_cat_games, "أسئلة ومعلومات", ITEM_TYPE_GAME, sortOrder = 24),
        CategoryItem("game_word", R.string.cat_game_word, "Word", "🔤", R.string.cat_group_games, R.drawable.ic_cat_games, "كلمات", ITEM_TYPE_GAME, sortOrder = 25),
        CategoryItem("game_casual", R.string.cat_game_casual, "Casual", "🎈", R.string.cat_group_games, R.drawable.ic_cat_games, "خفيفة", ITEM_TYPE_GAME, sortOrder = 26),
        CategoryItem("game_idle", R.string.cat_game_idle, "Idle & Clicker", "⏳", R.string.cat_group_games, R.drawable.ic_cat_games, "ألعاب خاملة ونقر", ITEM_TYPE_GAME, sortOrder = 27),
        CategoryItem("game_tower_defense", R.string.cat_game_tower_defense, "Tower Defense", "🗼", R.string.cat_group_games, R.drawable.ic_cat_games, "دفاع عن البرج", ITEM_TYPE_GAME, sortOrder = 28),
        CategoryItem("game_stealth", R.string.cat_game_stealth, "Stealth", "🥷", R.string.cat_group_games, R.drawable.ic_cat_games, "تسلل", ITEM_TYPE_GAME, sortOrder = 29),
        CategoryItem("game_sandbox", R.string.cat_game_sandbox, "Sandbox", "🧱", R.string.cat_group_games, R.drawable.ic_cat_games, "صندوق الرمل", ITEM_TYPE_GAME, sortOrder = 30),
        CategoryItem("game_fishing", R.string.cat_game_fishing, "Fishing", "🎣", R.string.cat_group_games, R.drawable.ic_cat_games, "صيد الأسماك", ITEM_TYPE_GAME, sortOrder = 31),
        CategoryItem("game_hunting", R.string.cat_game_hunting, "Hunting", "🏹", R.string.cat_group_games, R.drawable.ic_cat_games, "صيد", ITEM_TYPE_GAME, sortOrder = 32),
        CategoryItem("game_cooking", R.string.cat_game_cooking, "Cooking", "👨‍🍳", R.string.cat_group_games, R.drawable.ic_cat_games, "طبخ", ITEM_TYPE_GAME, sortOrder = 33),
        CategoryItem("game_dress_up", R.string.cat_game_dress_up, "Dress Up & Fashion", "👗", R.string.cat_group_games, R.drawable.ic_cat_games, "تلبيس وأزياء", ITEM_TYPE_GAME, sortOrder = 34),
        CategoryItem("game_kids", R.string.cat_game_kids, "Kids Games", "🧸", R.string.cat_group_games, R.drawable.ic_cat_games, "ألعاب أطفال", ITEM_TYPE_GAME, sortOrder = 35),
        CategoryItem("game_offline", R.string.cat_game_offline, "Offline Games", "📴", R.string.cat_group_games, R.drawable.ic_cat_games, "بدون إنترنت", ITEM_TYPE_GAME, sortOrder = 36),
        CategoryItem("game_multiplayer", R.string.cat_game_multiplayer, "Multiplayer", "👥", R.string.cat_group_games, R.drawable.ic_cat_games, "لعب جماعي", ITEM_TYPE_GAME, sortOrder = 37),
        CategoryItem("emulators", R.string.cat_emulators, "Emulators", "🕹️", R.string.cat_group_games, R.drawable.ic_cat_emulators, "محاكيات", ITEM_TYPE_GAME, sortOrder = 38),
        CategoryItem("mods", R.string.cat_mods, "Game Mods", "🧬", R.string.cat_group_games, R.drawable.ic_cat_games, "تعديلات الألعاب", ITEM_TYPE_GAME, sortOrder = 39)
    )

    // 54 Apps Categories
    val APPS = listOf(
        CategoryItem("social", R.string.cat_social, "Social", "👥", R.string.cat_group_apps, R.drawable.ic_cat_social, "التواصل الاجتماعي", ITEM_TYPE_APP, sortOrder = 0),
        CategoryItem("communication", R.string.cat_communication, "Communication", "📞", R.string.cat_group_apps, R.drawable.ic_cat_social, "الاتصال والمحادثة", ITEM_TYPE_APP, sortOrder = 1),
        CategoryItem("video_players_editors", R.string.cat_video, "Video Players & Editors", "🎬", R.string.cat_group_apps, R.drawable.ic_cat_video, "مشغلات ومحررات الفيديو", ITEM_TYPE_APP, sortOrder = 2),
        CategoryItem("photography", R.string.cat_photography, "Photography", "📷", R.string.cat_group_apps, R.drawable.ic_cat_apps, "التصوير الفوتوغرافي", ITEM_TYPE_APP, sortOrder = 3),
        CategoryItem("photo_editors", R.string.cat_photo_editors, "Photo Editors", "🖼️", R.string.cat_group_apps, R.drawable.ic_cat_apps, "محررات الصور", ITEM_TYPE_APP, sortOrder = 4),
        CategoryItem("music_audio", R.string.cat_music, "Music & Audio", "🎵", R.string.cat_group_apps, R.drawable.ic_cat_music, "الموسيقى والصوت", ITEM_TYPE_APP, sortOrder = 5),
        CategoryItem("browsers", R.string.cat_browsers, "Web Browsers", "🌐", R.string.cat_group_apps, R.drawable.ic_cat_apps, "متصفحات الويب", ITEM_TYPE_APP, sortOrder = 6),
        CategoryItem("screen_recorders", R.string.cat_screen_recorders, "Screen Recorders", "🎥", R.string.cat_group_apps, R.drawable.ic_cat_video, "مسجلات الشاشة", ITEM_TYPE_APP, sortOrder = 7),
        CategoryItem("wallpapers", R.string.cat_wallpapers, "Wallpapers", "🏞️", R.string.cat_group_apps, R.drawable.ic_cat_personalization, "خلفيات الشاشة", ITEM_TYPE_APP, sortOrder = 8),
        CategoryItem("themes_launchers", R.string.cat_themes_launchers, "Themes & Launchers", "🪄", R.string.cat_group_apps, R.drawable.ic_cat_personalization, "السمات واللانشرات", ITEM_TYPE_APP, sortOrder = 9),
        CategoryItem("icon_packs", R.string.cat_icon_packs, "Icon Packs", "💠", R.string.cat_group_apps, R.drawable.ic_cat_personalization, "حزم الأيقونات", ITEM_TYPE_APP, sortOrder = 10),
        CategoryItem("keyboards", R.string.cat_keyboards, "Keyboards", "⌨️", R.string.cat_group_apps, R.drawable.ic_cat_tools, "لوحات المفاتيح", ITEM_TYPE_APP, sortOrder = 11),
        CategoryItem("fonts", R.string.cat_fonts, "Fonts", "🔤", R.string.cat_group_apps, R.drawable.ic_cat_personalization, "الخطوط", ITEM_TYPE_APP, sortOrder = 12),
        CategoryItem("tools", R.string.cat_tools, "Tools", "🔧", R.string.cat_group_apps, R.drawable.ic_cat_tools, "الأدوات", ITEM_TYPE_APP, sortOrder = 13),
        CategoryItem("file_managers", R.string.cat_file_managers, "File Managers", "📁", R.string.cat_group_apps, R.drawable.ic_cat_tools, "مديرو الملفات", ITEM_TYPE_APP, sortOrder = 14),
        CategoryItem("cleaners_boosters", R.string.cat_cleaners_boosters, "Cleaners & Boosters", "🧹", R.string.cat_group_apps, R.drawable.ic_cat_tools, "التنظيف وتسريع الجهاز", ITEM_TYPE_APP, sortOrder = 15),
        CategoryItem("security_antivirus", R.string.cat_security_antivirus, "Security & Antivirus", "🛡️", R.string.cat_group_apps, R.drawable.ic_cat_tools, "الحماية ومكافحة الفيروسات", ITEM_TYPE_APP, sortOrder = 16),
        CategoryItem("vpn", R.string.cat_vpn, "VPN & Privacy", "🔒", R.string.cat_group_apps, R.drawable.ic_cat_tools, "VPN والخصوصية", ITEM_TYPE_APP, sortOrder = 17),
        CategoryItem("battery_savers", R.string.cat_battery_savers, "Battery Savers", "🔋", R.string.cat_group_apps, R.drawable.ic_cat_tools, "توفير البطارية", ITEM_TYPE_APP, sortOrder = 18),
        CategoryItem("productivity", R.string.cat_productivity, "Productivity", "📊", R.string.cat_group_apps, R.drawable.ic_cat_productivity, "الإنتاجية", ITEM_TYPE_APP, sortOrder = 19),
        CategoryItem("office_documents", R.string.cat_office_documents, "Office & Documents", "📄", R.string.cat_group_apps, R.drawable.ic_cat_productivity, "المكتب والمستندات", ITEM_TYPE_APP, sortOrder = 20),
        CategoryItem("notes", R.string.cat_notes, "Notes & Lists", "📝", R.string.cat_group_apps, R.drawable.ic_cat_productivity, "الملاحظات والقوائم", ITEM_TYPE_APP, sortOrder = 21),
        CategoryItem("calendar", R.string.cat_calendar, "Calendar & Planner", "📅", R.string.cat_group_apps, R.drawable.ic_cat_productivity, "التقويم والمهام", ITEM_TYPE_APP, sortOrder = 22),
        CategoryItem("education", R.string.cat_education, "Education", "🎓", R.string.cat_group_apps, R.drawable.ic_cat_education, "التعليم", ITEM_TYPE_APP, sortOrder = 23),
        CategoryItem("languages_translators", R.string.cat_languages_translators, "Languages & Translators", "🗣️", R.string.cat_group_apps, R.drawable.ic_cat_education, "اللغات والترجمة", ITEM_TYPE_APP, sortOrder = 24),
        CategoryItem("books_reference", R.string.cat_books_reference, "Books & Reference", "📚", R.string.cat_group_apps, R.drawable.ic_cat_education, "الكتب والمراجع", ITEM_TYPE_APP, sortOrder = 25),
        CategoryItem("news_magazines", R.string.cat_news_magazines, "News & Magazines", "📰", R.string.cat_group_apps, R.drawable.ic_cat_apps, "الأخبار والمجلات", ITEM_TYPE_APP, sortOrder = 26),
        CategoryItem("weather", R.string.cat_weather, "Weather", "🌤️", R.string.cat_group_apps, R.drawable.ic_cat_apps, "الطقس", ITEM_TYPE_APP, sortOrder = 27),
        CategoryItem("maps_navigation", R.string.cat_maps_navigation, "Maps & Navigation", "🗺️", R.string.cat_group_apps, R.drawable.ic_cat_apps, "الخرائط والملاحة", ITEM_TYPE_APP, sortOrder = 28),
        CategoryItem("travel_local", R.string.cat_travel_local, "Travel & Local", "✈️", R.string.cat_group_apps, R.drawable.ic_cat_apps, "السفر والأماكن", ITEM_TYPE_APP, sortOrder = 29),
        CategoryItem("shopping", R.string.cat_shopping, "Shopping", "🛍️", R.string.cat_group_apps, R.drawable.ic_cat_apps, "التسوق", ITEM_TYPE_APP, sortOrder = 30),
        CategoryItem("finance", R.string.cat_finance, "Finance", "💰", R.string.cat_group_apps, R.drawable.ic_cat_business, "المالية", ITEM_TYPE_APP, sortOrder = 31),
        CategoryItem("business", R.string.cat_business, "Business", "💼", R.string.cat_group_apps, R.drawable.ic_cat_business, "الأعمال", ITEM_TYPE_APP, sortOrder = 32),
        CategoryItem("health_fitness", R.string.cat_health_fitness, "Health & Fitness", "🏃", R.string.cat_group_apps, R.drawable.ic_cat_apps, "الصحة واللياقة", ITEM_TYPE_APP, sortOrder = 33),
        CategoryItem("medical", R.string.cat_medical, "Medical", "🩺", R.string.cat_group_apps, R.drawable.ic_cat_apps, "الطب", ITEM_TYPE_APP, sortOrder = 34),
        CategoryItem("food_drink", R.string.cat_food_drink, "Food & Drink", "🍔", R.string.cat_group_apps, R.drawable.ic_cat_apps, "الطعام والشراب", ITEM_TYPE_APP, sortOrder = 35),
        CategoryItem("entertainment", R.string.cat_entertainment, "Entertainment", "▶️", R.string.cat_group_apps, R.drawable.ic_cat_entertainment, "الترفيه", ITEM_TYPE_APP, sortOrder = 36),
        CategoryItem("live_tv_streaming", R.string.cat_live_tv_streaming, "Live TV & Streaming", "📺", R.string.cat_group_apps, R.drawable.ic_cat_entertainment, "البث المباشر والتلفاز", ITEM_TYPE_APP, sortOrder = 37),
        CategoryItem("sports", R.string.cat_sports, "Sports", "⚽", R.string.cat_group_apps, R.drawable.ic_cat_sports, "الرياضة", ITEM_TYPE_APP, sortOrder = 38),
        CategoryItem("lifestyle", R.string.cat_lifestyle, "Lifestyle", "✨", R.string.cat_group_apps, R.drawable.ic_cat_apps, "نمط الحياة", ITEM_TYPE_APP, sortOrder = 39),
        CategoryItem("personalization", R.string.cat_personalization, "Personalization", "🎭", R.string.cat_group_apps, R.drawable.ic_cat_personalization, "التخصيص", ITEM_TYPE_APP, sortOrder = 40),
        CategoryItem("auto_vehicles", R.string.cat_auto_vehicles, "Auto & Vehicles", "🚗", R.string.cat_group_apps, R.drawable.ic_cat_apps, "السيارات والمركبات", ITEM_TYPE_APP, sortOrder = 41),
        CategoryItem("parenting", R.string.cat_parenting, "Parenting", "🍼", R.string.cat_group_apps, R.drawable.ic_cat_apps, "الأمومة والطفولة", ITEM_TYPE_APP, sortOrder = 42),
        CategoryItem("dating", R.string.cat_dating, "Dating", "❤️", R.string.cat_group_apps, R.drawable.ic_cat_apps, "التعارف", ITEM_TYPE_APP, sortOrder = 43),
        CategoryItem("art_design", R.string.cat_art_design, "Art & Design", "🎨", R.string.cat_group_apps, R.drawable.ic_cat_personalization, "الفن والتصميم", ITEM_TYPE_APP, sortOrder = 44),
        CategoryItem("developer_tools", R.string.cat_developer_tools, "Developer Tools", "💻", R.string.cat_group_apps, R.drawable.ic_cat_open_source, "أدوات المطورين", ITEM_TYPE_APP, sortOrder = 45),
        CategoryItem("widgets", R.string.cat_widgets, "Widgets", "🧩", R.string.cat_group_apps, R.drawable.ic_cat_personalization, "الويدجت", ITEM_TYPE_APP, sortOrder = 46),
        CategoryItem("qr_scanners", R.string.cat_qr_scanners, "QR & Barcode Scanners", "🏁", R.string.cat_group_apps, R.drawable.ic_cat_tools, "ماسح QR والباركود", ITEM_TYPE_APP, sortOrder = 47),
        CategoryItem("pdf_tools", R.string.cat_pdf_tools, "PDF Tools", "📑", R.string.cat_group_apps, R.drawable.ic_cat_productivity, "أدوات PDF", ITEM_TYPE_APP, sortOrder = 48),
        CategoryItem("downloaders", R.string.cat_downloaders, "Download Managers", "⬇️", R.string.cat_group_apps, R.drawable.ic_cat_tools, "مديرو التحميل", ITEM_TYPE_APP, sortOrder = 49),
        CategoryItem("gallery", R.string.cat_gallery, "Gallery & Albums", "🖼️", R.string.cat_group_apps, R.drawable.ic_cat_apps, "المعرض والألبومات", ITEM_TYPE_APP, sortOrder = 50),
        CategoryItem("clock_alarm", R.string.cat_clock_alarm, "Clock & Alarms", "⏰", R.string.cat_group_apps, R.drawable.ic_cat_tools, "الساعة والمنبه", ITEM_TYPE_APP, sortOrder = 51),
        CategoryItem("calculators", R.string.cat_calculators, "Calculators", "🧮", R.string.cat_group_apps, R.drawable.ic_cat_tools, "الآلات الحاسبة", ITEM_TYPE_APP, sortOrder = 52),
        CategoryItem("social_media_tools", R.string.cat_social_media_tools, "Social Media Tools", "📣", R.string.cat_group_apps, R.drawable.ic_cat_social, "أدوات التواصل الاجتماعي", ITEM_TYPE_APP, sortOrder = 53)
    )

    // Legacy compatibility keys so older Supabase rows stay discoverable under Apps without colliding with the 54 core categories
    val EXTRA_NON_GAME_CATEGORIES = listOf(
        CategoryItem("root_tools", R.string.cat_root_tools, "Root Tools", "⚡", R.string.cat_group_apps, R.drawable.ic_cat_root_tools, "أدوات الروت", ITEM_TYPE_APP, sortOrder = 54),
        CategoryItem("open_source", R.string.cat_open_source, "Open Source", "🔓", R.string.cat_group_apps, R.drawable.ic_cat_open_source, "مفتوح المصدر", ITEM_TYPE_APP, sortOrder = 55)
    )

    val APPS_HUB_CATEGORIES: List<CategoryItem> = APPS + EXTRA_NON_GAME_CATEGORIES
    val GAMES_HUB_CATEGORIES: List<CategoryItem> = GAMES

    val ALL_SELECTIONS: List<CategoryItem> = APPS + EXTRA_NON_GAME_CATEGORIES + GAMES
    val ALL_FILTER_ITEMS: List<CategoryItem> = GENERAL + APPS + EXTRA_NON_GAME_CATEGORIES + GAMES

    private val RES_NAME_MAP: Map<String, Int> = mapOf(
        "cat_action" to R.string.cat_action,
        "cat_game_adventure" to R.string.cat_game_adventure,
        "cat_game_arcade" to R.string.cat_game_arcade,
        "cat_game_racing" to R.string.cat_game_racing,
        "cat_sports" to R.string.cat_sports,
        "cat_game_football" to R.string.cat_game_football,
        "cat_game_shooter" to R.string.cat_game_shooter,
        "cat_game_battle_royale" to R.string.cat_game_battle_royale,
        "cat_game_survival" to R.string.cat_game_survival,
        "cat_game_strategy" to R.string.cat_game_strategy,
        "cat_game_war" to R.string.cat_game_war,
        "cat_game_puzzle" to R.string.cat_game_puzzle,
        "cat_game_board" to R.string.cat_game_board,
        "cat_game_card" to R.string.cat_game_card,
        "cat_game_casino" to R.string.cat_game_casino,
        "cat_simulation" to R.string.cat_simulation,
        "cat_game_role_playing" to R.string.cat_game_role_playing,
        "cat_game_mmorpg" to R.string.cat_game_mmorpg,
        "cat_game_fighting" to R.string.cat_game_fighting,
        "cat_game_horror" to R.string.cat_game_horror,
        "cat_game_platformer" to R.string.cat_game_platformer,
        "cat_game_open_world" to R.string.cat_game_open_world,
        "cat_game_music" to R.string.cat_game_music,
        "cat_game_educational" to R.string.cat_game_educational,
        "cat_game_trivia" to R.string.cat_game_trivia,
        "cat_game_word" to R.string.cat_game_word,
        "cat_game_casual" to R.string.cat_game_casual,
        "cat_game_idle" to R.string.cat_game_idle,
        "cat_game_tower_defense" to R.string.cat_game_tower_defense,
        "cat_game_stealth" to R.string.cat_game_stealth,
        "cat_game_sandbox" to R.string.cat_game_sandbox,
        "cat_game_fishing" to R.string.cat_game_fishing,
        "cat_game_hunting" to R.string.cat_game_hunting,
        "cat_game_cooking" to R.string.cat_game_cooking,
        "cat_game_dress_up" to R.string.cat_game_dress_up,
        "cat_game_kids" to R.string.cat_game_kids,
        "cat_game_offline" to R.string.cat_game_offline,
        "cat_game_multiplayer" to R.string.cat_game_multiplayer,
        "cat_emulators" to R.string.cat_emulators,
        "cat_mods" to R.string.cat_mods,
        "cat_social" to R.string.cat_social,
        "cat_communication" to R.string.cat_communication,
        "cat_video" to R.string.cat_video,
        "cat_photography" to R.string.cat_photography,
        "cat_photo_editors" to R.string.cat_photo_editors,
        "cat_music" to R.string.cat_music,
        "cat_browsers" to R.string.cat_browsers,
        "cat_screen_recorders" to R.string.cat_screen_recorders,
        "cat_wallpapers" to R.string.cat_wallpapers,
        "cat_themes_launchers" to R.string.cat_themes_launchers,
        "cat_icon_packs" to R.string.cat_icon_packs,
        "cat_keyboards" to R.string.cat_keyboards,
        "cat_fonts" to R.string.cat_fonts,
        "cat_tools" to R.string.cat_tools,
        "cat_file_managers" to R.string.cat_file_managers,
        "cat_cleaners_boosters" to R.string.cat_cleaners_boosters,
        "cat_security_antivirus" to R.string.cat_security_antivirus,
        "cat_vpn" to R.string.cat_vpn,
        "cat_battery_savers" to R.string.cat_battery_savers,
        "cat_productivity" to R.string.cat_productivity,
        "cat_office_documents" to R.string.cat_office_documents,
        "cat_notes" to R.string.cat_notes,
        "cat_calendar" to R.string.cat_calendar,
        "cat_education" to R.string.cat_education,
        "cat_languages_translators" to R.string.cat_languages_translators,
        "cat_books_reference" to R.string.cat_books_reference,
        "cat_news_magazines" to R.string.cat_news_magazines,
        "cat_weather" to R.string.cat_weather,
        "cat_maps_navigation" to R.string.cat_maps_navigation,
        "cat_travel_local" to R.string.cat_travel_local,
        "cat_shopping" to R.string.cat_shopping,
        "cat_finance" to R.string.cat_finance,
        "cat_business" to R.string.cat_business,
        "cat_health_fitness" to R.string.cat_health_fitness,
        "cat_medical" to R.string.cat_medical,
        "cat_food_drink" to R.string.cat_food_drink,
        "cat_entertainment" to R.string.cat_entertainment,
        "cat_live_tv_streaming" to R.string.cat_live_tv_streaming,
        "cat_lifestyle" to R.string.cat_lifestyle,
        "cat_personalization" to R.string.cat_personalization,
        "cat_auto_vehicles" to R.string.cat_auto_vehicles,
        "cat_parenting" to R.string.cat_parenting,
        "cat_dating" to R.string.cat_dating,
        "cat_art_design" to R.string.cat_art_design,
        "cat_developer_tools" to R.string.cat_developer_tools,
        "cat_widgets" to R.string.cat_widgets,
        "cat_qr_scanners" to R.string.cat_qr_scanners,
        "cat_pdf_tools" to R.string.cat_pdf_tools,
        "cat_downloaders" to R.string.cat_downloaders,
        "cat_gallery" to R.string.cat_gallery,
        "cat_clock_alarm" to R.string.cat_clock_alarm,
        "cat_calculators" to R.string.cat_calculators,
        "cat_social_media_tools" to R.string.cat_social_media_tools,
        "cat_root_tools" to R.string.cat_root_tools,
        "cat_open_source" to R.string.cat_open_source
    )

    private val LEGACY_RES_MAP = mapOf(
        "music" to R.string.cat_music,
        "video" to R.string.cat_video,
        "root_tools" to R.string.cat_root_tools,
        "open_source" to R.string.cat_open_source,
        "new_games" to R.string.cat_new_games,
        "popular" to R.string.cat_popular,
        "tools" to R.string.cat_tools,
        "games" to R.string.cat_games,
        "apps" to R.string.cat_apps,
        "sports" to R.string.cat_sports,
        "education" to R.string.cat_education,
        "entertainment" to R.string.cat_entertainment,
        "business" to R.string.cat_business,
        "media" to R.string.cat_media,
        "action" to R.string.cat_action,
        "rpg" to R.string.cat_rpg,
        "simulation" to R.string.cat_simulation,
        "emulators" to R.string.cat_emulators,
        "retro" to R.string.cat_retro,
        "more" to R.string.cat_more,
        "beauty" to R.string.cat_beauty,
        "comics" to R.string.cat_comics,
        "events" to R.string.cat_events,
        "house_home" to R.string.cat_house_home,
        "libraries_demo" to R.string.cat_libraries_demo
    )

    fun normalizeItemType(raw: String?): String {
        val clean = raw?.trim()?.lowercase().orEmpty()
        return if (clean == ITEM_TYPE_GAME || clean == "games") ITEM_TYPE_GAME else ITEM_TYPE_APP
    }

    fun inferItemType(categoryKey: String?, explicitType: String? = null, category: AppCategory? = null): String {
        val rawType = explicitType?.trim()?.lowercase().orEmpty()
        if (rawType == ITEM_TYPE_GAME || rawType == "games") return ITEM_TYPE_GAME
        if (rawType == ITEM_TYPE_APP || rawType == "apps") return ITEM_TYPE_APP
        return if (isGameCategory(categoryKey, category)) ITEM_TYPE_GAME else ITEM_TYPE_APP
    }

    fun isGameCategory(key: String?, category: AppCategory? = null): Boolean {
        val clean = key?.trim()?.lowercase().orEmpty()
        if (clean in GAME_KEY_SET || clean.startsWith("game_")) return true
        if (clean.isNotEmpty() && clean != "all" && clean != "trending" && clean != "popular" && clean != "more") {
            return false
        }
        return category != null && category in GAME_CATEGORY_SET
    }

    fun isGameApp(app: AppItem): Boolean {
        return app.itemType.equals(ITEM_TYPE_GAME, ignoreCase = true) ||
            (app.itemType.isBlank() && isGameCategory(app.categoryKey, app.category))
    }

    fun isNonGameApp(app: AppItem): Boolean = !isGameApp(app)

    fun matchesCategoryFilter(
        app: AppItem,
        filterKey: String,
        requiredType: String? = null
    ): Boolean {
        if (!requiredType.isNullOrBlank()) {
            val expected = normalizeItemType(requiredType)
            val actual = if (isGameApp(app)) ITEM_TYPE_GAME else ITEM_TYPE_APP
            if (actual != expected) return false
        }
        val cleanFilter = filterKey.trim().lowercase()
        val appKey = app.categoryKey.trim().lowercase()
        return when (cleanFilter) {
            "all", "" -> true
            "games" -> isGameApp(app)
            "apps" -> isNonGameApp(app)
            "trending", "popular" -> app.isTrending || appKey == "trending" || appKey == "popular"
            "open_source" -> app.isOpenSource || appKey == "open_source" || app.category == AppCategory.OPEN_SOURCE
            "root_tools" -> appKey == "root_tools" || app.category == AppCategory.ROOT_TOOLS
            "music_audio", "music" -> appKey == "music_audio" || appKey == "music" || app.category == AppCategory.MUSIC
            "video_players_editors", "video", "media" -> appKey in setOf("video_players_editors", "video", "media") || app.category == AppCategory.VIDEO || app.category == AppCategory.MEDIA
            "communication" -> appKey == "communication"
            "social" -> appKey == "social" || app.category == AppCategory.SOCIAL
            "game_action", "action" -> isGameApp(app) && (appKey == "game_action" || appKey == "action" || app.category == AppCategory.ACTION)
            "game_role_playing", "rpg" -> isGameApp(app) && (appKey == "game_role_playing" || appKey == "rpg" || app.category == AppCategory.RPG)
            "game_simulation", "simulation" -> isGameApp(app) && (appKey == "game_simulation" || appKey == "simulation" || app.category == AppCategory.SIMULATION)
            "game_sports" -> isGameApp(app) && (appKey == "game_sports" || appKey == "sports" || app.category == AppCategory.SPORTS)
            "sports" -> isNonGameApp(app) && (appKey == "sports" || app.category == AppCategory.SPORTS)
            else -> appKey == cleanFilter || app.category.name.lowercase() == cleanFilter
        }
    }

    fun getTitleResByName(resName: String?): Int? {
        if (resName.isNullOrBlank()) return null
        return RES_NAME_MAP[resName.trim()]
    }

    @StringRes
    fun getTitleRes(key: String?): Int {
        if (key.isNullOrBlank()) return R.string.cat_apps
        val clean = key.trim().lowercase()
        val match = ALL_FILTER_ITEMS.find { it.key.lowercase() == clean }
        if (match != null) return match.titleRes
        return LEGACY_RES_MAP[clean] ?: R.string.cat_apps
    }

    fun getTitle(context: Context, key: String?): String {
        return context.getString(getTitleRes(key))
    }

    fun getEmoji(key: String?): String {
        if (key.isNullOrBlank()) return "📱"
        val clean = key.trim().lowercase()
        return ALL_FILTER_ITEMS.find { it.key.lowercase() == clean }?.emoji ?: "📱"
    }

    fun find(key: String?, typeHint: String? = null): CategoryItem {
        val clean = key?.trim()?.lowercase().orEmpty()
        val exact = ALL_FILTER_ITEMS.find {
            it.key.lowercase() == clean && (typeHint.isNullOrBlank() || it.type == normalizeItemType(typeHint))
        } ?: ALL_FILTER_ITEMS.find { it.key.lowercase() == clean }
        if (exact != null) return exact
        return CategoryItem(
            key = clean.ifBlank { "tools" },
            titleRes = getTitleRes(clean),
            titleEn = if (clean == "apps") "Apps" else clean.ifBlank { "Tools" },
            emoji = getEmoji(clean),
            groupRes = if (isGameCategory(clean)) R.string.cat_group_games else R.string.cat_group_apps,
            type = if (typeHint != null) normalizeItemType(typeHint) else if (isGameCategory(clean)) ITEM_TYPE_GAME else ITEM_TYPE_APP
        )
    }

    fun titleResNameFor(key: String): String {
        val clean = key.trim().lowercase()
        return when (clean) {
            "game_action" -> "cat_action"
            "game_sports" -> "cat_sports"
            "game_simulation" -> "cat_simulation"
            "video_players_editors" -> "cat_video"
            "music_audio" -> "cat_music"
            else -> {
                val candidate = "cat_$clean"
                if (RES_NAME_MAP.containsKey(candidate)) candidate else ""
            }
        }
    }
}

@Immutable
data class StoreUpdateInfo(
    val id: Int = 1,
    val versionCode: Int = 1,
    val versionName: String = "1.0.0",
    val notes: String = "",
    val apkUrl: String = "",
    val updatedAt: String? = null
)

@Immutable
data class AppItem(
    val id: String,
    val name: String,
    val nameAr: String = name,
    val category: AppCategory,
    val tags: List<String>,
    val rating: Float,
    val ratingCount: String,
    val downloadCount: String,
    val size: String,
    val sizeBytes: Long,
    val iconEmoji: String,
    val accentColor: Long,
    val bannerRes: Int? = null,
    val descriptionAr: String,
    val descriptionEn: String,
    val featuresAr: List<String>,
    val screenshotsColors: List<Long>,
    val isFeatured: Boolean = false,
    val isTrending: Boolean = false,
    val isNew: Boolean = false,
    val iconUrl: String? = null,
    val fileUrl: String? = null,
    val screenshotUrls: List<String> = emptyList(),
    val isOpenSource: Boolean = false,
    val sourceCodeUrl: String? = null,
    val supportedAbis: String = "Universal (All Architectures)",
    val packageName: String? = null,
    val version: String = "1.0.0",
    val categoryKey: String = "tools",
    val changelog: String? = null,
    val versionCode: Long = 1L,
    val updatedAtMillis: Long = 0L,
    val createdAtMillis: Long = 0L,
    val downloadCountLong: Long = 0L,
    val itemType: String = StoreCategories.inferItemType(categoryKey, null, category),
    val sha256: String? = null,
    val splitApkUrls: List<String> = emptyList(),
    val obbUrl: String? = null
) {
    val isGame: Boolean
        get() = StoreCategories.isGameApp(this)

    @Composable
    fun localizedCategoryTitle(): String {
        val key = categoryKey.ifBlank { category.name.lowercase() }
        return stringResource(id = StoreCategories.getTitleRes(key))
    }

    val displayCategoryTitle: String
        get() = StoreCategories.find(categoryKey.ifBlank { category.name.lowercase() }, itemType).titleEn

    val displayVersionBadge: String
        get() = if (version.isNotBlank()) "v$version" else "v1.0.0"
}

enum class DownloadStatus {
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED,
    PAUSED
}

@Immutable
data class DownloadItem(
    val appId: String,
    val appName: String,
    val appIcon: String,
    val accentColor: Long,
    val progress: Float,
    val speed: String,
    val downloadedSize: String,
    val totalSize: String,
    val isCompleted: Boolean,
    val isPaused: Boolean = false,
    val status: DownloadStatus = if (isCompleted) DownloadStatus.COMPLETED else (if (isPaused) DownloadStatus.PAUSED else DownloadStatus.DOWNLOADING),
    val apkFilePath: String? = null,
    val errorMessage: String? = null
)

@Immutable
data class ReviewItem(
    val id: String,
    val userName: String,
    val userAvatar: String,
    val rating: Int,
    val date: String,
    val comment: String
)
