// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.application

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import fi.espoo.evaka.application.ApplicationPreschoolTypeToggle.DAYCARE_ONLY
import fi.espoo.evaka.application.ApplicationPreschoolTypeToggle.PREPARATORY_DAYCARE
import fi.espoo.evaka.application.ApplicationPreschoolTypeToggle.PREPARATORY_ONLY
import fi.espoo.evaka.application.ApplicationPreschoolTypeToggle.PRESCHOOL_CLUB
import fi.espoo.evaka.application.ApplicationPreschoolTypeToggle.PRESCHOOL_DAYCARE
import fi.espoo.evaka.application.ApplicationPreschoolTypeToggle.PRESCHOOL_ONLY
import fi.espoo.evaka.application.persistence.club.ClubFormV0
import fi.espoo.evaka.application.persistence.daycare.DaycareFormV0
import fi.espoo.evaka.application.utils.exhaust
import fi.espoo.evaka.placement.PlacementPlanConfirmationStatus
import fi.espoo.evaka.placement.PlacementType
import fi.espoo.evaka.shared.ApplicationId
import fi.espoo.evaka.shared.AttachmentId
import fi.espoo.evaka.shared.ChildId
import fi.espoo.evaka.shared.DatabaseTable
import fi.espoo.evaka.shared.DaycareId
import fi.espoo.evaka.shared.Paged
import fi.espoo.evaka.shared.PersonId
import fi.espoo.evaka.shared.ServiceNeedOptionId
import fi.espoo.evaka.shared.auth.AclAuthorization
import fi.espoo.evaka.shared.db.Binding
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.db.freeTextSearchQuery
import fi.espoo.evaka.shared.db.mapColumn
import fi.espoo.evaka.shared.db.mapJsonColumn
import fi.espoo.evaka.shared.domain.EvakaClock
import fi.espoo.evaka.shared.domain.HelsinkiDateTime
import fi.espoo.evaka.shared.mapToPaged
import fi.espoo.evaka.shared.security.actionrule.AccessControlFilter
import fi.espoo.evaka.shared.security.actionrule.forTable
import java.time.LocalDate
import java.util.UUID
import mu.KotlinLogging
import org.jdbi.v3.core.result.RowView

private val logger = KotlinLogging.logger {}

enum class ApplicationSortColumn {
    APPLICATION_TYPE,
    CHILD_NAME,
    DUE_DATE,
    START_DATE,
    STATUS,
    UNIT_NAME
}

enum class ApplicationBasis {
    ADDITIONAL_INFO,
    SIBLING_BASIS,
    ASSISTANCE_NEED,
    CLUB_CARE,
    DAYCARE,
    EXTENDED_CARE,
    DUPLICATE_APPLICATION,
    URGENT,
    HAS_ATTACHMENTS
}

enum class ApplicationSortDirection {
    ASC,
    DESC
}

fun Database.Transaction.insertApplication(
    type: ApplicationType,
    guardianId: PersonId,
    childId: ChildId,
    origin: ApplicationOrigin,
    hideFromGuardian: Boolean = false,
    sentDate: LocalDate? = null,
): ApplicationId {
    // language=sql
    val sql =
        """
        INSERT INTO application (type, status, guardian_id, child_id, origin, hidefromguardian, sentdate, allow_other_guardian_access)
        VALUES (:type, 'CREATED'::application_status_type, :guardianId, :childId, :origin::application_origin_type, :hideFromGuardian, :sentDate, false)
        RETURNING id
        """
            .trimIndent()

    return createUpdate(sql)
        .bind("type", type)
        .bind("guardianId", guardianId)
        .bind("childId", childId)
        .bind("origin", origin)
        .bind("hideFromGuardian", hideFromGuardian)
        .bind("sentDate", sentDate)
        .executeAndReturnGeneratedKeys()
        .mapTo<ApplicationId>()
        .exactlyOne()
}

fun Database.Read.duplicateApplicationExists(
    childId: ChildId,
    guardianId: PersonId,
    type: ApplicationType
): Boolean {
    // language=sql
    val sql =
        """
            SELECT 1
            FROM application_view
            WHERE 
                childid = :childId AND 
                guardianid = :guardianId AND
                type = :type AND 
                hidefromguardian = false AND
                status = ANY ('{CREATED,SENT,WAITING_PLACEMENT,WAITING_CONFIRMATION,WAITING_DECISION,WAITING_MAILING,WAITING_UNIT_CONFIRMATION}'::application_status_type[])
        """
            .trimIndent()
    return createQuery(sql)
        .bind("childId", childId)
        .bind("guardianId", guardianId)
        .bind("type", type)
        .mapTo<Int>()
        .toList()
        .isNotEmpty()
}

fun Database.Read.activePlacementExists(
    childId: ChildId,
    type: ApplicationType,
    today: LocalDate
): Boolean {
    val placementTypes =
        when (type) {
            ApplicationType.DAYCARE ->
                listOf(PlacementType.DAYCARE, PlacementType.DAYCARE_PART_TIME)
            ApplicationType.PRESCHOOL ->
                listOf(
                    PlacementType.PREPARATORY,
                    PlacementType.PREPARATORY_DAYCARE,
                    PlacementType.PRESCHOOL,
                    PlacementType.PRESCHOOL_DAYCARE,
                    PlacementType.PRESCHOOL_CLUB
                )
            else -> listOf()
        }
    if (placementTypes.isEmpty()) return false

    val sql =
        """
            SELECT 1
            FROM placement
            WHERE 
                child_id = :childId AND
                type = ANY(:types::placement_type[]) AND 
                :today <= end_date
        """
            .trimIndent()
    return createQuery(sql)
        .bind("childId", childId)
        .bind("types", placementTypes)
        .bind("today", today)
        .mapTo<Int>()
        .toList()
        .isNotEmpty()
}

