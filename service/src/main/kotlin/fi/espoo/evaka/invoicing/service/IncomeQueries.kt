// SPDX-FileCopyrightText: 2017-2023 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.invoicing.service

import fi.espoo.evaka.shared.IncomeNotificationId
import fi.espoo.evaka.shared.PersonId
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.domain.FiniteDateRange
import fi.espoo.evaka.shared.domain.HelsinkiDateTime
import fi.espoo.evaka.shared.utils.applyIf
import java.time.LocalDate

fun Database.Read.personHasActiveIncomeOnDate(personId: PersonId, theDate: LocalDate): Boolean {
    return createQuery(
            """
                    SELECT 1
                    FROM income
                    WHERE daterange(valid_from, valid_to, '[]') @> :the_date
                        AND person_id = :personId
                """
                .trimIndent()
        )
        .bind("personId", personId)
        .bind("the_date", theDate)
        .mapTo<Int>()
        .toList()
        .isNotEmpty()
}

enum class IncomeNotificationType {
    INITIAL_EMAIL,
    REMINDER_EMAIL,
    EXPIRED_EMAIL
}

data class GuardianIncomeExpirationDate(val guardianId: PersonId, val expirationDate: LocalDate)

fun Database.Read.expiringIncomes(
    today: LocalDate,
    checkForExpirationRange: FiniteDateRange,
    checkForExistingRecentIncomeNotificationType: IncomeNotificationType? = null,
    guardianId: PersonId? = null
): List<GuardianIncomeExpirationDate> {
    val existingRecentIncomeNotificationQuery =
        """
    SELECT 1 FROM income_notification 
    WHERE receiver_id = expiring_income.person_id AND notification_type = :notificationType 
        AND created > :today - INTERVAL '1 month'
    """
            .trimIndent()

    return createQuery(
            """
WITH latest_income AS (
    SELECT DISTINCT ON (person_id)
    id, person_id, valid_to
    FROM income i 
    ORDER BY person_id, valid_to DESC
), expiring_income_with_billable_placement_day_after_expiration AS (
    SELECT DISTINCT i.person_id, i.valid_to
    FROM placement pl
    JOIN service_need sn ON pl.id = sn.placement_id AND daterange(sn.start_date, sn.end_date, '[]') @> :dayAfterExpiration
    JOIN service_need_option sno ON sn.option_id = sno.id AND sno.fee_coefficient > 0
    JOIN guardian g ON g.child_id = pl.child_id
    JOIN latest_income i ON i.person_id = g.guardian_id
    WHERE :checkForExpirationRange @> i.valid_to
     AND daterange(pl.start_date, pl.end_date, '[]') @> (i.valid_to + INTERVAL '1 day')::date
)
SELECT person_id AS guardian_id, valid_to AS expiration_date
FROM expiring_income_with_billable_placement_day_after_expiration expiring_income 
WHERE NOT EXISTS (
    SELECT 1 FROM income_statement
    WHERE person_id = expiring_income.person_id
        AND (end_date IS NULL OR :dayAfterExpiration <= end_date)
        AND handler_id IS NULL
) 
${if (checkForExistingRecentIncomeNotificationType != null) " AND NOT EXISTS ($existingRecentIncomeNotificationQuery)" else ""}                
${if (guardianId != null) " AND person_id = :guardianId" else ""}
    """
                .trimIndent()
        )
        .bind("checkForExpirationRange", checkForExpirationRange.asDateRange())
        .bind("dayAfterExpiration", checkForExpirationRange.end.plusDays(1))
        .bind("notificationType", checkForExistingRecentIncomeNotificationType)
        .bind("today", today)
        .applyIf(checkForExistingRecentIncomeNotificationType != null) {
            this.bind("notificationType", checkForExistingRecentIncomeNotificationType)
        }
        .applyIf(guardianId != null) { this.bind("guardianId", guardianId) }
        .mapTo<GuardianIncomeExpirationDate>()
        .toList()
}

data class IncomeNotification(
    val receiverId: PersonId,
    val notificationType: IncomeNotificationType,
    val created: HelsinkiDateTime
)

fun Database.Transaction.createIncomeNotification(
    receiverId: PersonId,
    notificationType: IncomeNotificationType
): IncomeNotificationId {
    return createUpdate(
            """
        INSERT INTO income_notification(receiver_id, notification_type)
        VALUES (:receiverId, :notificationType)
        RETURNING id
    """
                .trimIndent()
        )
        .bind("receiverId", receiverId)
        .bind("notificationType", notificationType)
        .executeAndReturnGeneratedKeys()
        .mapTo<IncomeNotificationId>()
        .exactlyOne()
}

fun Database.Read.getIncomeNotifications(receiverId: PersonId): List<IncomeNotification> =
    createQuery(
            """SELECT receiver_id, notification_type, created FROM income_notification WHERE receiver_id = :receiverId"""
        )
        .bind("receiverId", receiverId)
        .mapTo<IncomeNotification>()
        .toList()
