// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.reservations

import fi.espoo.evaka.Audit
import fi.espoo.evaka.ExcludeCodeGen
import fi.espoo.evaka.dailyservicetimes.DailyServiceTimesValue
import fi.espoo.evaka.dailyservicetimes.getDailyServiceTimesForChildren
import fi.espoo.evaka.daycare.getDaycare
import fi.espoo.evaka.daycare.service.AbsenceType
import fi.espoo.evaka.shared.ChildId
import fi.espoo.evaka.shared.DaycareId
import fi.espoo.evaka.shared.GroupId
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.db.mapColumn
import fi.espoo.evaka.shared.db.mapJsonColumn
import fi.espoo.evaka.shared.domain.BadRequest
import fi.espoo.evaka.shared.domain.EvakaClock
import fi.espoo.evaka.shared.domain.FiniteDateRange
import fi.espoo.evaka.shared.domain.NotFound
import fi.espoo.evaka.shared.security.AccessControl
import fi.espoo.evaka.shared.security.Action
import java.time.LocalDate
import org.jdbi.v3.core.mapper.Nested
import org.jdbi.v3.core.mapper.PropagateNull
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/attendance-reservations")
class AttendanceReservationController(private val ac: AccessControl) {
    @GetMapping
    fun getAttendanceReservations(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @RequestParam unitId: DaycareId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate
    ): UnitAttendanceReservations {
        if (to < from || from.plusMonths(1) < to) throw BadRequest("Invalid query dates")
        val period = FiniteDateRange(from, to)

        return db.connect { dbc ->
                dbc.read { tx ->
                    ac.requirePermissionFor(
                        tx,
                        user,
                        clock,
                        Action.Unit.READ_ATTENDANCE_RESERVATIONS,
                        unitId
                    )
                    val unitName =
                        tx.getDaycare(unitId)?.name ?: throw NotFound("Unit $unitId not found")
                    val operationalDays = tx.getUnitOperationalDays(unitId, period)

                    val effectiveGroupPlacements =
                        tx.getEffectiveGroupPlacementsInRange(unitId, period)

                    val flatData =
                        period
                            .dates()
                            .flatMap { date ->
                                effectiveGroupPlacements
                                    .filter { it.period.includes(date) }
                                    .groupBy { it.childId }
                                    // Find the correct placement status for each child on this
                                    // date.
                                    // Precedence: Backup > Group placement > Placement
                                    .map { (childId, rows) ->
                                        val backup = rows.find { it.isBackup }
                                        val groupInOwnUnit = rows.find { it.group != null }?.group
                                        val group =
                                            if (backup != null && !backup.inOtherUnit) backup.group
                                            else groupInOwnUnit
                                        ChildPlacementStatus(
                                            date = date,
                                            childId = childId,
                                            group = group,
                                            inOtherUnit = backup?.inOtherUnit ?: false
                                        )
                                    }
                            }
                            .toList()

                    val childIds = flatData.map { it.childId }.toSet()
                    val serviceTimes = tx.getDailyServiceTimesForChildren(childIds)
                    val childData = tx.getChildData(childIds, period)

                    val byGroup = flatData.groupBy { it.group }

                    UnitAttendanceReservations(
                        unit = unitName,
                        operationalDays = operationalDays,
                        groups =
                            byGroup.entries.mapNotNull { (group, rows) ->
                                if (group == null) {
                                    null
                                } else {
                                    UnitAttendanceReservations.GroupAttendanceReservations(
                                        group = group,
                                        children = toChildDayRows(rows, serviceTimes, childData)
                                    )
                                }
                            },
                        ungrouped =
                            byGroup[null]?.let { toChildDayRows(it, serviceTimes, childData) }
                                ?: emptyList()
                    )
                }
            }
            .also {
                Audit.UnitAttendanceReservationsRead.log(
                    targetId = unitId,
                    meta = mapOf("from" to from, "to" to to)
                )
            }
    }