fun Database.Read.fetchApplicationSummaries(
    today: LocalDate,
    page: Int,
    pageSize: Int,
    sortBy: ApplicationSortColumn,
    sortDir: ApplicationSortDirection,
    areas: List<String>,
    units: List<DaycareId>,
    basis: List<ApplicationBasis>,
    type: ApplicationTypeToggle,
    preschoolType: List<ApplicationPreschoolTypeToggle>,
    statuses: List<ApplicationStatusOption>,
    dateType: List<ApplicationDateType>,
    distinctions: List<ApplicationDistinctions>,
    periodStart: LocalDate?,
    periodEnd: LocalDate?,
    searchTerms: String = "",
    transferApplications: TransferApplicationFilter,
    voucherApplications: VoucherApplicationFilter?,
    authorizedUnitsForApplicationsWithoutAssistanceNeed: AclAuthorization,
    authorizedUnitsForApplicationsWithAssistanceNeed: AclAuthorization,
    canReadServiceWorkerNotes: Boolean
): Paged<ApplicationSummary> {
    val params =
        listOf(
            Binding.of("page", page),
            Binding.of("pageSize", pageSize),
            Binding.of("area", areas),
            Binding.of("units", units),
            Binding.of(
                "authorizedUnitsForApplicationsWithoutAssistanceNeed",
                authorizedUnitsForApplicationsWithoutAssistanceNeed.ids
            ),
            Binding.of(
                "authorizedUnitsForApplicationsWithAssistanceNeed",
                authorizedUnitsForApplicationsWithAssistanceNeed.ids
            ),
            Binding.of("documentType", type.toApplicationType()),
            Binding.of("preschoolType", preschoolType),
            Binding.of("status", statuses),
            Binding.of("periodStart", periodStart),
            Binding.of("periodEnd", periodEnd)
        )

    val (freeTextQuery, freeTextParams) = freeTextSearchQuery(listOf("child"), searchTerms)

    val conditions =
        listOfNotNull(
            "a.status = ANY(:status::application_status_type[])",
            if (areas.isNotEmpty()) "ca.short_name = ANY(:area)" else null,
            if (basis.isNotEmpty()) {
                basis.joinToString("\nAND ") { applicationBasis ->
                    when (applicationBasis) {
                        ApplicationBasis.ADDITIONAL_INFO ->
                            """(
                            (a.document -> 'additionalDetails' ->> 'dietType') != '' OR 
                            (a.document -> 'additionalDetails' ->> 'otherInfo') != '' OR 
                            (a.document -> 'additionalDetails' ->> 'allergyType') != '')
                    """
                                .trimIndent()
                        ApplicationBasis.SIBLING_BASIS ->
                            "(a.document -> 'apply' ->> 'siblingBasis')::boolean = true"
                        ApplicationBasis.ASSISTANCE_NEED ->
                            "(a.document -> 'careDetails' ->> 'assistanceNeeded')::boolean = true"
                        ApplicationBasis.CLUB_CARE -> "was_on_club_care"
                        ApplicationBasis.DAYCARE ->
                            "(a.document ->> 'wasOnDaycare')::boolean = true"
                        ApplicationBasis.EXTENDED_CARE ->
                            "(a.document ->> 'extendedCare')::boolean = true"
                        ApplicationBasis.DUPLICATE_APPLICATION -> "has_duplicates"
                        ApplicationBasis.URGENT -> "(a.document ->> 'urgent')::boolean = true"
                        ApplicationBasis.HAS_ATTACHMENTS ->
                            "((a.document ->> 'urgent')::boolean = true OR (a.document ->> 'extendedCare')::boolean = true) AND array_length(attachments.attachment_ids, 1) > 0"
                    }
                }
            } else {
                null
            },
            if (type != ApplicationTypeToggle.ALL) "a.type = :documentType" else null,
            if (type == ApplicationTypeToggle.PRESCHOOL) {
                data class PreschoolFlags(
                    val preparatory: Boolean?,
                    val connectedDaycare: Boolean,
                    val additionalDaycareApplication: Boolean,
                    val serviceNeedOptionType: PlacementType? = null
                )
                when {
                    preschoolType.isEmpty() -> "FALSE"
                    else ->
                        preschoolType.joinToString(
                            separator = " OR ",
                            prefix = "(",
                            postfix = ")"
                        ) {
                            when (it) {
                                PRESCHOOL_ONLY ->
                                    PreschoolFlags(
                                        preparatory = false,
                                        connectedDaycare = false,
                                        additionalDaycareApplication = false
                                    )
                                PRESCHOOL_DAYCARE ->
                                    PreschoolFlags(
                                        preparatory = false,
                                        connectedDaycare = true,
                                        additionalDaycareApplication = false,
                                        serviceNeedOptionType = PlacementType.PRESCHOOL_DAYCARE
                                    )
                                PRESCHOOL_CLUB ->
                                    PreschoolFlags(
                                        preparatory = false,
                                        connectedDaycare = true,
                                        additionalDaycareApplication = false,
                                        serviceNeedOptionType = PlacementType.PRESCHOOL_CLUB
                                    )
                                PREPARATORY_ONLY ->
                                    PreschoolFlags(
                                        preparatory = true,
                                        connectedDaycare = false,
                                        additionalDaycareApplication = false
                                    )
                                PREPARATORY_DAYCARE ->
                                    PreschoolFlags(
                                        preparatory = true,
                                        connectedDaycare = true,
                                        additionalDaycareApplication = false
                                    )
                                DAYCARE_ONLY ->
                                    PreschoolFlags(
                                        preparatory = null,
                                        connectedDaycare = true,
                                        additionalDaycareApplication = true
                                    )
                            }.run {
                                listOfNotNull(
                                        preparatory?.let {
                                            "(a.document->'careDetails'->>'preparatory')::boolean = $preparatory"
                                        },
                                        "(a.document->>'connectedDaycare')::boolean = $connectedDaycare",
                                        "a.additionalDaycareApplication = $additionalDaycareApplication",
                                        serviceNeedOptionType?.let { pt ->
                                            when (pt) {
                                                PlacementType.PRESCHOOL_DAYCARE ->
                                                    "(a.document->'serviceNeedOption'->>'validPlacementType' = 'PRESCHOOL_DAYCARE' OR a.document->'serviceNeedOption'->>'validPlacementType' IS NULL)"
                                                PlacementType.PRESCHOOL_CLUB ->
                                                    "a.document->'serviceNeedOption'->>'validPlacementType' = 'PRESCHOOL_CLUB'"
                                                else ->
                                                    throw Error(
                                                        "Unsupported preschool type: $serviceNeedOptionType"
                                                    )
                                            }
                                        }
                                    )
                                    .joinToString(prefix = "(", separator = " AND ", postfix = ")")
                            }
                        }
                }
            } else {
                null
            },
            if (distinctions.contains(ApplicationDistinctions.SECONDARY))
                "pu.preferredUnits && :units"
            else if (units.isNotEmpty()) "d.id = ANY(:units)" else null,
            if (authorizedUnitsForApplicationsWithoutAssistanceNeed != AclAuthorization.All)
                "((a.document->'careDetails'->>'assistanceNeeded')::boolean = true OR pu.preferredUnits && :authorizedUnitsForApplicationsWithoutAssistanceNeed)"
            else null,
            if (authorizedUnitsForApplicationsWithAssistanceNeed != AclAuthorization.All)
                "((a.document->'careDetails'->>'assistanceNeeded')::boolean = false OR pu.preferredUnits && :authorizedUnitsForApplicationsWithAssistanceNeed)"
            else null,
            if (
                (periodStart != null || periodEnd != null) &&
                    dateType.contains(ApplicationDateType.DUE)
            )
                "between_start_and_end(daterange(:periodStart, :periodEnd, '[]'), a.dueDate)"
            else null,
            if (
                (periodStart != null || periodEnd != null) &&
                    dateType.contains(ApplicationDateType.START)
            )
                "between_start_and_end(daterange(:periodStart, :periodEnd, '[]'), (a.document ->> 'preferredStartDate')::date)"
            else null,
            if (
                (periodStart != null || periodEnd != null) &&
                    dateType.contains(ApplicationDateType.ARRIVAL)
            )
                "between_start_and_end(daterange(:periodStart, :periodEnd, '[]'), a.sentdate)"
            else null,
            if (searchTerms.isNotBlank()) freeTextQuery else null,
            when (transferApplications) {
                TransferApplicationFilter.TRANSFER_ONLY -> "a.transferApplication"
                TransferApplicationFilter.NO_TRANSFER -> "NOT a.transferApplication"
                else -> null
            },
            when (voucherApplications) {
                VoucherApplicationFilter.VOUCHER_FIRST_CHOICE ->
                    "d.provider_type = 'PRIVATE_SERVICE_VOUCHER'"
                VoucherApplicationFilter.VOUCHER_ONLY ->
                    "pu.preferredUnits && (SELECT array_agg(id) FROM daycare WHERE provider_type = 'PRIVATE_SERVICE_VOUCHER')"
                VoucherApplicationFilter.NO_VOUCHER ->
                    "NOT pu.preferredUnits && (SELECT array_agg(id) FROM daycare WHERE provider_type = 'PRIVATE_SERVICE_VOUCHER')"
                null -> null
            }
        )

    val andWhere =
        conditions.takeIf { it.isNotEmpty() }?.joinToString(" AND ")?.let { " AND $it" } ?: ""
    // language=sql
    val sql =
        """
        SELECT
            a.id,
            child.first_name,
            child.last_name,
            child.date_of_birth,
            child.social_security_number,
            a.document,
            (a.document -> 'serviceNeedOption' ->> 'id')::uuid as serviceNeedId,
            a.document -> 'serviceNeedOption' ->> 'nameFi' as serviceNeedNameFi,
            a.document -> 'serviceNeedOption' ->> 'nameSv' as serviceNeedNameSv,
            a.document -> 'serviceNeedOption' ->> 'nameEn' as serviceNeedNameEn,
            a.document -> 'serviceNeedOption' ->> 'validPlacementType' as serviceNeedValidPlacementType,
            a.duedate,
            (a.document ->> 'preferredStartDate')::date as preferredStartDate,
            a.document -> 'apply' -> 'preferredUnits' as preferredUnits,
            a.type,
            a.status AS application_status,
            a.origin,
            a.transferapplication,
            a.additionaldaycareapplication,
            a.checkedbyadmin,
            a.service_worker_note,
            (
                COALESCE((a.document -> 'additionalDetails' ->> 'dietType'), '') != '' OR
                COALESCE((a.document -> 'additionalDetails' ->> 'otherInfo'), '') != '' OR
                COALESCE((a.document -> 'additionalDetails' ->> 'allergyType'), '') != ''
            ) as additionalInfo,
            (a.document -> 'apply' ->> 'siblingBasis')::boolean as siblingBasis,
            COALESCE(a.document -> 'careDetails' ->> 'assistanceNeeded', a.document -> 'clubCare' ->> 'assistanceNeeded')::boolean as assistanceNeed,
            club_care.was_on_club_care AS was_on_club_care,
            (a.document ->> 'wasOnDaycare')::boolean as wasOnDaycare,
            COALESCE((a.document ->> 'urgent')::boolean, false) as urgent,
            (SELECT COALESCE(array_length(attachments.attachment_ids, 1), 0)) AS attachmentCount,
            COALESCE((a.document ->> 'extendedCare')::boolean, false) as extendedCare,
            has_duplicates,
            pp.unit_confirmation_status,
            pp.unit_reject_reason,
            pp.unit_reject_other_reason,
            pp.start_date AS placement_plan_start_date,
            (CASE WHEN pp.unit_id IS NOT NULL THEN d.name END) AS placement_plan_unit_name,
            cpu.id AS current_placement_unit_id,
            cpu.name AS current_placement_unit_name,
            count(*) OVER () AS total,
            pu.preferredUnits
        FROM application a
        JOIN person child ON child.id = a.child_id
        LEFT JOIN placement_plan pp ON pp.application_id = a.id
        JOIN daycare d ON COALESCE(pp.unit_id, (a.document -> 'apply' -> 'preferredUnits' ->> 0)::uuid) = d.id
        JOIN care_area ca ON d.care_area_id = ca.id
        JOIN (
            SELECT l.id, EXISTS(
              SELECT 1
              FROM application r
              WHERE
                l.child_id = r.child_id
                AND l.id <> r.id
                AND l.status = ANY ('{SENT,WAITING_PLACEMENT,WAITING_CONFIRMATION,WAITING_DECISION,WAITING_MAILING,WAITING_UNIT_CONFIRMATION}'::application_status_type[])
                AND r.status = ANY ('{SENT,WAITING_PLACEMENT,WAITING_CONFIRMATION,WAITING_DECISION,WAITING_MAILING,WAITING_UNIT_CONFIRMATION}'::application_status_type[])
            ) as has_duplicates
            FROM application l
        ) duplicates ON duplicates.id = a.id
        LEFT JOIN (
            SELECT application_id, array_agg(id) AS attachment_ids
            FROM attachment
            WHERE application_id IS NOT NULL
            GROUP by application_id
        ) attachments ON a.id = attachments.application_id
        LEFT JOIN LATERAL (
            SELECT EXISTS( SELECT 1 
            FROM placement p 
            WHERE p.child_id = a.child_id AND p.type = 'CLUB'::placement_type AND p.start_date <= :today) AS was_on_club_care
        ) club_care ON true
        LEFT JOIN LATERAL (
            SELECT daycare.id, daycare.name
            FROM daycare
            JOIN placement ON daycare.id = placement.unit_id
            WHERE placement.child_id = a.child_id AND daterange(start_date, end_date, '[]') && daterange(:today, null, '[]')
            ORDER BY start_date
            LIMIT 1
        ) cpu ON true
        LEFT JOIN LATERAL ( 
            SELECT COALESCE(array_agg(e::UUID) FILTER (WHERE e IS NOT NULL), '{}'::UUID[]) AS preferredUnits
            FROM jsonb_array_elements_text(a.document -> 'apply' -> 'preferredUnits') e
        ) pu ON true
        WHERE a.status != 'CREATED'::application_status_type $andWhere
        """
            .trimIndent()

    val orderedSql =
        when (sortBy) {
            ApplicationSortColumn.APPLICATION_TYPE ->
                "$sql ORDER BY type $sortDir, last_name, first_name"
            ApplicationSortColumn.CHILD_NAME -> "$sql ORDER BY last_name $sortDir, first_name"
            ApplicationSortColumn.DUE_DATE ->
                "$sql ORDER BY duedate $sortDir, last_name, first_name"
            ApplicationSortColumn.START_DATE ->
                "$sql ORDER BY preferredStartDate $sortDir, last_name, first_name"
            ApplicationSortColumn.STATUS ->
                "$sql ORDER BY application_status $sortDir, last_name, first_name"
            ApplicationSortColumn.UNIT_NAME ->
                "$sql ORDER BY d.name $sortDir, last_name, first_name"
        }.exhaust()

    val paginatedSql = "$orderedSql LIMIT $pageSize OFFSET ${(page - 1) * pageSize}"

    val applicationSummaries =
        createQuery(paginatedSql)
            .bind("today", today)
            .addBindings(params)
            .addBindings(freeTextParams)
            .mapToPaged(pageSize, "total") { row ->
                val status = row.mapColumn<ApplicationStatus>("application_status")
                ApplicationSummary(
                    id = row.mapColumn("id"),
                    firstName = row.mapColumn("first_name"),
                    lastName = row.mapColumn("last_name"),
                    socialSecurityNumber = row.mapColumn("social_security_number"),
                    dateOfBirth = row.mapColumn("date_of_birth"),
                    type = row.mapColumn("type"),
                    placementType = mapRequestedPlacementType(row, "document"),
                    serviceNeed =
                        row.mapColumn<ServiceNeedOptionId?>("serviceNeedId")?.let {
                            ServiceNeedOption(
                                it,
                                row.mapColumn("serviceNeedNameFi"),
                                row.mapColumn("serviceNeedNameSv"),
                                row.mapColumn("serviceNeedNameEn"),
                                row.mapColumn("serviceNeedValidPlacementType")
                            )
                        },
                    dueDate = row.mapColumn("duedate"),
                    startDate = row.mapColumn("preferredStartDate"),
                    preferredUnits =
                        row.mapJsonColumn<List<String>>("preferredUnits").map {
                            PreferredUnit(
                                id = DaycareId(UUID.fromString(it)),
                                name = "" // filled afterwards
                            )
                        },
                    origin = row.mapColumn("origin"),
                    checkedByAdmin = row.mapColumn("checkedbyadmin"),
                    status = status,
                    additionalInfo = row.mapColumn("additionalInfo"),
                    serviceWorkerNote =
                        if (canReadServiceWorkerNotes) row.mapColumn("service_worker_note") else "",
                    siblingBasis = row.mapColumn("siblingBasis"),
                    assistanceNeed = row.mapColumn("assistanceNeed"),
                    wasOnClubCare = row.mapColumn("was_on_club_care"),
                    wasOnDaycare = row.mapColumn("wasOnDaycare"),
                    extendedCare = row.mapColumn("extendedCare"),
                    duplicateApplication = row.mapColumn("has_duplicates"),
                    transferApplication = row.mapColumn("transferapplication"),
                    urgent = row.mapColumn("urgent"),
                    attachmentCount = row.mapColumn("attachmentCount"),
                    additionalDaycareApplication = row.mapColumn("additionaldaycareapplication"),
                    placementProposalStatus =
                        row.mapColumn<PlacementPlanConfirmationStatus?>("unit_confirmation_status")
                            ?.takeIf { status == ApplicationStatus.WAITING_UNIT_CONFIRMATION }
                            ?.let {
                                PlacementProposalStatus(
                                    unitConfirmationStatus = it,
                                    unitRejectReason = row.mapColumn("unit_reject_reason"),
                                    unitRejectOtherReason =
                                        row.mapColumn("unit_reject_other_reason")
                                )
                            },
                    placementPlanStartDate = row.mapColumn("placement_plan_start_date"),
                    placementPlanUnitName = row.mapColumn("placement_plan_unit_name"),
                    currentPlacementUnit =
                        row.mapColumn<DaycareId?>("current_placement_unit_id")?.let {
                            PreferredUnit(it, row.mapColumn("current_placement_unit_name"))
                        }
                )
            }

    // language=sql
    val unitSql =
        """
        SELECT id, name
        FROM daycare
        WHERE id = ANY(:unitIds)
        """
            .trimIndent()
    val unitIds =
        applicationSummaries.data.flatMap { summary ->
            summary.preferredUnits.map { unit -> unit.id }
        }
    val unitMap =
        createQuery(unitSql).bind("unitIds", unitIds).toMap {
            columnPair<DaycareId, String>("id", "name")
        }

    return applicationSummaries.copy(
        data =
            applicationSummaries.data.map { summary ->
                summary.copy(
                    preferredUnits =
                        summary.preferredUnits.map {
                            PreferredUnit(id = it.id, name = unitMap.getOrDefault(it.id, ""))
                        }
                )
            }
    )
}

