package app.olauncher.helper

import android.content.pm.ApplicationInfo
import app.olauncher.data.AppCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCategorizerTest {
    @Test
    fun popularAppsLandInUsefulCategories() {
        val examples = mapOf(
            "com.amazon.kindle Kindle" to AppCategory.MEDIA,
            "com.overdrive.mobile.android.libby Libby" to AppCategory.MEDIA,
            "com.audible.application Audible" to AppCategory.MEDIA,
            "com.google.android.apps.maps Google Maps" to AppCategory.TRAVEL,
            "com.spotify.music Spotify" to AppCategory.MEDIA,
            "com.google.android.youtube YouTube" to AppCategory.MEDIA,
            "com.nytimes.android NYTimes" to AppCategory.NEWS,
            "wsj.reader_sp WSJ" to AppCategory.NEWS,
            "com.economist.lamarr The Economist" to AppCategory.NEWS,
            "com.bbc.news BBC News" to AppCategory.NEWS,
            "flipboard.app Flipboard" to AppCategory.NEWS,
            "com.google.android.gm Gmail" to AppCategory.COMMUNICATION,
            "com.Slack Slack" to AppCategory.COMMUNICATION,
            "com.microsoft.teams Teams" to AppCategory.COMMUNICATION,
            "notion.id Notion" to AppCategory.PRODUCTIVITY,
            "com.strava Strava" to AppCategory.HEALTH,
            "com.whatsapp WhatsApp" to AppCategory.COMMUNICATION,
            "com.google.android.apps.photos Google Photos" to AppCategory.COMMUNICATION,
            "com.dd.doordash DoorDash" to AppCategory.SHOPPING,
            "com.openai.chatgpt ChatGPT" to AppCategory.AI_AGENTS,
            "com.anthropic.claude Claude" to AppCategory.AI_AGENTS,
            "com.google.android.apps.bard Gemini" to AppCategory.AI_AGENTS,
            "ai.perplexity.app.android Perplexity" to AppCategory.AI_AGENTS,
        )

        examples.forEach { (app, expected) ->
            assertEquals(app, expected, AppCategorizer.categoryFromText(app))
        }
    }

    @Test
    fun newsIsSeparateFromMedia() {
        assertEquals(
            AppCategory.NEWS,
            AppCategorizer.resolveCategory(
                null,
                "com.example.news",
                "Local News",
                ApplicationInfo.CATEGORY_NEWS,
            ),
        )
        assertEquals(
            AppCategory.MEDIA,
            AppCategorizer.resolveCategory(
                null,
                "com.example.stream",
                "Music Stream",
                ApplicationInfo.CATEGORY_AUDIO,
            ),
        )
        assertEquals(
            AppCategory.NEWS,
            AppCategorizer.categoryFromText("com.example.reader Wall Street Journal"),
        )
        assertEquals(
            AppCategory.MEDIA,
            AppCategorizer.categoryFromText("com.example.player Podcast Player"),
        )
    }

    @Test
    fun semanticEvidenceCorrectsMisleadingAndroidCategories() {
        val examples = listOf(
            CategoryExample(
                "example.camera",
                "Family Camera",
                ApplicationInfo.CATEGORY_IMAGE,
                AppCategory.COMMUNICATION,
            ),
            CategoryExample(
                "example.teams",
                "Teams",
                ApplicationInfo.CATEGORY_PRODUCTIVITY,
                AppCategory.COMMUNICATION,
            ),
            CategoryExample(
                "example.wallet",
                "Retirement Wallet",
                ApplicationInfo.CATEGORY_PRODUCTIVITY,
                AppCategory.FINANCE,
            ),
            CategoryExample(
                "example.theatres",
                "Local Theatres",
                ApplicationInfo.CATEGORY_UNDEFINED,
                AppCategory.TRAVEL,
            ),
            CategoryExample(
                "example.play",
                "Play Store",
                ApplicationInfo.CATEGORY_UNDEFINED,
                AppCategory.GAMES,
            ),
            CategoryExample(
                "example.files",
                "File Browser",
                ApplicationInfo.CATEGORY_PRODUCTIVITY,
                AppCategory.TOOLS,
            ),
        )

        examples.forEach { example ->
            assertEquals(
                example.label,
                example.expected,
                AppCategorizer.resolveCategory(
                    null,
                    example.packageName,
                    example.label,
                    example.declaredCategory,
                ),
            )
        }
    }

    @Test
    fun manualAndKnownPackageCategoriesBeatAndroidDefaults() {
        assertEquals(
            AppCategory.MEDIA,
            AppCategorizer.resolveCategory(
                null,
                "com.amazon.kindle",
                "Kindle",
                ApplicationInfo.CATEGORY_PRODUCTIVITY,
            ),
        )
        assertEquals(
            AppCategory.HEALTH,
            AppCategorizer.resolveCategory(
                AppCategory.HEALTH,
                "com.amazon.kindle",
                "Kindle",
                ApplicationInfo.CATEGORY_PRODUCTIVITY,
            ),
        )
        assertEquals(
            AppCategory.AI_AGENTS,
            AppCategorizer.resolveCategory(
                null,
                "ai.poe.app",
                "Poe",
                ApplicationInfo.CATEGORY_SOCIAL,
            ),
        )
        assertEquals(
            AppCategory.NEWS,
            AppCategorizer.resolveCategory(
                null,
                "com.nytimes.android",
                "NYTimes",
                ApplicationInfo.CATEGORY_NEWS,
            ),
        )
    }

    @Test
    fun multiCategoryOverridesArePreservedInOrder() {
        val overrides = listOf(AppCategory.NEWS, AppCategory.MEDIA, AppCategory.NEWS)
        assertEquals(
            listOf(AppCategory.NEWS, AppCategory.MEDIA),
            AppCategorizer.resolveCategories(
                overrides,
                "com.example.reader",
                "News Reader",
                ApplicationInfo.CATEGORY_UNDEFINED,
            ),
        )
    }

    @Test
    fun automaticPlacementStillReturnsASingleCategory() {
        assertEquals(
            listOf(AppCategory.MEDIA),
            AppCategorizer.resolveCategories(
                null,
                "com.spotify.music",
                "Spotify",
                ApplicationInfo.CATEGORY_AUDIO,
            ),
        )
    }

    @Test
    fun emptyOverridesFallBackToAutomatic() {
        assertEquals(
            listOf(AppCategory.AI_AGENTS),
            AppCategorizer.resolveCategories(
                emptyList(),
                "com.openai.chatgpt",
                "ChatGPT",
                ApplicationInfo.CATEGORY_UNDEFINED,
            ),
        )
    }

    private data class CategoryExample(
        val packageName: String,
        val label: String,
        val declaredCategory: Int,
        val expected: AppCategory,
    )

}
