package app.olauncher.helper

import android.content.pm.ApplicationInfo
import app.olauncher.data.AppCategory
import app.olauncher.data.AppRoutine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class AppCategorizerTest {
    @Test
    fun popularAppsLandInUsefulCategories() {
        val examples = mapOf(
            "com.amazon.kindle Kindle" to AppCategory.MEDIA,
            "com.overdrive.mobile.android.libby Libby" to AppCategory.MEDIA,
            "com.audible.application Audible" to AppCategory.MEDIA,
            "com.google.android.apps.maps Google Maps" to AppCategory.TRAVEL,
            "com.spotify.music Spotify" to AppCategory.MEDIA,
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
    fun eachRoutinePutsTheExpectedCategoryFirst() {
        val expected = mapOf(
            AppRoutine.READING to AppCategory.MEDIA,
            AppRoutine.COMMUTE to AppCategory.TRAVEL,
            AppRoutine.WORK to AppCategory.PRODUCTIVITY,
            AppRoutine.FITNESS to AppCategory.HEALTH,
            AppRoutine.FAMILY to AppCategory.COMMUNICATION,
            AppRoutine.EVENING to AppCategory.MEDIA,
            AppRoutine.WEEKEND to AppCategory.HEALTH,
            AppRoutine.VACATION to AppCategory.TRAVEL,
        )

        expected.forEach { (routine, category) ->
            assertEquals(routine.name, category, AppCategorizer.categoryOrder(routine).first())
        }
    }

    @Test
    fun pinnedAiAgentsAlwaysComeFirst() {
        AppRoutine.entries.forEach { routine ->
            assertEquals(
                routine.name,
                AppCategory.AI_AGENTS,
                AppCategorizer.categoryOrder(routine, AppCategory.AI_AGENTS).first(),
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
    }

    @Test
    fun fitnessDoesNotOverrideALaterRoutine() {
        assertEquals(
            AppRoutine.EVENING,
            AppCategorizer.resolveRoutine(21 * 60, 5 * 60, 7 * 60, 9 * 60, 19 * 60, 17 * 60, 20 * 60),
        )
        assertEquals(
            AppRoutine.FITNESS,
            AppCategorizer.resolveRoutine(30, 5 * 60, 7 * 60, 9 * 60, 23 * 60, 17 * 60, 20 * 60),
        )
        assertEquals(
            AppRoutine.WORK,
            AppCategorizer.resolveRoutine(90, 5 * 60, 7 * 60, 9 * 60, 23 * 60, 17 * 60, 20 * 60),
        )
    }

    @Test
    fun vacationOverridesWeekendAndWeekendOverridesWeekday() {
        assertEquals(
            AppRoutine.VACATION,
            AppCategorizer.resolveMode(true, Calendar.SATURDAY, AppRoutine.WORK),
        )
        assertEquals(
            AppRoutine.WEEKEND,
            AppCategorizer.resolveMode(false, Calendar.SUNDAY, AppRoutine.READING),
        )
        assertEquals(
            AppRoutine.WORK,
            AppCategorizer.resolveMode(false, Calendar.MONDAY, AppRoutine.WORK),
        )
    }

    private data class CategoryExample(
        val packageName: String,
        val label: String,
        val declaredCategory: Int,
        val expected: AppCategory,
    )

}