fun Database.Read.fetchApplicationSummariesForGuardian(
    guardianId: PersonId
): List<PersonApplicationSummary> {
    // language=SQL
    val sql =
        """
        SELECT
            a.id AS applicationId,
            a.preferredUnit AS preferredUnitId,
            a.preferredStartDate, a.sentDate, a.type,
            a.childId, a.childName, a.childSsn,
            a.guardianId, concat(p.last_name, ' ', p.first_name) as guardianName,
            coalesce(a.connecteddaycare, false) AS connectedDaycare,
            coalesce(a.preparatoryeducation, false) AS preparatoryEducation,
            d.name AS preferredUnitName,
            a.status
        FROM application_view a
        LEFT JOIN daycare d ON a.preferredUnit = d.id
        LEFT JOIN person p ON a.guardianId = p.id
        WHERE guardianId = :guardianId
        AND status != 'CREATED'::application_status_type
        ORDER BY sentDate DESC
        """
            .trimIndent()

    return createQuery(sql)
        .bind("guardianId", guardianId)
        .mapTo<PersonApplicationSummary>()
        .toList()
}

fun Database.Read.fetchApplicationSummariesForChild(
    childId: ChildId,
    filter: AccessControlFilter<ApplicationId>
): List<PersonApplicationSummary> =
    createQuery<Any> {
            sql(
                """
        SELECT
            a.id AS applicationId,
            a.preferredUnit AS preferredUnitId,
            a.preferredStartDate, a.sentDate, a.type,
            a.childId, a.childName, a.childSsn,
            a.guardianId, concat(p.last_name, ' ', p.first_name) as guardianName,
            coalesce(a.connecteddaycare, false) as connecteddaycare,
            coalesce(a.preparatoryeducation, false) as preparatoryeducation,
            d.name AS preferredUnitName,
            a.status
        FROM application_view a
        LEFT JOIN daycare d ON a.preferredUnit = d.id
        LEFT JOIN person p ON a.guardianId = p.id
        WHERE childId = ${bind(childId)}
        AND ${predicate(filter.forTable("a"))}
        AND status != 'CREATED'::application_status_type
        ORDER BY sentDate DESC
        """
                    .trimIndent()
            )
        }
        .mapTo<PersonApplicationSummary>()
        .toList()