    @PostMapping
    fun postReservations(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @RequestBody body: List<DailyReservationRequest>
    ) {
        val children = body.map { it.childId }.toSet()

        if (body.any { it.absent }) {
            throw BadRequest("Absences are not allowed", "ABSENCES_NOT_ALLOWED")
        }

        val result =
            db.connect { dbc ->
                dbc.transaction {
                    ac.requirePermissionFor(
                        it,
                        user,
                        clock,
                        Action.Child.CREATE_ATTENDANCE_RESERVATION,
                        children
                    )
                    createReservationsAndAbsences(it, user.evakaUserId, body)
                }
            }
        Audit.AttendanceReservationEmployeeCreate.log(
            targetId = children,
            meta =
                mapOf(
                    "deletedAbsences" to result.deletedAbsences,
                    "deletedReservations" to result.deletedReservations,
                    "upsertedAbsences" to result.upsertedAbsences,
                    "upsertedReservations" to result.upsertedReservations
                )
        )
    }
}

@ExcludeCodeGen
data class UnitAttendanceReservations(
    val unit: String,
    val operationalDays: List<OperationalDay>,
    val groups: List<GroupAttendanceReservations>,
    val ungrouped: List<ChildDailyRecords>
) {
    data class OperationalDay(val date: LocalDate, val isHoliday: Boolean)

    data class GroupAttendanceReservations(
        val group: ReservationGroup,
        val children: List<ChildDailyRecords>
    )

    data class ReservationGroup(@PropagateNull val id: GroupId, val name: String)

    data class ChildDailyRecords(
        val child: Child,
        val dailyData: List<Map<LocalDate, ChildRecordOfDay>>
    )

    data class ChildRecordOfDay(
        val reservation: ReservationTimes?,
        val attendance: AttendanceTimes?,
        val absence: Absence?,
        val dailyServiceTimes: DailyServiceTimesValue?,
        val inOtherUnit: Boolean
    )

    data class ReservationTimes(val startTime: String, val endTime: String)

    data class AttendanceTimes(val startTime: String, val endTime: String?)

    data class Absence(val type: AbsenceType)

    data class Child(
        val id: ChildId,
        val firstName: String,
        val lastName: String,
        val dateOfBirth: LocalDate
    )
}

private fun Database.Read.getUnitOperationalDays(unitId: DaycareId, dateRange: FiniteDateRange) =
    createQuery(
            """
    SELECT t::date AS date, holiday.date IS NOT NULL AS is_holiday
    FROM generate_series(:start, :end, '1 day') t
    JOIN daycare ON daycare.id = :unitId AND date_part('isodow', t) = ANY(daycare.operation_days)
    LEFT JOIN holiday ON t = holiday.date AND NOT round_the_clock
    """
                .trimIndent()
        )
        .bind("unitId", unitId)
        .bind("start", dateRange.start)
        .bind("end", dateRange.end)
        .mapTo<UnitAttendanceReservations.OperationalDay>()
        .toList()

private data class ChildPlacementStatus(
    val date: LocalDate,
    val childId: ChildId,
    val group: UnitAttendanceReservations.ReservationGroup?,
    val inOtherUnit: Boolean
)

private data class EffectiveGroupPlacementPeriod(
    val period: FiniteDateRange,
    val childId: ChildId,
    @Nested("group") val group: UnitAttendanceReservations.ReservationGroup?,
    val isBackup: Boolean,
    val inOtherUnit: Boolean
)

