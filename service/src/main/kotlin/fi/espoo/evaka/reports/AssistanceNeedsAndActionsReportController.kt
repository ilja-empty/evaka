// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.reports

import fi.espoo.evaka.Audit
import fi.espoo.evaka.assistance.DaycareAssistanceLevel
import fi.espoo.evaka.assistance.OtherAssistanceMeasureType
import fi.espoo.evaka.assistance.PreschoolAssistanceLevel
import fi.espoo.evaka.assistanceaction.AssistanceActionOption
import fi.espoo.evaka.assistanceaction.getAssistanceActionOptions
import fi.espoo.evaka.shared.DatabaseTable
import fi.espoo.evaka.shared.DaycareId
import fi.espoo.evaka.shared.GroupId
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.domain.EvakaClock
import fi.espoo.evaka.shared.security.AccessControl
import fi.espoo.evaka.shared.security.Action
import fi.espoo.evaka.shared.security.actionrule.AccessControlFilter
import fi.espoo.evaka.shared.security.actionrule.forTable
import java.time.LocalDate
import org.jdbi.v3.json.Json
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class AssistanceNeedsAndActionsReportController(private val accessControl: AccessControl) {
    @GetMapping("/reports/assistance-needs-and-actions")
    fun getAssistanceNeedReport(
        db: Database,
        user: AuthenticatedUser,
        clock: EvakaClock,
        @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): AssistanceNeedsAndActionsReport {
        return db.connect { dbc ->
                dbc.read {
                    val filter =
                        accessControl.requireAuthorizationFilter(
                            it,
                            user,
                            clock,
                            Action.Unit.READ_ASSISTANCE_NEEDS_AND_ACTIONS_REPORT
                        )
                    it.setStatementTimeout(REPORT_STATEMENT_TIMEOUT)
                    AssistanceNeedsAndActionsReport(
                        actions = it.getAssistanceActionOptions(),
                        rows = it.getReportRows(date, filter)
                    )
                }
            }
            .also {
                Audit.AssistanceNeedsReportRead.log(
                    meta = mapOf("date" to date, "count" to it.rows.size)
                )
            }
    }

    data class AssistanceNeedsAndActionsReport(
        val actions: List<AssistanceActionOption>,
        val rows: List<AssistanceNeedsAndActionsReportRow>
    )

    data class AssistanceNeedsAndActionsReportRow(
        val careAreaName: String,
        val unitId: DaycareId,
        val unitName: String,
        val groupId: GroupId,
        val groupName: String,
        @Json val actionCounts: Map<AssistanceActionOptionValue, Int>,
        val otherActionCount: Int,
        val noActionCount: Int,
        @Json val daycareAssistanceCounts: Map<DaycareAssistanceLevel, Int>,
        @Json val preschoolAssistanceCounts: Map<PreschoolAssistanceLevel, Int>,
        @Json val otherAssistanceMeasureCounts: Map<OtherAssistanceMeasureType, Int>
    )
}

private typealias AssistanceActionOptionValue = String