fun Database.Read.fetchApplicationSummariesForCitizen(
    citizenId: PersonId
): List<CitizenApplicationSummary> {
    // language=SQL
    val sql =
        """
        SELECT
            a.id AS application_id, 
            a.type,
            a.childId, a.childName, 
            d.name AS preferred_unit_name,
            COALESCE((SELECT array_agg(name) as other_preferred_units
             FROM daycare JOIN (SELECT unnest(preferredUnits) 
                                FROM application WHERE application.id = a.id) pu ON daycare.id = pu.unnest), '{}'::text[]) AS all_preferred_unit_names,
            a.startDate, a.sentDate, 
            a.status AS application_status,
            a.created AS created_date,
            a.formmodified AS modified_date,
            a.transferapplication
        FROM application_view a
        LEFT JOIN daycare d ON a.preferredUnit = d.id
        WHERE (guardianId = :guardianId OR EXISTS (
            SELECT 1 FROM application_other_guardian WHERE application_id = a.id AND guardian_id = :guardianId
        ))
        AND NOT a.hidefromguardian AND a.status != 'CANCELLED'
        ORDER BY sentDate DESC
        """
            .trimIndent()

    return createQuery(sql)
        .bind("guardianId", citizenId)
        .mapTo<CitizenApplicationSummary>()
        .toList()
}

