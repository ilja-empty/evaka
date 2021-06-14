// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka

import fi.espoo.voltti.logging.loggers.audit
import mu.KotlinLogging

enum class Audit(
    private val eventCode: String,
    private val securityEvent: Boolean = false,
    private val securityLevel: String = "low"
) {
    AbsenceRead("evaka.absence.read"),
    AbsenceDeleteRange("evaka.absence.delete.range"),
    AbsenceDelete("evaka.absence.delete.range"),
    AbsenceUpdate("evaka.absence.update"),
    ApplicationAdminDetailsUpdate("evaka.application.admin-details.update"),
    ApplicationCancel("evaka.application.cancel"),
    ApplicationCreate("evaka.application.create"),
    ApplicationDelete("evaka.application.delete"),
    ApplicationRead("evaka.application.read"),
    ApplicationReadDuplicates("evaka.application.read-duplicates"),
    ApplicationReadActivePlacementsByType("evaka.application.read-active-placements-by-type"),
    ApplicationReturnToSent("evaka.application.return-to-sent"),
    ApplicationReturnToWaitingPlacement("evaka.application.return-to-waiting-placement"),
    ApplicationReturnToWaitingDecision("evaka.application.return-to-waiting-decision"),
    ApplicationSearch("evaka.application.search"),
    ApplicationSend("evaka.application.send"),
    ApplicationUpdate("evaka.application.update"),
    ApplicationVerify("evaka.application.verify"),
    ApplicationsCancelOutdatedTransferApplications("evaka.applications.cancel-outdated-transfer-applications"),
    ApplicationsDeleteDrafts("evaka.application.delete-drafts"),
    ApplicationsReportRead("evaka.applications-report.read"),
    AssistanceActionsReportRead("evaka.assistance-actions-report.read"),
    AssistanceNeedsReportRead("evaka.assistance-needs-report.read"),
    AttachmentsDelete("evaka.attachments.delete"),
    AttachmentsRead("evaka.attachments.read"),
    AttachmentsUpload("evaka.attachments.upload"),
    ChildAbsenceUpdate("evaka.child.absence.update"),
    ChildAdditionalInformationRead("evaka.child.additional-information.read"),
    ChildAdditionalInformationUpdate("evaka.child.additional-information.update"),
    ChildAgeLanguageReportRead("evaka.child-age-language-report.read"),
    ChildAssistanceActionCreate("evaka.child.assistance-action.create"),
    ChildAssistanceActionDelete("evaka.child.assistance-action.delete"),
    ChildAssistanceActionRead("evaka.child.assistance-action.read"),
    ChildAssistanceActionUpdate("evaka.child.assistance-action.update"),
    ChildAssistanceNeedCreate("evaka.child.assistance-need.create"),
    ChildAssistanceNeedDelete("evaka.child.assistance-need.delete"),
    ChildAssistanceNeedRead("evaka.child.assistance-need.read"),
    ChildAssistanceNeedUpdate("evaka.child.assistance-need.update"),
    ChildAttendancesRead("evaka.child-attendances.read"),
    ChildAttendancesArrivalRead("evaka.child-attendances.arrival.read"),
    ChildAttendancesArrivalCreate("evaka.child-attendances.arrival.create"),
    ChildAttendancesDepartureRead("evaka.child-attendances.departure.read"),
    ChildAttendancesDepartureCreate("evaka.child-attendances.departure.create"),
    ChildAttendancesFullDayAbsenceCreate("evaka.child-attendances.full-day-absence.create"),
    ChildAttendancesAbsenceRangeCreate("evaka.child-attendances.absence-range.create"),
    ChildAttendancesReturnToComing("evaka.child-attendances.return-to-coming"),
    ChildAttendancesReturnToPresent("evaka.child-attendances.return-to-present"),
    ChildBackupPickupCreate("evaka.child.backup-pickup.create"),
    ChildBackupPickupDelete("evaka.child.backup-pickup.delete"),
    ChildBackupPickupRead("evaka.child.backup-pickup.read"),
    ChildBackupPickupUpdate("evaka.child.backup-pickup.update"),
    ChildDailyServiceTimesDelete("evaka.child.daily-service-times.delete"),
    ChildDailyServiceTimesEdit("evaka.child.daily-service-times.edit"),
    ChildDailyServiceTimesRead("evaka.child.daily-service-times.read"),
    ChildFeeAlterationsCreate("evaka.child.fee-alterations.create"),
    ChildFeeAlterationsDelete("evaka.child.fee-alterations.delete"),
    ChildFeeAlterationsRead("evaka.child.fee-alterations.read"),
    ChildFeeAlterationsUpdate("evaka.child.fee-alterations.update"),
    ChildrenInDifferentAddressReportRead("evaka.children-in-different-address-report.read"),
    ChildImageDelete("evaka.child.image.delete"),
    ChildImageDownload("evaka.child.image.download"),
    ChildImageUpload("evaka.child.image.upload"),
    ChildSensitiveInfoRead("evaka.child-sensitive-info.read"),
    ChildServiceNeedCreate("evaka.child.service-need.create"),
    ChildServiceNeedDelete("evaka.child.service-need.delete"),
    ChildServiceNeedRead("evaka.child.service-need.read"),
    ChildServiceNeedUpdate("evaka.child.service-need.update"),
    ChildVasuDocumentsRead("evaka.child.vasu-documents.read"),
    ClubPlacementRead("evaka.club-placement.read"),
    ClubTermRead("evaka.club-term.read"),
    DaycareDailyNoteCreate("evaka.daycare-daily-note.create"),
    DaycareDailyNoteUpdate("evaka.daycare-daily-note.update"),
    DaycareDailyNoteRead("evaka.daycare-daily-note.read"),
    DaycareDailyNoteDelete("evaka.daycare-daily-note.delete"),
    DaycareGroupPlacementCreate("evaka.daycare-group-placement.create"),
    DaycareGroupPlacementDelete("evaka.daycare-group-placement.delete"),
    DaycareGroupPlacementTransfer("evaka.daycare-group-placement.transfer"),
    DecisionAccept("evaka.decision.accept"),
    DecisionConfirmMailed("evaka.decision.confirm-mailed"),
    DecisionCreate("evaka.decision.create"),
    DecisionDownloadPdf("evaka.decision.download-pdf"),
    DecisionDraftRead("evaka.decision-draft.read"),
    DecisionDraftUpdate("evaka.decision-draft.update"),
    DecisionRead("evaka.decision.read"),
    DecisionReadByApplication("evaka.decision.read.by-application"),
    DecisionReject("evaka.decision.reject"),
    DecisionsReportRead("evaka.decisions-report.read"),
    DuplicatePeopleReportRead("evaka.duplicate-people-report.read"),
    EmployeeCreate("evaka.employee.create"),
    EmployeeDelete("evaka.employee.delete"),
    EmployeeGetOrCreate("evaka.employee.get-or-create", securityEvent = true, securityLevel = "high"),
    EmployeeRead("evaka.employee.read"),
    EmployeeUpdate("evaka.employee.update"),
    EmployeesRead("evaka.employees.read"),
    EndedPlacementsReportRead("evaka.ended-placements-report.read"),
    FamilyConflictReportRead("evaka.family-conflict-report.read"),
    FamilyContactReportRead("evaka.family-contact-report.read"),
    FamilyContactsRead("evaka.family-contacts.read"),
    FamilyContactsUpdate("evaka.family-contacts.update"),
    FeeDecisionConfirm("evaka.fee-decision.confirm"),
    FeeDecisionGenerate("evaka.fee-decision.generate"),
    FeeDecisionHeadOfFamilyRead("evaka.fee-decision.head-of-family.read"),
    FeeDecisionHeadOfFamilyCreateRetroactive("evaka.fee-decision.head-of-family.create-retroactive"),
    FeeDecisionMarkSent("evaka.fee-decision.mark-sent"),
    FeeDecisionPdfRead("evaka.fee-decision-pdf.read"),
    FeeDecisionRead("evaka.fee-decision.read"),
    FeeDecisionSearch("evaka.fee-decision.search"),
    FeeDecisionSetType("evaka.fee-decision.set-type"),
    FinanceBasicsFeeThresholdsRead("evaka.finance-basics-fee-thresholds.read"),
    FinanceBasicsFeeThresholdsCreate("evaka.finance-basics-fee-thresholds.create"),
    FinanceBasicsFeeThresholdsUpdate("evaka.finance-basics-fee-thresholds.update"),
    InvalidServiceNeedReportRead("evaka.invalid-service-need-report.read"),
    InvoicesCreate("evaka.invoices.create"),
    InvoicesDeleteDrafts("evaka.invoices.delete-drafts"),
    InvoicesMarkSent("evaka.invoices.mark-sent"),
    InvoicesRead("evaka.invoices.read"),
    InvoicesReportRead("evaka.invoices-report.read"),
    InvoicesSearch("evaka.invoices.search"),
    InvoicesSend("evaka.invoices.send"),
    InvoicesSendByDate("evaka.invoices.send-by-date"),
    InvoicesUpdate("evaka.invoices.update"),
    MessagingBlocklistEdit("messaging.blocklist.edit"),
    MessagingBlocklistRead("messaging.blocklist.read"),
    MessagingMyAccountsRead("messaging.messages.my-accounts.read"),
    MessagingUnreadMessagesRead("messaging.messages.unread.read"),
    MessagingMarkMessagesReadWrite("messaging.messages.mark-read.write"),
    MessagingMessageReceiversRead("messaging.message.receivers.read"),
    MessagingReceivedMessagesRead("messaging.messages.received-messages.read"),
    MessagingSentMessagesRead("messaging.messages.sent-messages.read"),
    MessagingNewMessageWrite("messaging.messages.new-message.write"),
    MessagingDraftsRead("messaging.messages.drafts.read"),
    MessagingCreateDraft("messaging.messages.new-draft.write"),
    MessagingUpdateDraft("messaging.messages.update-draft.write"),
    MessagingDeleteDraft("messaging.messages.drafts.delete"),
    MessagingReplyToMessageWrite("messaging.messages.reply-to-message.write"),
    MissingHeadOfFamilyReportRead("evaka.missing-head-of-family-report.read"),
    MissingServiceNeedReportRead("evaka.missing-service-need-report.read"),
    MobileDevicesList("evaka.mobile-devices.list"),
    MobileDevicesRead("evaka.mobile-devices.read"),
    MobileDevicesRename("evaka.mobile-devices.rename"),
    MobileDevicesDelete("evaka.mobile-devices.delete"),
    NoteCreate("evaka.note.create"),
    NoteDelete("evaka.note.delete"),
    NoteRead("evaka.note.read"),
    NoteUpdate("evaka.note.update"),
    OccupancyRead("evaka.occupancy.read"),
    OccupancyReportRead("evaka.occupancy-report.read"),
    PairingInit("pairing.init", securityEvent = true),
    PairingChallenge("pairing.challenge", securityEvent = true),
    PairingResponse("pairing.response", securityEvent = true, securityLevel = "high"),
    PairingValidation("pairing.validation", securityEvent = true, securityLevel = "high"),
    PairingStatusRead("pairing.status.read"),
    ParentShipsCreate("evaka.parentships.create"),
    ParentShipsDelete("evaka.parentships.delete"),
    ParentShipsRead("evaka.parentships.read"),
    ParentShipsRetry("evaka.parentships.retry"),
    ParentShipsUpdate("evaka.parentships.update"),
    PartnerShipsCreate("evaka.partnerships.create"),
    PartnerShipsDelete("evaka.partnerships.delete"),
    PartnerShipsRead("evaka.partnerships.read"),
    PartnerShipsRetry("evaka.partnerships.retry"),
    PartnerShipsUpdate("evaka.partnerships.update"),
    PartnersInDifferentAddressReportRead("evaka.partners-in-different-address-report.read"),
    PersonContactInfoUpdate("evaka.person-contact-info.update", securityEvent = true, securityLevel = "high"),
    PersonCreate("evaka.person.create", securityEvent = true, securityLevel = "high"),
    PersonDelete("evaka.person.delete", securityEvent = true, securityLevel = "high"),
    PersonDependantRead("evaka.person-dependant.read", securityEvent = true, securityLevel = "high"),
    PersonGuardianRead("evaka.person-guardian.read", securityEvent = true, securityLevel = "high"),
    PersonDetailsRead("evaka.person-details.read", securityEvent = true, securityLevel = "high"),
    PersonDetailsSearch("evaka.person-details.search"),
    PersonIncomeCreate("evaka.person.income.create"),
    PersonIncomeDelete("evaka.person.income.delete"),
    PersonIncomeRead("evaka.person.income.read"),
    PersonIncomeUpdate("evaka.person.income.update"),
    PersonMerge("evaka.person.merge", securityEvent = true, securityLevel = "high"),
    PersonUpdate("evaka.person.update", securityEvent = true, securityLevel = "high"),
    PinCodeLockedRead("evaka.pin-locked.read"),
    PinCodeUpdate("evaka.pin.update"),
    PisFamilyRead("evaka.pis-family.read"),
    PlacementCancel("evaka.placement.cancel"),
    PlacementCreate("evaka.placement.create"),
    PlacementSketchingReportRead("evaka.placement-sketching-report.read"),
    PlacementPlanCreate("evaka.placement-plan.create"),
    PlacementPlanRespond("evaka.placement-plan.respond"),
    PlacementPlanDraftRead("evaka.placement-plan-draft.read"),
    PlacementPlanSearch("evaka.placement-plan.search"),
    PlacementProposalCreate("evaka.placement-proposal.create"),
    PlacementProposalAccept("evaka.placement-proposal.accept"),
    PlacementSearch("evaka.placement.search"),
    PlacementUpdate("evaka.placement.update"),
    PlacementServiceNeedCreate("evaka.placement.service-need.create"),
    PlacementServiceNeedDelete("evaka.placement.service-need.delete"),
    PlacementServiceNeedUpdate("evaka.placement.service-need.update"),
    PresenceReportRead("evaka.presence-report.read"),
    RawReportRead("evaka.raw-report.read"),
    ServiceNeedOptionsRead("evaka.service-need-options.read"),
    ServiceNeedReportRead("evaka.service-need-report.read"),
    UnitStaffAttendanceRead("evaka.unit-staff-attendance.read"),
    StaffAttendanceRead("evaka.staff-attendance.read"),
    StaffAttendanceUpdate("evaka.staff-attendance.update"),
    StartingPlacementsReportRead("evaka.starting-placements-report.read"),
    UnitAclCreate("evaka.unit-acl.create"),
    UnitAclDelete("evaka.unit-acl.delete"),
    UnitAclRead("evaka.unit-acl.read"),
    UnitGroupAclUpdate("evaka.unit-group-acl.update"),
    UnitGroupsCreate("evaka.unit.groups.create"),
    UnitGroupsUpdate("evaka.unit.groups.update"),
    UnitGroupsDelete("evaka.unit.groups.delete"),
    UnitGroupsSearch("evaka.unit.groups.search"),
    UnitGroupsCaretakersCreate("evaka.unit.groups.caretakers.create"),
    UnitGroupsCaretakersDelete("evaka.unit.groups.caretakers.delete"),
    UnitGroupsCaretakersRead("evaka.unit.groups.caretakers.read"),
    UnitGroupsCaretakersUpdate("evaka.unit.groups.caretakers.update"),
    UnitCreate("evaka.unit.create"),
    UnitRead("evaka.unit.read"),
    UnitSearch("evaka.unit.search"),
    UnitUpdate("evaka.unit.update"),
    UnitStatisticsCreate("evaka.unit.statistics.create"),
    UnitView("evaka.unit.view"),
    VasuDocumentCreate("evaka.vasu-document.create"),
    VasuDocumentRead("evaka.vasu-document.read"),
    VasuDocumentUpdate("evaka.vasu-document.update"),
    VasuTemplateCreate("evaka.vasu-template.create"),
    VasuTemplateDelete("evaka.vasu-template.delete"),
    VasuTemplateRead("evaka.vasu-template.read"),
    VasuTemplateUpdate("evaka.vasu-template.update"),
    VoucherValueDecisionMarkSent("evaka.value-decision.mark-sent"),
    VoucherValueDecisionRead("evaka.value-decision.read"),
    VoucherValueDecisionSearch("evaka.value-decision.search"),
    VoucherValueDecisionSend("evaka.value-decision.send"),
    VtjBatchSchedule("evaka.vtj.batch-schedule"),
    VtjRequest("evaka.vtj.request", securityEvent = true, securityLevel = "high");

    fun log(targetId: Any? = null, objectId: Any? = null) {
        logger.audit(
            mapOf(
                "eventCode" to eventCode,
                "targetId" to targetId,
                "objectId" to objectId,
                "securityLevel" to securityLevel,
                "securityEvent" to securityEvent
            )
        ) { eventCode }
    }
}

private val logger = KotlinLogging.logger {}
