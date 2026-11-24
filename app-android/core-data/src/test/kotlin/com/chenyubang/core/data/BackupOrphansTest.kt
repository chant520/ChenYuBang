package com.chenyubang.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 验证计划第 2 节 #9：四类孤儿分别计数与处理，不猜字段、不丢整条。 */
@Suppress("MagicNumber")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupOrphansTest {
    @Test
    fun resolveReportsFourOrphanKindsWithExactCounts() {
        val missingExample = "99999999-9999-4999-8999-999999999999"
        val missingMistakeIdiom = "88888888-8888-4888-8888-888888888888"
        val missingExampleIdiom = "77777777-7777-4777-8777-777777777777"
        val missingReviewMistake = "66666666-6666-4666-8666-666666666666"
        val snapshot =
            BackupSnapshot(
                mistakes =
                    listOf(
                        BackupFixture.mistake(id = BackupFixture.MISTAKE_A, userExampleId = missingExample),
                        BackupFixture.mistake(
                            id = BackupFixture.MISTAKE_B,
                            userExampleId = BackupFixture.EXAMPLE_A,
                            idiomIds = listOf(BackupFixture.IDIOM_1, missingMistakeIdiom),
                        ),
                    ),
                userExamples =
                    listOf(
                        BackupFixture.userExample(
                            id = BackupFixture.EXAMPLE_A,
                            idiomIds = listOf(BackupFixture.IDIOM_2, missingExampleIdiom),
                        ),
                    ),
                reviews =
                    listOf(
                        BackupFixture.review(id = BackupFixture.REVIEW_A, mistakeId = missingReviewMistake),
                        BackupFixture.review(id = BackupFixture.REVIEW_B, mistakeId = BackupFixture.MISTAKE_B),
                    ),
            )
        val references =
            BackupReferences(
                idiomIds = setOf(BackupFixture.IDIOM_1, BackupFixture.IDIOM_2),
                userExampleIds = setOf(BackupFixture.EXAMPLE_A),
                mistakeIds = emptySet(),
            )
        val resolved = BackupOrphans.resolve(BackupFixture.validated(snapshot), references)
        val report = resolved.report
        assertEquals(1, report.mistakesWithoutUserExample.size)
        assertEquals(1, report.mistakeIdiomsMissing.size)
        assertEquals(1, report.userExampleIdiomsMissing.size)
        assertEquals(1, report.reviewsWithoutMistake.size)
        assertEquals(listOf(BackupFixture.MISTAKE_A), report.mistakesWithoutUserExample)
        assertEquals(mapOf(BackupFixture.MISTAKE_B to listOf(missingMistakeIdiom)), report.mistakeIdiomsMissing)
        assertEquals(
            mapOf(BackupFixture.EXAMPLE_A to listOf(missingExampleIdiom)),
            report.userExampleIdiomsMissing,
        )
        assertEquals(listOf(BackupFixture.REVIEW_A), report.reviewsWithoutMistake)
        assertEquals(listOf(BackupFixture.MISTAKE_B), resolved.snapshot.mistakes.map { it.id })
        assertEquals(listOf(BackupFixture.IDIOM_1), resolved.snapshot.mistakes.single().idiomIds)
        assertEquals(listOf(BackupFixture.IDIOM_2), resolved.snapshot.userExamples.single().idiomIds)
        assertEquals(listOf(BackupFixture.REVIEW_B), resolved.snapshot.reviews.map { it.id })
    }
}
