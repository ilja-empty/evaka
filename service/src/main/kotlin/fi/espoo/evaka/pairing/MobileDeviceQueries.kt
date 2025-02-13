// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.pairing

import fi.espoo.evaka.pis.SystemController
import fi.espoo.evaka.shared.DaycareId
import fi.espoo.evaka.shared.EmployeeId
import fi.espoo.evaka.shared.MobileDeviceId
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.domain.HelsinkiDateTime
import fi.espoo.evaka.shared.domain.NotFound
import java.util.UUID

fun Database.Read.getDevice(id: MobileDeviceId): MobileDeviceDetails {
    return createQuery(
            """
SELECT
    md.id, md.name, md.employee_id,
    CASE
        WHEN md.unit_id IS NOT NULL THEN ARRAY[md.unit_id]
        ELSE coalesce(array_agg(acl.daycare_id) FILTER (WHERE acl.daycare_id IS NOT NULL), '{}')
    END AS unit_ids,
    md.employee_id IS NOT NULL AS personal_device
FROM mobile_device md
LEFT JOIN daycare_acl acl ON md.employee_id = acl.employee_id
WHERE id = :id
GROUP BY md.id, md.name, md.employee_id
"""
        )
        .bind("id", id)
        .exactlyOneOrNull<MobileDeviceDetails>()
        ?: throw NotFound("Device $id not found")
}

fun Database.Read.getDeviceByToken(token: UUID): MobileDeviceIdentity =
    createQuery("SELECT id, long_term_token FROM mobile_device WHERE long_term_token = :token")
        .bind("token", token)
        .exactlyOneOrNull<MobileDeviceIdentity>()
        ?: throw NotFound("Device not found with token $token")

fun Database.Read.listSharedDevices(unitId: DaycareId): List<MobileDevice> {
    return createQuery("SELECT id, name FROM mobile_device WHERE unit_id = :unitId")
        .bind("unitId", unitId)
        .mapTo<MobileDevice>()
        .toList()
}

fun Database.Read.listPersonalDevices(employeeId: EmployeeId): List<MobileDevice> {
    return createQuery("SELECT id, name FROM mobile_device WHERE employee_id = :employeeId")
        .bind("employeeId", employeeId)
        .mapTo<MobileDevice>()
        .toList()
}

fun Database.Transaction.updateDeviceTracking(
    id: MobileDeviceId,
    lastSeen: HelsinkiDateTime,
    tracking: SystemController.MobileDeviceTracking
) =
    createUpdate<Any> {
            sql(
                """
UPDATE mobile_device
SET last_seen = ${bind(lastSeen)}, user_agent = ${bind(tracking.userAgent)}
WHERE id = ${bind(id)}
"""
            )
        }
        .execute()

fun Database.Transaction.renameDevice(id: MobileDeviceId, name: String) {
    // language=sql
    val deviceUpdate = "UPDATE mobile_device SET name = :name WHERE id = :id"
    createUpdate(deviceUpdate)
        .bind("id", id)
        .bind("name", name)
        .updateExactlyOne(notFoundMsg = "Device $id not found")
}

fun Database.Transaction.deleteDevice(id: MobileDeviceId) =
    createUpdate(
            """
DELETE FROM mobile_device WHERE id = :id
    """
                .trimIndent()
        )
        .bind("id", id)
        .execute()