private fun Database.Read.getEffectiveGroupPlacementsInRange(
    unitId: DaycareId,
    dateRange: FiniteDateRange
): List<EffectiveGroupPlacementPeriod> {
    return createQuery(
            """
-- Placement without group
SELECT
    daterange(p.start_date, p.end_date, '[]') * :dateRange AS period,
    p.child_id,
    NULL AS group_id,
    NULL AS group_name,
    FALSE AS is_backup,
    FALSE AS in_other_unit
FROM placement p
WHERE p.unit_id = :unitId AND daterange(p.start_date, p.end_date, '[]') && :dateRange

UNION ALL

-- Group placement
SELECT
    daterange(dgp.start_date, dgp.end_date, '[]') * :dateRange AS period,
    p.child_id,
    dgp.daycare_group_id AS group_id,
    dg.name AS group_name,
    FALSE AS is_backup,
    FALSE AS in_other_unit
FROM daycare_group_placement dgp
JOIN placement p ON p.id = dgp.daycare_placement_id
JOIN daycare_group dg ON dg.id = dgp.daycare_group_id
WHERE p.unit_id = :unitId AND daterange(dgp.start_date, dgp.end_date, '[]') && :dateRange

UNION ALL

/*
Backup placement
- in own unit => use the backup group
- in other unit => use no group, so the group is taken from own group placement
*/
SELECT
    daterange(bc.start_date, bc.end_date, '[]') * :dateRange AS period,
    bc.child_id,
    CASE WHEN bc.unit_id <> :unitId THEN NULL ELSE bc.group_id END AS group_id,
    CASE WHEN bc.unit_id <> :unitId THEN NULL ELSE dg.name END AS group_name,
    TRUE AS is_backup,
    bc.unit_id <> :unitId AS in_other_unit
FROM placement p
JOIN backup_care bc ON bc.child_id = p.child_id
LEFT JOIN daycare_group dg ON dg.id = bc.group_id
WHERE
    (p.unit_id = :unitId OR bc.unit_id = :unitId) AND
    daterange(p.start_date, p.end_date, '[]') && :dateRange AND
    daterange(bc.start_date, bc.end_date, '[]') && :dateRange
"""
        )
        .bind("unitId", unitId)
        .bind("dateRange", dateRange)
        .mapTo<EffectiveGroupPlacementPeriod>()
        .toList()
}

private data class ChildData(
    val child: UnitAttendanceReservations.Child,
    val reservations: Map<LocalDate, List<UnitAttendanceReservations.ReservationTimes>>,
    val attendances: Map<LocalDate, List<UnitAttendanceReservations.AttendanceTimes>>,
    val absences: Map<LocalDate, UnitAttendanceReservations.Absence>
)

private data class ReservationTimesForDate(
    val date: LocalDate,
    val startTime: String,
    val endTime: String
) {
    fun toReservationTimes() = UnitAttendanceReservations.ReservationTimes(startTime, endTime)
}

private data class AttendanceTimesForDate(
    val date: LocalDate,
    val startTime: String,
    val endTime: String?
) {
    fun toAttendanceTimes() = UnitAttendanceReservations.AttendanceTimes(startTime, endTime)
}

private data class AbsenceForDate(val date: LocalDate, val type: AbsenceType) {
    fun toAbsence() = UnitAttendanceReservations.Absence(type)
}