private fun Database.Read.getReportRows(
    date: LocalDate,
    unitFilter: AccessControlFilter<DaycareId>
) =
    createQuery<DatabaseTable> {
            sql(
                """
WITH action_counts AS (
    SELECT
        daycare_group_id,
        jsonb_object_agg(value, count) FILTER (WHERE value IS NOT NULL) AS action_counts,
        sum(count) FILTER (WHERE has_other_action IS TRUE) AS other_action_count,
        sum(count) FILTER (WHERE has_no_action IS TRUE) AS no_action_count
    FROM (
        SELECT
            gpl.daycare_group_id,
            aa.other_action != '' AS has_other_action,
            value IS NULL AND aa.other_action = '' AS has_no_action,
            value,
            count(DISTINCT aa.child_id) AS count
        FROM daycare_group_placement gpl
        JOIN placement pl ON pl.id = gpl.daycare_placement_id
        JOIN assistance_action aa ON aa.child_id = pl.child_id
        LEFT JOIN assistance_action_option_ref r ON r.action_id = aa.id
        LEFT JOIN assistance_action_option o on r.option_id = o.id
        WHERE daterange(gpl.start_date, gpl.end_date, '[]') @> ${bind(date)}
        AND daterange(pl.start_date, pl.end_date, '[]') @> ${bind(date)}
        AND daterange(aa.start_date, aa.end_date, '[]') @> ${bind(date)}
        GROUP BY GROUPING SETS ((1, 2), (1, 3), (1, 4))
    ) action_stats
    GROUP BY daycare_group_id
), daycare_assistance_counts AS (
    SELECT
        daycare_group_id,
        jsonb_object_agg(level, count) AS daycare_assistance_counts
    FROM (
        SELECT
            gpl.daycare_group_id,
            level,
            count(da.child_id) AS count
        FROM daycare_group_placement gpl
        JOIN placement pl ON pl.id = gpl.daycare_placement_id
        JOIN daycare_assistance da ON da.child_id = pl.child_id
        WHERE daterange(gpl.start_date, gpl.end_date, '[]') @> ${bind(date)}
        AND daterange(pl.start_date, pl.end_date, '[]') @> ${bind(date)}
        AND da.valid_during @> ${bind(date)}
        GROUP BY 1, 2
    ) daycare_assistance_stats
    GROUP BY daycare_group_id
), preschool_assistance_counts AS (
    SELECT
        daycare_group_id,
        jsonb_object_agg(level, count) AS preschool_assistance_counts
    FROM (
        SELECT
            gpl.daycare_group_id,
            level,
            count(pa.child_id) AS count
        FROM daycare_group_placement gpl
        JOIN placement pl ON pl.id = gpl.daycare_placement_id
        JOIN preschool_assistance pa ON pa.child_id = pl.child_id
        WHERE daterange(gpl.start_date, gpl.end_date, '[]') @> ${bind(date)}
        AND daterange(pl.start_date, pl.end_date, '[]') @> ${bind(date)}
        AND pa.valid_during @> ${bind(date)}
        GROUP BY 1, 2
    ) daycare_assistance_stats
    GROUP BY daycare_group_id
), other_assistance_measure_counts AS (
    SELECT
        daycare_group_id,
        jsonb_object_agg(type, count) AS other_assistance_measure_counts
    FROM (
        SELECT
            gpl.daycare_group_id,
            oam.type,
            count(oam.child_id) AS count
        FROM daycare_group_placement gpl
        JOIN placement pl ON pl.id = gpl.daycare_placement_id
        JOIN other_assistance_measure oam ON oam.child_id = pl.child_id
        WHERE daterange(gpl.start_date, gpl.end_date, '[]') @> ${bind(date)}
        AND daterange(pl.start_date, pl.end_date, '[]') @> ${bind(date)}
        AND oam.valid_during @> ${bind(date)}
        GROUP BY 1, 2
    ) daycare_assistance_stats
    GROUP BY daycare_group_id
)
SELECT
    ca.name AS care_area_name,
    u.id AS unit_id,
    u.name AS unit_name,
    g.id AS group_id,
    initcap(g.name) AS group_name,
    coalesce(action_counts, '{}') AS action_counts,
    coalesce(other_action_count, 0) AS other_action_count,
    coalesce(no_action_count, 0) AS no_action_count,
    coalesce(daycare_assistance_counts, '{}') AS daycare_assistance_counts,
    coalesce(preschool_assistance_counts, '{}') AS preschool_assistance_counts,
    coalesce(other_assistance_measure_counts, '{}') AS other_assistance_measure_counts
FROM daycare u
JOIN care_area ca ON u.care_area_id = ca.id
JOIN daycare_group g ON g.daycare_id = u.id AND daterange(g.start_date, g.end_date, '[]') @> ${bind(date)}
LEFT JOIN action_counts ON g.id = action_counts.daycare_group_id
LEFT JOIN daycare_assistance_counts ON g.id = daycare_assistance_counts.daycare_group_id
LEFT JOIN preschool_assistance_counts ON g.id = preschool_assistance_counts.daycare_group_id
LEFT JOIN other_assistance_measure_counts ON g.id = other_assistance_measure_counts.daycare_group_id
WHERE ${predicate(unitFilter.forTable("u"))}
ORDER BY ca.name, u.name, g.name
        """
                    .trimIndent()
            )
        }
        .mapTo<AssistanceNeedsAndActionsReportController.AssistanceNeedsAndActionsReportRow>()
        .toList()
