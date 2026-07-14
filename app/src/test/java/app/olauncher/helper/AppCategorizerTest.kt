package app.olauncher.helper

import android.content.pm.ApplicationInfo
import app.olauncher.data.AppCategory
import app.olauncher.data.AppRoutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            "com.google.android.gm Gmail" to AppCategory.COMMUNICATION,
            "com.Slack Slack" to AppCategory.COMMUNICATION,
            "com.microsoft.teams Teams" to AppCategory.COMMUNICATION,
            "notion.id Notion" to AppCategory.PRODUCTIVITY,
            "com.strava Strava" to AppCategory.HEALTH,
            "com.whatsapp WhatsApp" to AppCategory.COMMUNICATION,
            "com.google.android.apps.photos Google Photos" to AppCategory.MEDIA,
            "com.dd.doordash DoorDash" to AppCategory.SHOPPING,
        )

        examples.forEach { (app, expected) ->
            assertEquals(app, expected, AppCategorizer.categoryFromText(app))
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
        )

        expected.forEach { (routine, category) ->
            assertEquals(routine.name, category, AppCategorizer.categoryOrder(routine).first())
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
    fun routineHintsRankLikelyAppsAheadOfGenericPeers() {
        assertAhead(AppRoutine.READING, "com.amazon.kindle", "Kindle", "com.spotify.music", "Spotify")
        assertAhead(AppRoutine.COMMUTE, "com.google.android.apps.maps", "Maps", "com.booking", "Booking")
        assertAhead(AppRoutine.WORK, "notion.id", "Notion", "com.microsoft.office", "Office")
        assertAhead(AppRoutine.FITNESS, "com.tennisone", "TennisONE", "com.sleep", "Sleep")
        assertAhead(AppRoutine.FAMILY, "com.whatsapp", "Messages", "com.discord", "Discord")
        assertAhead(AppRoutine.EVENING, "com.audible.application", "Audible", "com.youtube", "YouTube")
    }

    private fun assertAhead(
        routine: AppRoutine,
        preferredPackage: String,
        preferredLabel: String,
        otherPackage: String,
        otherLabel: String,
    ) {
        assertTrue(
            "$preferredLabel should rank ahead of $otherLabel during ${routine.displayName}",
            AppCategorizer.routineScore(preferredPackage, preferredLabel, routine) >
                AppCategorizer.routineScore(otherPackage, otherLabel, routine),
        )
    }
}