private fun Database.Read.getChildData(
    childIds: Set<ChildId>,
    dateRange: FiniteDateRange
): Map<ChildId, ChildData> {
    return createQuery(
            """
SELECT            
    p.id,
    p.first_name,
    p.last_name,
    p.date_of_birth,
    coalesce((
        SELECT jsonb_agg(jsonb_build_object(
            'date', to_char(ar.date, 'YYYY-MM-DD'),
            'startTime', to_char(ar.start_time, 'HH24:MI'),
            'endTime', to_char(ar.end_time, 'HH24:MI')
        ) ORDER BY ar.date, ar.start_time)
        FROM attendance_reservation ar WHERE ar.child_id = p.id AND between_start_and_end(:dateRange, ar.date)
    ), '[]'::jsonb) AS reservations,
    coalesce((
        SELECT jsonb_agg(jsonb_build_object(
            'date', to_char(att.date, 'YYYY-MM-DD'),
            'startTime', to_char(att.start_time, 'HH24:MI'),
            'endTime', to_char(att.end_time, 'HH24:MI')
        ) ORDER BY att.date, att.start_time)
        FROM child_attendance att WHERE att.child_id = p.id AND between_start_and_end(:dateRange, att.date)
    ), '[]'::jsonb) AS attendances,
    coalesce((
        SELECT jsonb_agg(json_build_object(
            'date', to_char(a.date, 'YYYY-MM-DD'),
            'type', a.absence_type
        ) ORDER BY a.date)
        FROM absence a
        WHERE a.child_id = p.id AND between_start_and_end(:dateRange, a.date)
    ), '[]'::jsonb) AS absences
FROM person p
WHERE p.id = ANY(:childIds)
"""
        )
        .bind("dateRange", dateRange)
        .bind("childIds", childIds)
        .map { row ->
            ChildData(
                child =
                    UnitAttendanceReservations.Child(
                        id = row.mapColumn("id"),
                        firstName = row.mapColumn("first_name"),
                        lastName = row.mapColumn("last_name"),
                        dateOfBirth = row.mapColumn("date_of_birth")
                    ),
                reservations =
                    row.mapJsonColumn<List<ReservationTimesForDate>>("reservations")
                        .groupBy({ it.date }, { it.toReservationTimes() }),
                attendances =
                    row.mapJsonColumn<List<AttendanceTimesForDate>>("attendances")
                        .groupBy({ it.date }, { it.toAttendanceTimes() }),
                // The SQL query can return multiple absences for the same date, but here we only
                // take one
                absences =
                    row.mapJsonColumn<List<AbsenceForDate>>("absences")
                        .associateBy({ it.date }, { it.toAbsence() })
            )
        }
        .associateBy { it.child.id }
}

private fun toChildDayRows(
    rows: List<ChildPlacementStatus>,
    serviceTimes: Map<ChildId, List<DailyServiceTimesValue>>,
    childData: Map<ChildId, ChildData>
): List<UnitAttendanceReservations.ChildDailyRecords> {
    return rows
        .groupBy { it.childId }
        .map { (childId, dailyData) ->
            val child =
                childData[childId]
                    ?: throw IllegalStateException("Child data not found for child $childId")
            val hasMultipleRecordsOnSomeDay =
                dailyData.any { placementStatus ->
                    val date = placementStatus.date
                    !placementStatus.inOtherUnit &&
                        ((child.reservations[date] ?: listOf()).size > 1 ||
                            (child.attendances[date] ?: listOf()).size > 1)
                }
            UnitAttendanceReservations.ChildDailyRecords(
                child = child.child,
                dailyData =
                    listOfNotNull(
                        dailyData.associateBy(
                            { it.date },
                            { dailyRecord(it, 0, child, serviceTimes[childId]) }
                        ),
                        if (hasMultipleRecordsOnSomeDay) {
                            dailyData.associateBy(
                                { it.date },
                                { dailyRecord(it, 1, child, serviceTimes[childId]) }
                            )
                        } else {
                            null
                        }
                    )
            )
        }
}

private fun dailyRecord(
    placementStatus: ChildPlacementStatus,
    rowIndex: Int,
    childData: ChildData,
    serviceTimes: List<DailyServiceTimesValue>?
): UnitAttendanceReservations.ChildRecordOfDay {
    val date = placementStatus.date
    val inOtherUnit = placementStatus.inOtherUnit

    val reservation = if (inOtherUnit) null else childData.reservations[date]?.getOrNull(rowIndex)
    val attendance = if (inOtherUnit) null else childData.attendances[date]?.getOrNull(rowIndex)

    return UnitAttendanceReservations.ChildRecordOfDay(
        reservation = reservation,
        attendance = attendance,
        absence = childData.absences[date],
        dailyServiceTimes = serviceTimes?.find { dst -> dst.validityPeriod.includes(date) },
        inOtherUnit = inOtherUnit
    )
}