data class CitizenChildren(
    val id: ChildId,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: LocalDate,
    val socialSecurityNumber: String?,
    val duplicateOf: PersonId?,
)

fun Database.Read.getCitizenChildren(today: LocalDate, citizenId: PersonId): List<CitizenChildren> {
    // language=sql
    val sql =
        """
        SELECT child.id, first_name, last_name, date_of_birth, social_security_number, duplicate_of
        FROM guardian LEFT JOIN person child ON guardian.child_id = child.id
        WHERE guardian_id = :citizenId

        UNION

        SELECT child.id, first_name, last_name, date_of_birth, social_security_number, duplicate_of
        FROM foster_parent JOIN person child ON foster_parent.child_id = child.id
        WHERE parent_id = :citizenId AND valid_during @> :today
        """
            .trimIndent()

    return createQuery(sql)
        .bind("citizenId", citizenId)
        .bind("today", today)
        .mapTo<CitizenChildren>()
        .toList()
}

fun Database.Read.fetchApplicationDetails(
    applicationId: ApplicationId,
    includeCitizenAttachmentsOnly: Boolean = false
): ApplicationDetails? {
    val attachmentWhereClause =
        if (includeCitizenAttachmentsOnly) "WHERE eu.type = 'CITIZEN'" else ""
    // language=sql
    val sql =
        """
        SELECT 
            a.id,
            a.type,
            a.document,
            a.status,
            a.origin,
            a.child_id,
            a.guardian_id,
            a.other_guardian_id,
            c.restricted_details_enabled AS child_restricted,
            g1.restricted_details_enabled AS guardian_restricted,
            g1.residence_code AS guardian_residence_code,
            g1.date_of_death AS guardian_date_of_death,
            a.transferapplication,
            a.additionaldaycareapplication,
            a.hidefromguardian,
            a.created,
            a.updated,
            a.sentdate,
            a.duedate,
            a.duedate_set_manually_at,
            a.checkedbyadmin,
            a.allow_other_guardian_access,
            coalesce(att.json, '[]'::jsonb) attachments
        FROM application a
        LEFT JOIN person c ON c.id = a.child_id
        LEFT JOIN person g1 ON g1.id = a.guardian_id
        LEFT JOIN (
            SELECT application_id, jsonb_agg(jsonb_build_object(
                'id', attachment.id,
                'name', attachment.name,
                'contentType', content_type,
                'updated', updated,
                'receivedAt', received_at,
                'type', attachment.type,
                'uploadedByEmployee', (CASE eu.type WHEN 'EMPLOYEE' THEN eu.id END),
                'uploadedByPerson', (CASE eu.type WHEN 'CITIZEN' THEN eu.id END)
            )) json
            FROM attachment
            JOIN evaka_user eu ON attachment.uploaded_by = eu.id
            $attachmentWhereClause
            GROUP BY application_id
        ) att ON a.id = att.application_id
        WHERE a.id = :id
        """
            .trimIndent()

    val application =
        createQuery(sql).bind("id", applicationId).exactlyOneOrNull {
            val childRestricted = column("child_restricted") ?: false
            val guardianRestricted = column("guardian_restricted") ?: false
            val deserializedForm =
                if (jsonColumn<FormWithType>("document").type == "CLUB") {
                    jsonColumn<ClubFormV0>("document")
                } else {
                    jsonColumn<DaycareFormV0>("document")
                }

            ApplicationDetails(
                id = column("id"),
                type = column("type"),
                form =
                    deserializedForm.let {
                        ApplicationForm.fromV0(it, childRestricted, guardianRestricted)
                    },
                status = column("status"),
                origin = column("origin"),
                childId = column("child_id"),
                guardianId = column("guardian_id"),
                otherGuardianId = column("other_guardian_id"),
                otherGuardianLivesInSameAddress = null,
                childRestricted = childRestricted,
                guardianRestricted = guardianRestricted,
                guardianDateOfDeath = column("guardian_date_of_death"),
                transferApplication = column("transferapplication"),
                additionalDaycareApplication = column("additionaldaycareapplication"),
                createdDate = column("created"),
                modifiedDate = column("updated"),
                sentDate = column("sentdate"),
                dueDate = column("duedate"),
                dueDateSetManuallyAt = column("duedate_set_manually_at"),
                checkedByAdmin = column("checkedbyadmin"),
                hideFromGuardian = column("hidefromguardian"),
                allowOtherGuardianAccess = column("allow_other_guardian_access"),
                attachments = jsonColumn("attachments")
            )
        }

    if (application != null) {
        // language=sql
        val unitSql =
            """
            SELECT id, name
            FROM daycare
            WHERE id = ANY(:unitIds)
            """
                .trimIndent()
        val unitIds = application.form.preferences.preferredUnits.map { it.id }
        val unitMap =
            createQuery(unitSql).bind("unitIds", unitIds).toMap {
                columnPair<DaycareId, String>("id", "name")
            }

        return application.copy(
            form =
                application.form.copy(
                    preferences =
                        application.form.preferences.copy(
                            preferredUnits =
                                application.form.preferences.preferredUnits.map {
                                    PreferredUnit(
                                        id = it.id,
                                        name = unitMap.getOrDefault(it.id, "")
                                    )
                                }
                        )
                )
        )
    } else {
        return null
    }
}

