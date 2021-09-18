// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.attachment

import fi.espoo.evaka.Audit
import fi.espoo.evaka.BucketEnv
import fi.espoo.evaka.EvakaEnv
import fi.espoo.evaka.application.ApplicationStateService
import fi.espoo.evaka.incomestatement.isOwnIncomeStatement
import fi.espoo.evaka.s3.DocumentService
import fi.espoo.evaka.s3.DocumentWrapper
import fi.espoo.evaka.s3.checkFileContentType
import fi.espoo.evaka.shared.ApplicationId
import fi.espoo.evaka.shared.AttachmentId
import fi.espoo.evaka.shared.IncomeStatementId
import fi.espoo.evaka.shared.MessageDraftId
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.auth.UserRole
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.domain.BadRequest
import fi.espoo.evaka.shared.domain.Forbidden
import fi.espoo.evaka.shared.domain.NotFound
import fi.espoo.evaka.shared.security.AccessControl
import fi.espoo.evaka.shared.security.Action
import org.jdbi.v3.core.kotlin.mapTo
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.services.s3.model.S3Exception
import java.util.UUID

@RestController
@RequestMapping("/attachments")
class AttachmentsController(
    private val documentClient: DocumentService,
    private val stateService: ApplicationStateService,
    private val accessControl: AccessControl,
    evakaEnv: EvakaEnv,
    bucketEnv: BucketEnv
) {
    private val filesBucket = bucketEnv.attachments
    private val maxAttachmentsPerUser = evakaEnv.maxAttachmentsPerUser

    @PostMapping("/applications/{applicationId}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadApplicationAttachmentEmployee(
        db: Database,
        user: AuthenticatedUser,
        @PathVariable applicationId: ApplicationId,
        @RequestParam type: AttachmentType,
        @RequestPart("file") file: MultipartFile
    ): AttachmentId {
        Audit.AttachmentsUploadForApplication.log(applicationId)
        user.requireOneOfRoles(UserRole.ADMIN, UserRole.SERVICE_WORKER)
        return handleFileUpload(db, user, AttachToApplication(applicationId), file, type).also {
            db.transaction { tx -> stateService.reCalculateDueDate(tx, applicationId) }
        }
    }

    @PostMapping("/income-statements/{incomeStatementId}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadIncomeStatementAttachmentEmployee(
        db: Database,
        user: AuthenticatedUser,
        @PathVariable incomeStatementId: IncomeStatementId,
        @RequestPart("file") file: MultipartFile
    ): AttachmentId {
        Audit.AttachmentsUploadForIncomeStatement.log(incomeStatementId)
        accessControl.requirePermissionFor(user, Action.IncomeStatement.UPLOAD_EMPLOYEE_ATTACHMENT, incomeStatementId)
        user.requireOneOfRoles(UserRole.ADMIN, UserRole.FINANCE_ADMIN)
        return handleFileUpload(db, user, AttachToIncomeStatement(incomeStatementId), file)
    }

    @PostMapping("/messages/{draftId}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadMessageAttachment(
        db: Database,
        user: AuthenticatedUser,
        @PathVariable draftId: MessageDraftId,
        @RequestPart("file") file: MultipartFile
    ): AttachmentId {
        Audit.AttachmentsUploadForMessage.log()
        accessControl.requirePermissionFor(user, Action.Global.UPLOAD_MESSAGE_ATTACHMENT)
        return handleFileUpload(db, user, AttachToMessageDraft(draftId), file)
    }

    @PostMapping("/citizen/applications/{applicationId}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadApplicationAttachmentCitizen(
        db: Database,
        user: AuthenticatedUser,
        @PathVariable applicationId: ApplicationId,
        @RequestParam type: AttachmentType,
        @RequestPart("file") file: MultipartFile
    ): AttachmentId {
        Audit.AttachmentsUploadForApplication.log(applicationId)
        user.requireOneOfRoles(UserRole.END_USER)

        if (!db.read { it.isOwnApplication(applicationId, user) }) throw Forbidden("Permission denied")

        val attachTo = AttachToApplication(applicationId)
        checkAttachmentCount(db, attachTo, user)

        return handleFileUpload(db, user, attachTo, file, type)
            .also { db.transaction { stateService.reCalculateDueDate(it, applicationId) } }
    }

    @PostMapping(
        value = ["/citizen/income-statements/{incomeStatementId}", "/citizen/income-statements"],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun uploadAttachmentCitizen(
        db: Database,
        user: AuthenticatedUser,
        @PathVariable(required = false) incomeStatementId: IncomeStatementId?,
        @RequestPart("file") file: MultipartFile
    ): AttachmentId {
        Audit.AttachmentsUploadForIncomeStatement.log(incomeStatementId)
        user.requireOneOfRoles(UserRole.END_USER)

        if (incomeStatementId != null && !db.read { it.isOwnIncomeStatement(incomeStatementId, user.id) }) throw Forbidden("Permission denied")

        val attachTo = if (incomeStatementId != null) AttachToIncomeStatement(incomeStatementId) else AttachToNothing
        checkAttachmentCount(db, attachTo, user)

        return handleFileUpload(db, user, attachTo, file, null)
    }

    private fun checkAttachmentCount(db: Database, attachTo: AttachTo, user: AuthenticatedUser) {
        val count = db.read {
            when (attachTo) {
                is AttachToNothing -> it.userUnparentedAttachmentCount(user.id)
                is AttachToApplication -> it.userApplicationAttachmentCount(attachTo.applicationId, user.id)
                is AttachToIncomeStatement -> it.userIncomeStatementAttachmentCount(attachTo.incomeStatementId, user.id)
                is AttachToMessageDraft -> 0
            }
        }
        if (count >= maxAttachmentsPerUser) {
            throw Forbidden("Too many uploaded files for ${user.id}: $maxAttachmentsPerUser")
        }
    }

    private fun handleFileUpload(
        db: Database,
        user: AuthenticatedUser,
        attachTo: AttachTo,
        file: MultipartFile,
        type: AttachmentType? = null
    ): AttachmentId {
        val name = file.originalFilename
            ?.takeIf { it.isNotBlank() }
            ?: throw BadRequest("Filename missing")

        val contentType = file.contentType ?: throw BadRequest("Missing content type")
        checkFileContentType(file.inputStream, contentType)

        val id = AttachmentId(UUID.randomUUID())
        db.transaction { tx ->
            tx.insertAttachment(
                id,
                name,
                contentType,
                attachTo,
                uploadedByEnduser = user.id.takeIf { user.isEndUser },
                uploadedByEmployee = user.id.takeUnless { user.isEndUser },
                type = type
            )
            documentClient.upload(
                filesBucket,
                DocumentWrapper(
                    name = id.toString(),
                    bytes = file.bytes
                ),
                contentType
            )
        }

        return id
    }

    @GetMapping("/{attachmentId}/download")
    fun getAttachment(
        db: Database,
        user: AuthenticatedUser,
        @PathVariable attachmentId: AttachmentId
    ): ResponseEntity<ByteArray> {
        Audit.AttachmentsRead.log(targetId = attachmentId)
        db.authorizeAttachmentDownload(user, attachmentId)

        val attachment =
            db.read { it.getAttachment(attachmentId) ?: throw NotFound("Attachment $attachmentId not found") }

        return try {
            documentClient.get(filesBucket, "$attachmentId").let { document ->
                ResponseEntity.ok()
                    .header("Content-Disposition", "attachment;filename=${attachment.name}")
                    .contentType(MediaType.valueOf(attachment.contentType))
                    .body(document.getBytes())
            }
        } catch (e: S3Exception) {
            if (e.statusCode() == 403) throw NotFound("Attachment $attachmentId not available, scanning in progress or failed")
            throw e
        }
    }

    @DeleteMapping("/citizen/{id}")
    fun deleteAttachmentCitizen(db: Database, user: AuthenticatedUser, @PathVariable id: AttachmentId): ResponseEntity<Unit> {
        Audit.AttachmentsDelete.log(targetId = id)
        user.requireOneOfRoles(UserRole.END_USER)
        if (!db.read { it.isOwnAttachment(id, user) }) throw Forbidden("Permission denied")

        db.transaction { deleteAttachment(it, id) }

        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}")
    fun deleteAttachmentEmployee(db: Database, user: AuthenticatedUser, @PathVariable id: AttachmentId): ResponseEntity<Unit> {
        Audit.AttachmentsDelete.log(targetId = id)
        user.requireOneOfRoles(UserRole.ADMIN, UserRole.SERVICE_WORKER)
        if (!db.read { it.isSelfUploadedAttachment(id, user) }) throw Forbidden("Permission denied")

        db.transaction { deleteAttachment(it, id) }

        return ResponseEntity.noContent().build()
    }

    fun deleteAttachment(db: Database.Transaction, id: AttachmentId) {
        db.deleteAttachment(id)
        documentClient.delete(filesBucket, "$id")
    }
}

private fun Database.authorizeAttachmentDownload(user: AuthenticatedUser, attachmentId: AttachmentId) {
    if (user.hasOneOfRoles(UserRole.ADMIN, UserRole.SERVICE_WORKER, UserRole.FINANCE_ADMIN)) return
    if (user.hasOneOfRoles(UserRole.END_USER) && read { it.isOwnAttachment(attachmentId, user) }) return
    if (read { it.userHasSupervisorRights(user, attachmentId) }) return

    throw NotFound("Attachment $attachmentId not found")
}

fun Database.Read.isOwnApplication(applicationId: ApplicationId, user: AuthenticatedUser): Boolean {
    return this.createQuery("SELECT 1 FROM application WHERE id = :id AND guardian_id = :userId")
        .bind("id", applicationId)
        .bind("userId", user.id)
        .mapTo<Int>()
        .any()
}

fun Database.Read.userHasSupervisorRights(user: AuthenticatedUser, attachmentId: AttachmentId): Boolean {
    return this.createQuery(
        """
SELECT 1
FROM attachment
JOIN placement_plan ON attachment.application_id = placement_plan.application_id
JOIN daycare ON placement_plan.unit_id = daycare.id AND daycare.round_the_clock
JOIN daycare_acl_view ON daycare.id = daycare_acl_view.daycare_id
WHERE employee_id = :userId
    AND role = :unitSupervisor
    AND attachment.id = :attachmentId
    AND attachment.type = :extendedCare
"""
    )
        .bind("userId", user.id)
        .bind("unitSupervisor", UserRole.UNIT_SUPERVISOR)
        .bind("attachmentId", attachmentId)
        .bind("extendedCare", AttachmentType.EXTENDED_CARE)
        .mapTo<Int>()
        .any()
}
