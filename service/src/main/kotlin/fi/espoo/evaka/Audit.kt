// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka

import fi.espoo.voltti.logging.loggers.audit
import mu.KotlinLogging

enum class Audit(
    private val securityEvent: Boolean = false,
    private val securityLevel: String = "low"
) {
    AbsenceCitizenCreate,
    AbsenceRead,
    AbsenceDelete,
    AbsenceDeleteRange,
    AbsenceUpsert,
    ApplicationAdminDetailsUpdate,
    ApplicationCancel,
    ApplicationConfirmDecisionsMailed,
    ApplicationCreate,
    ApplicationDelete,
    ApplicationRead,
    ApplicationReadNotifications,
    ApplicationReadDuplicates,
    ApplicationReadActivePlacementsByType,
    ApplicationReturnToSent,
    ApplicationReturnToWaitingPlacement,
    ApplicationReturnToWaitingDecision,
    ApplicationSearch,
    ApplicationSend,
    ApplicationSendDecisionsWithoutProposal,
    ApplicationUpdate,
    ApplicationVerify,
    ApplicationsReportRead,
    AssistanceActionOptionsRead,
    AssistanceBasisOptionsRead,
    AssistanceFactorCreate,
    AssistanceFactorUpdate,
    AssistanceFactorDelete,
    AssistanceNeedDecisionsListCitizen,
    AssistanceNeedDecisionsReportRead,
    AssistanceNeedDecisionsReportUnreadCount,
    AssistanceNeedPreschoolDecisionsListCitizen,
    AssistanceNeedsReportRead,
    AttachmentsDelete,
    AttachmentsRead,
    AttachmentsUploadForApplication,
    AttachmentsUploadForIncome,
    AttachmentsUploadForIncomeStatement,
    AttachmentsUploadForMessageDraft,
    AttachmentsUploadForPedagogicalDocument,
    AttendanceReservationCitizenCreate,
    AttendanceReservationCitizenRead,
    AttendanceReservationDelete,
    AttendanceReservationEmployeeCreate,
    AttendanceReservationReportRead,
    BackupCareDelete,
    BackupCareUpdate,
    CalendarEventCreate,
    CalendarEventDelete,
    CalendarEventUpdate,
    ChildAdditionalInformationRead,
    ChildAdditionalInformationUpdate,
    ChildAgeLanguageReportRead,
    ChildAssistanceActionCreate,
    ChildAssistanceActionDelete,
    ChildAssistanceActionUpdate,
    ChildAssistanceNeedCreate,
    ChildAssistanceNeedDelete,
    ChildAssistanceNeedUpdate,
    ChildAssistanceNeedDecisionAnnul,
    ChildAssistanceNeedDecisionCreate,
    ChildAssistanceNeedDecisionDelete,
    ChildAssistanceNeedDecisionDownloadCitizen,
    ChildAssistanceNeedDecisionGetUnreadCountCitizen,
    ChildAssistanceNeedDecisionMarkReadCitizen,
    ChildAssistanceNeedDecisionRead,
    ChildAssistanceNeedDecisionReadDecisionMakerOptions,
    ChildAssistanceNeedDecisionReadCitizen,
    ChildAssistanceNeedDecisionUpdate,
    ChildAssistanceNeedDecisionsList,
    ChildAssistanceNeedDecisionSend,
    ChildAssistanceNeedDecisionRevertToUnsent,
    ChildAssistanceNeedDecisionDecide,
    ChildAssistanceNeedDecisionOpened,
    ChildAssistanceNeedDecisionUpdateDecisionMaker,
    ChildAssistanceNeedPreschoolDecisionAnnul,
    ChildAssistanceNeedPreschoolDecisionCreate,
    ChildAssistanceNeedPreschoolDecisionDelete,
    ChildAssistanceNeedPreschoolDecisionDownloadCitizen,
    ChildAssistanceNeedPreschoolDecisionGetUnreadCountCitizen,
    ChildAssistanceNeedPreschoolDecisionMarkReadCitizen,
    ChildAssistanceNeedPreschoolDecisionRead,
    ChildAssistanceNeedPreschoolDecisionReadDecisionMakerOptions,
    ChildAssistanceNeedPreschoolDecisionReadCitizen,
    ChildAssistanceNeedPreschoolDecisionUpdate,
    ChildAssistanceNeedPreschoolDecisionsList,
    ChildAssistanceNeedPreschoolDecisionSend,
    ChildAssistanceNeedPreschoolDecisionRevertToUnsent,
    ChildAssistanceNeedPreschoolDecisionDecide,
    ChildAssistanceNeedPreschoolDecisionOpened,
    ChildAssistanceNeedPreschoolDecisionUpdateDecisionMaker,
    ChildAssistanceNeedVoucherCoefficientCreate,
    ChildAssistanceNeedVoucherCoefficientRead,
    ChildAssistanceNeedVoucherCoefficientUpdate,
    ChildAssistanceNeedVoucherCoefficientDelete,
    ChildAttendanceChildrenRead,
    ChildAttendanceStatusesRead,
    ChildAttendancesUpsert,
    ChildAttendancesArrivalCreate,
    ChildAttendancesDepartureRead,
    ChildAttendancesDepartureCreate,
    ChildAttendancesFullDayAbsenceCreate,
    ChildAttendancesAbsenceRangeCreate,
    ChildAttendancesReturnToComing,
    ChildAttendancesReturnToPresent,
    ChildBackupCareCreate,
    ChildBackupCareRead,
    ChildBackupPickupCreate,
    ChildBackupPickupDelete,
    ChildBackupPickupRead,
    ChildBackupPickupUpdate,
    ChildConsentsRead,
    ChildConsentsReadCitizen,
    ChildConsentsReadNotificationsCitizen,
    ChildConsentsInsertCitizen,
    ChildConsentsUpdate,
    ChildDailyNoteCreate,
    ChildDailyNoteUpdate,
    ChildDailyNoteDelete,
    ChildDailyServiceTimesDelete,
    ChildDailyServiceTimesEdit,
    ChildDailyServiceTimesRead,
    ChildDailyServiceTimeNotificationsRead,
    ChildDailyServiceTimeNotificationsDismiss,
    ChildDocumentCreate,
    ChildDocumentDelete,
    ChildDocumentPublish,
    ChildDocumentRead,
    ChildDocumentUnpublish,
    ChildDocumentUpdateContent,
    ChildFeeAlterationsCreate,
    ChildFeeAlterationsDelete,
    ChildFeeAlterationsRead,
    ChildFeeAlterationsUpdate,
    ChildrenInDifferentAddressReportRead,
    ChildImageDelete,
    ChildImageDownload,
    ChildImageUpload,
    ChildSensitiveInfoRead,
    ChildStickyNoteCreate,
    ChildStickyNoteUpdate,
    ChildStickyNoteDelete,
    ChildVasuDocumentsRead,
    ChildVasuDocumentsReadByGuardian,
    CitizenChildrenRead(securityEvent = true, securityLevel = "high"),
    CitizenChildServiceNeedRead(securityEvent = true, securityLevel = "high"),
    CitizenChildDailyServiceTimeRead(securityEvent = true, securityLevel = "high"),
    CitizenLogin(securityEvent = true, securityLevel = "high"),
    DaycareAssistanceCreate,
    DaycareAssistanceUpdate,
    DaycareAssistanceDelete,
    DaycareGroupPlacementCreate,
    DaycareGroupPlacementDelete,
    DaycareGroupPlacementTransfer,
    DaycareBackupCareRead,
    DecisionAccept,
    DecisionDownloadPdf,
    DecisionDraftRead,
    DecisionDraftUpdate,
    DecisionRead,
    DecisionReadByApplication,
    DecisionReject,
    DecisionsReportRead,
    DuplicatePeopleReportRead,
    DocumentTemplateCopy,
    DocumentTemplateCreate,
    DocumentTemplateDelete,
    DocumentTemplatePublish,
    DocumentTemplateRead,
    DocumentTemplateUpdateContent,
    DocumentTemplateUpdateValidity,
    EmployeeCreate(securityEvent = true, securityLevel = "high"),
    EmployeeDelete(securityEvent = true, securityLevel = "high"),
    EmployeeGetOrCreate(securityEvent = true, securityLevel = "high"),
    EmployeeLogin(securityEvent = true, securityLevel = "high"),
    EmployeeRead(securityEvent = true),
    EmployeeUpdate(securityEvent = true, securityLevel = "high"),
    EmployeePreferredFirstNameRead,
    EmployeePreferredFirstNameUpdate,
    EmployeesRead(securityEvent = true),
    EndedPlacementsReportRead,
    FamilyConflictReportRead,
    FamilyContactReportRead,
    FamilyContactsRead,
    FamilyContactsUpdate,
    FamilyDaycareMealReport,
    FeeDecisionConfirm,
    FeeDecisionGenerate,
    FeeDecisionHeadOfFamilyRead,
    FeeDecisionHeadOfFamilyCreateRetroactive,
    FeeDecisionMarkSent,
    FeeDecisionPdfRead,
    FeeDecisionRead,
    FeeDecisionSearch,
    FeeDecisionSetType,
    FinanceBasicsFeeThresholdsRead,
    FinanceBasicsFeeThresholdsCreate,
    FinanceBasicsFeeThresholdsUpdate,
    FinanceDecisionHandlersRead,
    FosterParentCreateRelationship,
    FosterParentDeleteRelationship,
    FosterParentReadChildren,
    FosterParentReadParents,
    FosterParentUpdateRelationship,
    GuardianChildrenRead,
    GroupNoteCreate,
    GroupNoteUpdate,
    GroupNoteDelete,
    GroupNoteRead,
    HolidayPeriodCreate,
    HolidayPeriodRead,
    HolidayPeriodDelete,
    HolidayPeriodsList,
    HolidayPeriodUpdate,
    HolidayQuestionnairesList,
    HolidayQuestionnaireRead,
    HolidayQuestionnaireCreate,
    HolidayQuestionnaireUpdate,
    HolidayQuestionnaireDelete,
    HolidayAbsenceCreate,
    IncomeExpirationDatesRead,
    IncomeStatementCreate,
    IncomeStatementCreateForChild,
    IncomeStatementDelete,
    IncomeStatementDeleteOfChild,
    IncomeStatementReadOfPerson,
    IncomeStatementReadOfChild,
    IncomeStatementUpdate,
    IncomeStatementUpdateForChild,
    IncomeStatementUpdateHandled,
    IncomeStatementsAwaitingHandler,
    IncomeStatementsOfPerson,
    IncomeStatementsOfChild,
    IncomeStatementStartDates,
    IncomeStatementStartDatesOfChild,
    InvoiceCorrectionsCreate,
    InvoiceCorrectionsDelete,
    InvoiceCorrectionsNoteUpdate,
    InvoiceCorrectionsRead,
    InvoicesCreate,
    InvoicesDeleteDrafts,
    InvoicesMarkSent,
    InvoicesRead,
    InvoicesReportRead,
    InvoicesSearch,
    InvoicesSend,
    InvoicesSendByDate,
    InvoicesUpdate,
    ManualDuplicationReportRead,
    MessagingBlocklistEdit,
    MessagingBlocklistRead,
    MessagingMyAccountsRead,
    MessagingUnreadMessagesRead,
    MessagingMarkMessagesReadWrite,
    MessagingArchiveMessageWrite,
    MessagingMessageReceiversRead,
    MessagingReceivedMessagesRead,
    MessagingMessagesInFolderRead,
    MessagingSentMessagesRead,
    MessagingNewMessageWrite,
    MessagingDraftsRead,
    MessagingCreateDraft,
    MessagingUpdateDraft,
    MessagingDeleteDraft,
    MessagingReplyToMessageWrite,
    MessagingCitizenFetchReceiversForAccount,
    MessagingCitizenSendMessage,
    MessagingUndoMessage,
    MessagingUndoMessageReply,
    MessagingMessageThreadRead,
    MissingHeadOfFamilyReportRead,
    MissingServiceNeedReportRead,
    MobileDevicesList,
    MobileDevicesRead,
    MobileDevicesRename,
    MobileDevicesDelete,
    NoteCreate,
    NoteDelete,
    NoteRead,
    NoteUpdate,
    NotesByGroupRead,
    OccupancyGroupReportRead,
    OccupancyRead,
    OccupancyReportRead,
    OccupancySpeculatedRead,
    OtherAssistanceMeasureCreate,
    OtherAssistanceMeasureUpdate,
    OtherAssistanceMeasureDelete,
    PairingInit(securityEvent = true),
    PairingChallenge(securityEvent = true),
    PairingResponse(securityEvent = true, securityLevel = "high"),
    PairingValidation(securityEvent = true, securityLevel = "high"),
    PairingStatusRead,
    ParentShipsCreate,
    ParentShipsDelete,
    ParentShipsRead,
    ParentShipsRetry,
    ParentShipsUpdate,
    PartnerShipsCreate,
    PartnerShipsDelete,
    PartnerShipsRead,
    PartnerShipsRetry,
    PartnerShipsUpdate,
    PartnersInDifferentAddressReportRead,
    PatuReportSend,
    PaymentsCreate,
    PaymentsSend,
    PedagogicalDocumentCreate(securityEvent = true, securityLevel = "high"),
    PedagogicalDocumentCountUnread,
    PedagogicalDocumentReadByGuardian(securityEvent = true, securityLevel = "high"),
    PedagogicalDocumentRead(securityEvent = true, securityLevel = "high"),
    PedagogicalDocumentUpdate(securityEvent = true, securityLevel = "high"),
    PersonalDataUpdate(securityEvent = true, securityLevel = "high"),
    PersonCreate(securityEvent = true, securityLevel = "high"),
    PersonDelete(securityEvent = true, securityLevel = "high"),
    PersonDependantRead(securityEvent = true, securityLevel = "high"),
    PersonGuardianRead(securityEvent = true, securityLevel = "high"),
    PersonBlockedGuardiansRead(securityEvent = true, securityLevel = "high"),
    PersonDetailsRead(securityEvent = true, securityLevel = "high"),
    PersonDetailsSearch,
    PersonDuplicate,
    PersonIncomeCreate,
    PersonIncomeDelete,
    PersonIncomeRead,
    PersonIncomeUpdate,
    PersonIncomeNotificationRead,
    PersonMerge(securityEvent = true, securityLevel = "high"),
    PersonUpdate(securityEvent = true, securityLevel = "high"),
    PersonUpdateEvakaRights(securityEvent = true, securityLevel = "high"),
    PersonVtjFamilyUpdate,
    PinCodeLockedRead,
    PinCodeUpdate,
    PinLogin,
    PisFamilyRead,
    PlacementCancel,
    PlacementCountReportRead,
    PlacementCreate,
    PlacementSketchingReportRead,
    PlacementPlanCreate,
    PlacementPlanRespond,
    PlacementPlanDraftRead,
    PlacementPlanSearch,
    PlacementProposalCreate,
    PlacementProposalAccept,
    PlacementSearch,
    PlacementUpdate,
    PlacementServiceNeedCreate,
    PlacementServiceNeedDelete,
    PlacementServiceNeedUpdate,
    PlacementTerminate,
    PlacementChildPlacementPeriodsRead,
    PreschoolAssistanceCreate,
    PreschoolAssistanceUpdate,
    PreschoolAssistanceDelete,
    PresenceReportRead,
    PushSubscriptionUpsert,
    RawReportRead,
    ServiceNeedOptionsRead,
    ServiceNeedReportRead,
    SettingsRead,
    SettingsUpdate,
    ServiceWorkerNoteUpdate,
    SextetReportRead,
    UnitStaffAttendanceRead,
    StaffAttendanceArrivalCreate,
    StaffAttendanceArrivalExternalCreate,
    StaffAttendanceDepartureCreate,
    StaffAttendanceDepartureExternalCreate,
    StaffAttendanceRead,
    StaffAttendanceUpdate,
    StaffAttendanceDelete,
    StaffAttendanceExternalDelete,
    StaffAttendanceExternalUpdate,
    StaffOccupancyCoefficientRead,
    StaffOccupancyCoefficientUpsert,
    StartingPlacementsReportRead,
    TemporaryEmployeesRead,
    TemporaryEmployeeCreate,
    TemporaryEmployeeRead,
    TemporaryEmployeeUpdate,
    TemporaryEmployeeDeleteAcl,
    TemporaryEmployeeDelete,
    UnitAclCreate,
    UnitAclDelete,
    UnitAclRead,
    UnitApplicationsRead,
    UnitAttendanceReservationsRead,
    UnitCalendarEventsRead,
    UnitFeaturesRead,
    UnitFeaturesUpdate,
    UnitGroupAclUpdate,
    UnitGroupsCreate,
    UnitGroupsUpdate,
    UnitGroupsDelete,
    UnitGroupsSearch,
    UnitGroupsCaretakersCreate,
    UnitGroupsCaretakersDelete,
    UnitGroupsCaretakersRead,
    UnitGroupsCaretakersUpdate,
    UnitCreate,
    UnitCounters,
    UnitRead,
    UnitSearch,
    UnitUpdate,
    UnitView,
    VardaReportRead,
    VasuDocumentCreate,
    VasuDocumentRead,
    VasuDocumentReadByGuardian,
    VasuDocumentGivePermissionToShareByGuardian,
    VasuDocumentUpdate,
    VasuDocumentEventCreate,
    VasuTemplateCreate,
    VasuTemplateCopy,
    VasuTemplateEdit,
    VasuTemplateDelete,
    VasuTemplateRead,
    VasuTemplateUpdate,
    VoucherValueDecisionHeadOfFamilyCreateRetroactive,
    VoucherValueDecisionHeadOfFamilyRead,
    VoucherValueDecisionMarkSent,
    VoucherValueDecisionPdfRead,
    VoucherValueDecisionRead,
    VoucherValueDecisionSearch,
    VoucherValueDecisionSend,
    VoucherValueDecisionSetType,
    VtjRequest(securityEvent = true, securityLevel = "high");

    private val eventCode = name

    class UseNamedArguments private constructor()

    fun log(
        // This is a hack to force passing all real parameters by name
        @Suppress("UNUSED_PARAMETER") vararg forceNamed: UseNamedArguments,
        targetId: Any? = null,
        objectId: Any? = null,
        meta: Map<String, Any?> = emptyMap()
    ) {
        logger.audit(
            mapOf(
                "eventCode" to eventCode,
                "targetId" to targetId,
                "objectId" to objectId,
                "securityLevel" to securityLevel,
                "securityEvent" to securityEvent,
            ) + if (meta.isNotEmpty()) mapOf("meta" to meta) else emptyMap()
        ) {
            eventCode
        }
    }
}

private val logger = KotlinLogging.logger {}