fun Database.Read.getApplicationUnitSummaries(unitId: DaycareId): List<ApplicationUnitSummary> {
    // language=sql
    val sql =
        """
        SELECT
            a.id,
            a.type,
            (a.document -> 'serviceNeedOption' ->> 'id')::uuid as serviceNeedId,
            a.document -> 'serviceNeedOption' ->> 'nameFi' AS serviceNeedNameFi,
            a.document -> 'serviceNeedOption' ->> 'nameSv' AS serviceNeedNameSv,
            a.document -> 'serviceNeedOption' ->> 'nameEn' AS serviceNeedNameEn,
            a.document -> 'serviceNeedOption' ->> 'validPlacementType' AS serviceNeedValidPlacementType,
            a.document,
            (a.document ->> 'preferredStartDate')::date as preferred_start_date,
            (array_position((
                SELECT array_agg(e)
                FROM jsonb_array_elements_text(a.document -> 'apply' -> 'preferredUnits') e
            ), :unitId::text)) as preference_order,
            a.status,
            c.first_name,
            c.last_name,
            c.date_of_birth,
            g.first_name AS guardian_first_name,
            g.last_name AS guardian_last_name,
            g.phone AS guardian_phone,
            g.email AS guardian_email
        FROM application a
        JOIN person c ON c.id = a.child_id
        JOIN person g ON g.id = a.guardian_id
        WHERE EXISTS (
            SELECT 1
            FROM jsonb_array_elements_text(a.document -> 'apply' -> 'preferredUnits') e
            WHERE e = :unitId::text
        ) AND a.status = ANY ('{SENT,WAITING_PLACEMENT,WAITING_DECISION}'::application_status_type[])
        ORDER BY c.last_name, c.first_name
        """
            .trimIndent()

    return createQuery(sql)
        .bind("unitId", unitId)
        .map { row ->
            ApplicationUnitSummary(
                applicationId = row.mapColumn("id"),
                firstName = row.mapColumn("first_name"),
                lastName = row.mapColumn("last_name"),
                dateOfBirth = row.mapColumn("date_of_birth"),
                guardianFirstName = row.mapColumn("guardian_first_name"),
                guardianLastName = row.mapColumn("guardian_last_name"),
                guardianPhone = row.mapColumn("guardian_phone"),
                guardianEmail = row.mapColumn("guardian_email"),
                requestedPlacementType = mapRequestedPlacementType(row, "document"),
                serviceNeed =
                    row.mapColumn<ServiceNeedOptionId?>("serviceNeedId")?.let {
                        ServiceNeedOption(
                            it,
                            row.mapColumn("serviceNeedNameFi"),
                            row.mapColumn("serviceNeedNameSv"),
                            row.mapColumn("serviceNeedNameEn"),
                            row.mapColumn("serviceNeedValidPlacementType")
                        )
                    },
                preferredStartDate = row.mapColumn("preferred_start_date"),
                preferenceOrder = row.mapColumn("preference_order"),
                status = row.mapColumn("status")
            )
        }
        .toList()
}

@JsonIgnoreProperties(ignoreUnknown = true) data class FormWithType(val type: String)

fun mapRequestedPlacementType(row: RowView, colName: String): PlacementType =
    when (row.mapJsonColumn<FormWithType>(colName).type) {
        "CLUB" -> PlacementType.CLUB
        "DAYCARE" -> {
            if (row.mapJsonColumn<DaycareFormV0>(colName).partTime) {
                PlacementType.DAYCARE_PART_TIME
            } else {
                PlacementType.DAYCARE
            }
        }
        "PRESCHOOL" -> {
            row.mapJsonColumn<DaycareFormV0>(colName).let {
                if (it.careDetails.preparatory == true) {
                    if (it.connectedDaycare == true) {
                        PlacementType.PREPARATORY_DAYCARE
                    } else {
                        PlacementType.PREPARATORY
                    }
                } else {
                    if (it.connectedDaycare == true) {
                        it.serviceNeedOption?.validPlacementType ?: PlacementType.PRESCHOOL_DAYCARE
                    } else {
                        PlacementType.PRESCHOOL
                    }
                }
            }
        }
        else -> throw Error("unknown form type")
    }

fun Database.Read.getApplicationType(id: ApplicationId): ApplicationType =
    createQuery(
            """
SELECT type FROM application WHERE id = :id
    """
                .trimIndent()
        )
        .bind("id", id)
        .mapTo<ApplicationType>()
        .exactlyOne()

fun Database.Transaction.updateForm(
    id: ApplicationId,
    form: ApplicationForm,
    formType: ApplicationType,
    childRestricted: Boolean,
    guardianRestricted: Boolean,
    now: HelsinkiDateTime
) {
    check(getApplicationType(id) == formType) { "Invalid form type for the application" }
    val transformedForm =
        if (formType == ApplicationType.CLUB) {
            ClubFormV0.fromForm2(form, childRestricted, guardianRestricted)
        } else {
            DaycareFormV0.fromForm2(form, formType, childRestricted, guardianRestricted)
        }

    // language=SQL
    val sql =
        "UPDATE application SET document = :document, form_modified = :now WHERE id = :applicationId;"

    createUpdate(sql)
        .bind("applicationId", id)
        .bindJson("document", transformedForm)
        .bind("now", now)
        .execute()
}

fun Database.Transaction.setCheckedByAdminToDefault(id: ApplicationId, form: ApplicationForm) {
    // language=SQL
    val sql = "UPDATE application SET checkedbyadmin = :checked WHERE id = :applicationId"

    val default =
        !form.child.assistanceNeeded &&
            form.child.allergies.isBlank() &&
            form.child.diet.isBlank() &&
            form.otherInfo.isBlank()

    createUpdate(sql).bind("applicationId", id).bind("checked", default).execute()
}

fun Database.Transaction.updateApplicationStatus(id: ApplicationId, status: ApplicationStatus) {
    // language=SQL
    val sql = "UPDATE application SET status = :status WHERE id = :id"

    createUpdate(sql).bind("id", id).bind("status", status).execute()
}

fun Database.Transaction.updateApplicationDates(
    id: ApplicationId,
    sentDate: LocalDate,
    dueDate: LocalDate?
) {
    // language=SQL
    val sql = "UPDATE application SET sentdate = :sentDate, duedate = :dueDate WHERE id = :id"

    createUpdate(sql).bind("id", id).bind("sentDate", sentDate).bind("dueDate", dueDate).execute()
}

fun Database.Transaction.updateApplicationFlags(
    id: ApplicationId,
    applicationFlags: ApplicationFlags
) =
    createUpdate(
            """
                UPDATE application
                SET
                    transferapplication = :isTransferApplication,
                    additionaldaycareapplication = :isAdditionalDaycareApplication
                WHERE id = :id
            """
                .trimIndent()
        )
        .bind("id", id)
        .bindKotlin(applicationFlags)
        .execute()

fun Database.Transaction.updateApplicationAllowOtherGuardianAccess(
    id: ApplicationId,
    allowOtherGuardianAccess: Boolean
) =
    createUpdate<DatabaseTable> {
            sql(
                """
                    UPDATE application
                    SET allow_other_guardian_access = ${bind(allowOtherGuardianAccess)}
                    WHERE id = ${bind(id)}
                """
                    .trimIndent()
            )
        }
        .execute()

fun Database.Transaction.updateApplicationOtherGuardian(
    applicationId: ApplicationId,
    otherGuardianId: PersonId?
) {
    // language=SQL
    val sql =
        "UPDATE application SET other_guardian_id = :otherGuardianId WHERE id = :applicationId"

    createUpdate(sql)
        .bind("applicationId", applicationId)
        .bind("otherGuardianId", otherGuardianId)
        .execute()
}

fun Database.Transaction.syncApplicationOtherGuardians(id: ApplicationId) {
    createUpdate<DatabaseTable> {
            sql("DELETE FROM application_other_guardian WHERE application_id = ${bind(id)}")
        }
        .execute()

    createUpdate<DatabaseTable> {
            sql(
                """
            INSERT INTO application_other_guardian (application_id, guardian_id)
            SELECT application.id, other_citizen.id
            FROM application
            JOIN LATERAL (
                SELECT guardian_id AS id
                FROM guardian
                WHERE application.child_id = guardian.child_id AND application.guardian_id != guardian.guardian_id
                AND EXISTS (
                    SELECT 1
                    FROM guardian WHERE application.child_id = guardian.child_id AND guardian.guardian_id = application.guardian_id
                )

                UNION

                SELECT parent_id AS id
                FROM foster_parent
                WHERE application.child_id = foster_parent.child_id AND application.guardian_id != foster_parent.parent_id
                AND EXISTS (
                    SELECT 1
                    FROM foster_parent WHERE application.child_id = foster_parent.child_id AND foster_parent.parent_id = application.guardian_id
                )
            ) other_citizen ON true
            WHERE application.id = ${bind(id)}
        """
                    .trimIndent()
            )
        }
        .execute()
}

fun Database.Transaction.setApplicationVerified(id: ApplicationId, verified: Boolean) {
    // language=SQL
    val sql = "UPDATE application SET checkedByAdmin = :verified WHERE id = :id"

    createUpdate(sql).bind("verified", verified).bind("id", id).execute()
}

fun Database.Transaction.deleteApplication(id: ApplicationId) {
    // language=SQL
    val sql =
        """
        DELETE FROM attachment WHERE application_id = :id;
        DELETE FROM application WHERE id = :id;
        """
            .trimIndent()

    createUpdate(sql).bind("id", id).execute()
}

fun Database.Transaction.removeOldDrafts(
    clock: EvakaClock,
    deleteAttachment: (db: Database.Transaction, id: AttachmentId) -> Unit
) {
    // ~2 months
    val thresholdDays = 60

    // language=SQL
    val applicationIds =
        createQuery<DatabaseTable> {
                sql(
                    "SELECT id FROM application WHERE status = 'CREATED' AND created < ${bind(clock.today())} - ${bind(thresholdDays)}"
                )
            }
            .mapTo<ApplicationId>()
            .toList()

    if (applicationIds.isNotEmpty()) {
        logger.info(
            "Cleaning up ${applicationIds.size} draft applications older than $thresholdDays days"
        )

        // language=SQL
        createUpdate(
                """DELETE FROM application_note WHERE application_id = ANY(:applicationIds::uuid[])"""
            )
            .bind("applicationIds", applicationIds)
            .execute()

        applicationIds.forEach { applicationId ->
            val attachmentIds =
                createUpdate("""DELETE FROM attachment WHERE application_id = :id RETURNING id""")
                    .bind("id", applicationId)
                    .executeAndReturnGeneratedKeys()
                    .mapTo<AttachmentId>()
                    .toList()

            attachmentIds.forEach { attachmentId -> deleteAttachment(this, attachmentId) }
        }

        // language=SQL
        createUpdate("""DELETE FROM application WHERE id = ANY(:applicationIds::uuid[])""")
            .bind("applicationIds", applicationIds)
            .execute()
    }
}

fun Database.Transaction.cancelOutdatedSentTransferApplications(
    clock: EvakaClock
): List<ApplicationId> =
    createUpdate(
            // only include applications that don't have decisions
            // placement type checks are doing in inverse so that the addition and accidental
            // omission of new placement types
            // does not cause the cancellation of applications that shouldn't be cancelled
            """
UPDATE application SET status = :cancelled
WHERE transferapplication
AND status = ANY('{SENT}')
AND NOT EXISTS (
    SELECT 1
    FROM placement p
    WHERE (CASE
        WHEN application.type = 'DAYCARE' THEN NOT p.type = ANY(:notDaycarePlacements::placement_type[])
        WHEN application.type = 'PRESCHOOL' THEN NOT p.type = ANY(:notPreschoolPlacements::placement_type[])
        WHEN application.type = 'CLUB' THEN NOT p.type = ANY(:notClubPlacements::placement_type[])
    END)
    AND daterange((application.document->>'preferredStartDate')::date, null, '[]') && daterange(p.start_date, p.end_date, '[]')
    AND p.end_date >= :yesterday
)
RETURNING id
"""
        )
        .bind("cancelled", ApplicationStatus.CANCELLED)
        .bind("yesterday", clock.today().minusDays(1))
        .bind(
            "notDaycarePlacements",
            arrayOf(
                PlacementType.CLUB,
                PlacementType.PRESCHOOL,
                PlacementType.PRESCHOOL_DAYCARE,
                PlacementType.PRESCHOOL_CLUB,
                PlacementType.PREPARATORY,
                PlacementType.PREPARATORY_DAYCARE,
                PlacementType.TEMPORARY_DAYCARE,
                PlacementType.TEMPORARY_DAYCARE_PART_DAY,
                PlacementType.SCHOOL_SHIFT_CARE
            )
        )
        .bind(
            "notPreschoolPlacements",
            arrayOf(
                PlacementType.CLUB,
                PlacementType.DAYCARE,
                PlacementType.DAYCARE_PART_TIME,
                PlacementType.DAYCARE_FIVE_YEAR_OLDS,
                PlacementType.DAYCARE_PART_TIME_FIVE_YEAR_OLDS,
                PlacementType.TEMPORARY_DAYCARE,
                PlacementType.TEMPORARY_DAYCARE_PART_DAY,
                PlacementType.SCHOOL_SHIFT_CARE
            )
        )
        .bind(
            "notClubPlacements",
            arrayOf(
                PlacementType.DAYCARE,
                PlacementType.DAYCARE_PART_TIME,
                PlacementType.DAYCARE_FIVE_YEAR_OLDS,
                PlacementType.DAYCARE_PART_TIME_FIVE_YEAR_OLDS,
                PlacementType.PRESCHOOL,
                PlacementType.PRESCHOOL_CLUB,
                PlacementType.PRESCHOOL_DAYCARE,
                PlacementType.PREPARATORY,
                PlacementType.PREPARATORY_DAYCARE,
                PlacementType.TEMPORARY_DAYCARE,
                PlacementType.TEMPORARY_DAYCARE_PART_DAY,
                PlacementType.SCHOOL_SHIFT_CARE
            )
        )
        .executeAndReturnGeneratedKeys()
        .mapTo<ApplicationId>()
        .toList()

fun Database.Read.getApplicationAttachments(
    applicationId: ApplicationId
): List<ApplicationAttachment> =
    createQuery(
            """
SELECT
    attachment.id, attachment.name, content_type, updated, received_at, attachment.type,
    (CASE evaka_user.type WHEN 'EMPLOYEE' THEN evaka_user.id END) AS uploaded_by_employee,
    (CASE evaka_user.type WHEN 'CITIZEN' THEN evaka_user.id END) AS uploaded_by_person
FROM attachment
JOIN evaka_user ON attachment.uploaded_by = evaka_user.id
WHERE application_id = :applicationId
"""
        )
        .bind("applicationId", applicationId)
        .mapTo<ApplicationAttachment>()
        .toList()

fun Database.Transaction.cancelAllActiveTransferApplicationsAfterDate(
    childId: ChildId,
    preferredStartDateMinDate: LocalDate
): List<ApplicationId> =
    createUpdate(
            // language=sql
            """
UPDATE application
SET status = 'CANCELLED'
WHERE transferapplication
AND child_id = :childId
AND status = ANY(:activeApplicationStatus::application_status_type[])
AND daterange(:preferredStartDateMinDate::date, null, '[]') @> (document->>'preferredStartDate')::date
RETURNING id
    """
                .trimIndent()
        )
        .bind("activeApplicationStatus", arrayOf(ApplicationStatus.SENT))
        .bind("preferredStartDateMinDate", preferredStartDateMinDate)
        .bind("childId", childId)
        .executeAndReturnGeneratedKeys()
        .mapTo<ApplicationId>()
        .toList()

fun Database.Read.fetchApplicationNotificationCountForCitizen(citizenId: PersonId): Int {
    // language=SQL
    val sql =
        """
        SELECT COUNT(*)
        FROM application_view a
        WHERE guardianId = :guardianId
        AND NOT a.hidefromguardian
        AND NOT EXISTS (
            SELECT 1 FROM guardian_blocklist
            WHERE child_id = a.childId
            AND guardian_id = :guardianId
        )
        AND a.status = 'WAITING_CONFIRMATION'
        """
            .trimIndent()

    return createQuery(sql).bind("guardianId", citizenId).mapTo<Int>().exactlyOne()
}

fun Database.Read.personHasSentApplicationWithId(
    citizenId: PersonId,
    applicationId: ApplicationId
): Boolean {
    val sql =
        """
    SELECT EXISTS (
        SELECT 1
        FROM application app
        WHERE app.guardian_id = :citizenId 
              AND app.id = :applicationId
    )
    """
            .trimIndent()
    return createQuery(sql)
        .bind("citizenId", citizenId)
        .bind("applicationId", applicationId)
        .mapTo<Boolean>()
        .exactlyOne()
}
