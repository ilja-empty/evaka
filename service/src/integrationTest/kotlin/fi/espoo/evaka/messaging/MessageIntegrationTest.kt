// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.messaging

import fi.espoo.evaka.FullApplicationTest
import fi.espoo.evaka.attachment.AttachmentsController
import fi.espoo.evaka.pis.service.insertGuardian
import fi.espoo.evaka.shared.AttachmentId
import fi.espoo.evaka.shared.EmployeeId
import fi.espoo.evaka.shared.GroupId
import fi.espoo.evaka.shared.MessageAccountId
import fi.espoo.evaka.shared.MessageDraftId
import fi.espoo.evaka.shared.MessageId
import fi.espoo.evaka.shared.MessageThreadId
import fi.espoo.evaka.shared.async.AsyncJob
import fi.espoo.evaka.shared.async.AsyncJobRunner
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.auth.CitizenAuthLevel
import fi.espoo.evaka.shared.auth.UserRole
import fi.espoo.evaka.shared.auth.insertDaycareAclRow
import fi.espoo.evaka.shared.auth.insertDaycareGroupAcl
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.dev.DevChild
import fi.espoo.evaka.shared.dev.DevDaycare
import fi.espoo.evaka.shared.dev.DevDaycareGroup
import fi.espoo.evaka.shared.dev.DevEmployee
import fi.espoo.evaka.shared.dev.DevPerson
import fi.espoo.evaka.shared.dev.DevPlacement
import fi.espoo.evaka.shared.dev.insertTestCareArea
import fi.espoo.evaka.shared.dev.insertTestChild
import fi.espoo.evaka.shared.dev.insertTestDaycare
import fi.espoo.evaka.shared.dev.insertTestDaycareGroup
import fi.espoo.evaka.shared.dev.insertTestDaycareGroupPlacement
import fi.espoo.evaka.shared.dev.insertTestEmployee
import fi.espoo.evaka.shared.dev.insertTestParentship
import fi.espoo.evaka.shared.dev.insertTestPerson
import fi.espoo.evaka.shared.dev.insertTestPlacement
import fi.espoo.evaka.shared.domain.Forbidden
import fi.espoo.evaka.shared.domain.HelsinkiDateTime
import fi.espoo.evaka.shared.domain.MockEvakaClock
import fi.espoo.evaka.shared.domain.RealEvakaClock
import fi.espoo.evaka.shared.security.PilotFeature
import fi.espoo.evaka.testAdult_1
import fi.espoo.evaka.testAdult_2
import fi.espoo.evaka.testAdult_3
import fi.espoo.evaka.testAdult_4
import fi.espoo.evaka.testAdult_5
import fi.espoo.evaka.testArea
import fi.espoo.evaka.testChild_1
import fi.espoo.evaka.testChild_3
import fi.espoo.evaka.testChild_4
import fi.espoo.evaka.testChild_5
import fi.espoo.evaka.testChild_6
import fi.espoo.evaka.testDaycare
import fi.espoo.evaka.testDaycare2
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockMultipartFile

class MessageIntegrationTest : FullApplicationTest(resetDbBeforeEach = true) {
    @Autowired lateinit var attachmentsController: AttachmentsController
    @Autowired lateinit var messageController: MessageController
    @Autowired lateinit var messageControllerCitizen: MessageControllerCitizen
    @Autowired lateinit var asyncJobRunner: AsyncJobRunner<AsyncJob>

    private val clock = RealEvakaClock()

    private val groupId1 = GroupId(UUID.randomUUID())
    private val groupId2 = GroupId(UUID.randomUUID())
    private val employee1 =
        AuthenticatedUser.Employee(
            id = EmployeeId(UUID.randomUUID()),
            roles = setOf(UserRole.UNIT_SUPERVISOR)
        )
    private val employee2 =
        AuthenticatedUser.Employee(
            id = EmployeeId(UUID.randomUUID()),
            roles = setOf(UserRole.UNIT_SUPERVISOR)
        )
    private val person1 = AuthenticatedUser.Citizen(id = testAdult_1.id, CitizenAuthLevel.STRONG)
    private val person2 = AuthenticatedUser.Citizen(id = testAdult_2.id, CitizenAuthLevel.STRONG)
    private val person3 = AuthenticatedUser.Citizen(id = testAdult_3.id, CitizenAuthLevel.STRONG)
    private val person4 = AuthenticatedUser.Citizen(id = testAdult_4.id, CitizenAuthLevel.STRONG)
    private val person5 = AuthenticatedUser.Citizen(id = testAdult_5.id, CitizenAuthLevel.STRONG)
    private val placementStart = LocalDate.of(2022, 5, 14)
    private val placementEnd = placementStart.plusMonths(1)
    private val sendTime = HelsinkiDateTime.of(placementStart, LocalTime.of(12, 11))
    private val readTime = sendTime.plusSeconds(30)

    private lateinit var employee1Account: MessageAccountId
    private lateinit var employee2Account: MessageAccountId
    private lateinit var group1Account: MessageAccountId
    private lateinit var group2Account: MessageAccountId
    private lateinit var person1Account: MessageAccountId
    private lateinit var person2Account: MessageAccountId
    private lateinit var person3Account: MessageAccountId
    private lateinit var person4Account: MessageAccountId
    private lateinit var person5Account: MessageAccountId

    private fun insertChild(tx: Database.Transaction, child: DevPerson, groupId: GroupId) {
        tx.insertTestPerson(
            DevPerson(id = child.id, firstName = child.firstName, lastName = child.lastName)
        )
        tx.insertTestChild(DevChild(id = child.id))

        val placementId =
            tx.insertTestPlacement(
                DevPlacement(
                    childId = child.id,
                    unitId = testDaycare.id,
                    startDate = placementStart,
                    endDate = placementEnd
                )
            )
        tx.insertTestDaycareGroupPlacement(
            placementId,
            groupId,
            startDate = placementStart,
            endDate = placementEnd
        )
    }

    @BeforeEach
    fun setUp() {
        db.transaction { tx ->
            tx.insertTestCareArea(testArea)
            tx.insertTestDaycare(
                DevDaycare(
                    areaId = testArea.id,
                    id = testDaycare.id,
                    name = testDaycare.name,
                    enabledPilotFeatures = setOf(PilotFeature.MESSAGING)
                )
            )
            tx.insertTestDaycare(
                DevDaycare(
                    areaId = testArea.id,
                    id = testDaycare2.id,
                    name = testDaycare2.name,
                    enabledPilotFeatures = setOf(PilotFeature.MESSAGING)
                )
            )

            fun insertGroup(id: GroupId): MessageAccountId {
                tx.insertTestDaycareGroup(
                    DevDaycareGroup(id = id, daycareId = testDaycare.id, startDate = placementStart)
                )
                return tx.createDaycareGroupMessageAccount(id)
            }
            group1Account = insertGroup(groupId1)
            group2Account = insertGroup(groupId2)

            fun insertPerson(person: DevPerson): MessageAccountId {
                tx.insertTestPerson(person)
                return tx.createPersonMessageAccount(person.id)
            }
            person1Account = insertPerson(testAdult_1)
            person2Account = insertPerson(testAdult_2)
            person3Account = insertPerson(testAdult_3)
            person4Account = insertPerson(testAdult_4)
            person5Account = insertPerson(testAdult_5)

            val fridgeHeadId = person4.id

            // person 1 and 2 are guardians of child 1
            testChild_1.let {
                insertChild(tx, it, groupId1)
                tx.insertGuardian(person1.id, it.id)
                tx.insertGuardian(person2.id, it.id)
                tx.insertTestParentship(
                    fridgeHeadId,
                    it.id
                ) // parentship alone does not allow messaging if not a guardian
            }

            // person 2 and 3 are guardian of child 3
            testChild_3.let {
                insertChild(tx, it, groupId1)
                tx.insertGuardian(person2.id, it.id)
                tx.insertGuardian(person3.id, it.id)
            }

            testChild_4.let {
                insertChild(tx, it, groupId2)
                tx.insertGuardian(person4.id, it.id)
            }

            testChild_5.let {
                insertChild(tx, it, groupId1)
                tx.insertTestParentship(fridgeHeadId, it.id) // no guardian, no messages
            }

            // person 3 and 5 are guardian of child 6
            testChild_6.let {
                insertChild(tx, it, groupId1)
                tx.insertGuardian(person3.id, it.id)
                tx.insertGuardian(person5.id, it.id)
            }

            tx.insertTestEmployee(
                DevEmployee(id = employee1.id, firstName = "Firstname", lastName = "Employee")
            )
            employee1Account = tx.upsertEmployeeMessageAccount(employee1.id)
            tx.insertDaycareAclRow(testDaycare.id, employee1.id, UserRole.UNIT_SUPERVISOR)
            tx.insertDaycareGroupAcl(testDaycare.id, employee1.id, listOf(groupId1, groupId2))

            tx.insertTestEmployee(
                DevEmployee(id = employee2.id, firstName = "Foo", lastName = "Supervisor")
            )
            employee2Account = tx.upsertEmployeeMessageAccount(employee2.id)
            tx.insertDaycareAclRow(testDaycare2.id, employee2.id, UserRole.UNIT_SUPERVISOR)
        }
    }

    @Test
    fun `a thread is created, accessed and replied to by participants who are guardian of the same child`() {
        // when a message thread is created
        postNewThread(
            title = "Juhannus",
            message = "Juhannus tulee pian",
            messageType = MessageType.MESSAGE,
            sender = employee1Account,
            recipients = listOf(MessageRecipient(MessageRecipientType.CHILD, testChild_1.id)),
            user = employee1
        )

        // then sender does not see it in received messages
        assertEquals(listOf(), getMessageThreads(employee1Account, employee1))

        // then recipient can see it in received messages
        val threadWithOneReply = getMessageThreads(person1)[0]
        assertEquals("Juhannus", threadWithOneReply.title)
        assertEquals(MessageType.MESSAGE, threadWithOneReply.type)
        assertEquals(
            listOf(Pair(employee1Account, "Juhannus tulee pian")),
            threadWithOneReply.toSenderContentPairs()
        )

        // when
        replyToMessage(
            person1,
            threadWithOneReply.messages[0].id,
            setOf(employee1Account, person2Account),
            "No niinpä näyttää tulevan"
        )

        // then recipients see the same data
        val person2Threads = getMessageThreads(person2)
        assertEquals(getMessageThreads(person1), person2Threads)
        assertEquals(getMessageThreads(employee1Account, employee1), person2Threads)

        // then thread has both messages in correct order
        assertEquals(1, person2Threads.size)
        val person2Thread = person2Threads[0]
        assertEquals("Juhannus", person2Thread.title)
        assertEquals(
            listOf(
                Pair(employee1Account, "Juhannus tulee pian"),
                Pair(person1Account, "No niinpä näyttää tulevan")
            ),
            person2Thread.toSenderContentPairs()
        )

        // when person one replies to the employee only
        replyToMessage(
            person1,
            person2Thread.messages.last().id,
            setOf(employee1Account),
            "person 2 does not see this"
        )

        // then person one and employee see the new message
        val threadContentWithTwoReplies =
            listOf(
                Pair(employee1Account, "Juhannus tulee pian"),
                Pair(person1Account, "No niinpä näyttää tulevan"),
                Pair(person1Account, "person 2 does not see this")
            )
        assertEquals(
            threadContentWithTwoReplies,
            getMessageThreads(person1)[0].toSenderContentPairs()
        )
        assertEquals(
            threadContentWithTwoReplies,
            getMessageThreads(employee1Account, employee1)[0].toSenderContentPairs()
        )

        // then person two does not see the message
        assertEquals(
            listOf(
                Pair(employee1Account, "Juhannus tulee pian"),
                Pair(person1Account, "No niinpä näyttää tulevan")
            ),
            getMessageThreads(person2)[0].toSenderContentPairs()
        )

        // when author replies to person two
        replyToMessage(
            user = employee1,
            sender = employee1Account,
            messageId = threadWithOneReply.messages.last().id,
            recipientAccountIds = setOf(person2Account),
            content = "person 1 does not see this"
        )

        // then person two sees that
        assertEquals(
            listOf(
                Pair(employee1Account, "Juhannus tulee pian"),
                Pair(person1Account, "No niinpä näyttää tulevan"),
                Pair(employee1Account, "person 1 does not see this")
            ),
            getMessageThreads(person2)[0].toSenderContentPairs()
        )

        // then person one does not see that
        assertEquals(
            threadContentWithTwoReplies,
            getMessageThreads(person1)[0].toSenderContentPairs()
        )

        // then employee sees all the messages
        assertEquals(
            listOf(
                Pair(employee1Account, "Juhannus tulee pian"),
                Pair(person1Account, "No niinpä näyttää tulevan"),
                Pair(person1Account, "person 2 does not see this"),
                Pair(employee1Account, "person 1 does not see this")
            ),
            getMessageThreads(employee1Account, employee1)[0].toSenderContentPairs()
        )

        // then employee can see all sent messages
        assertEquals(
            setOf(
                Pair("person 1 does not see this", setOf(person2Account)),
                Pair("Juhannus tulee pian", setOf(person1Account, person2Account))
            ),
            getSentMessages(employee1Account, employee1)
                .map { it.toContentRecipientsPair() }
                .toSet()
        )
    }

    @Test
    fun `guardian can send a message only to group and other guardian, not group staff`() {
        fun getRecipients() = getCitizenReceivers(person1).messageAccounts.map { it.id }.toSet()

        assertEquals(setOf(group1Account, person2Account, employee1Account), getRecipients())

        // When a supervisor works as staff, her account is deactivated
        db.transaction { it.deactivateEmployeeMessageAccount(employee1.id) }
        assertEquals(setOf(group1Account, person2Account), getRecipients())
    }

    @Test
    fun `a message is split to several threads by guardianship`() {
        // when a new thread is created to several recipients who do not all have common children
        val title = "Thread splitting"
        val content = "This message is sent to several participants and split to threads"
        val recipients =
            listOf(
                MessageRecipient(MessageRecipientType.CHILD, testChild_1.id),
                MessageRecipient(MessageRecipientType.CHILD, testChild_4.id),
                MessageRecipient(MessageRecipientType.CHILD, testChild_6.id)
            )
        val recipientNames = listOf("Hippiäiset", "Jani")
        postNewThread(
            title = title,
            message = content,
            messageType = MessageType.MESSAGE,
            sender = employee1Account,
            recipients = recipients,
            recipientNames = recipientNames,
            user = employee1
        )

        // then three threads should be created
        db.read {
            assertEquals(
                1,
                it.createQuery("SELECT COUNT(id) FROM message_content").mapTo<Int>().one()
            )
            assertEquals(
                3,
                it.createQuery("SELECT COUNT(id) FROM message_thread").mapTo<Int>().one()
            )
            assertEquals(3, it.createQuery("SELECT COUNT(id) FROM message").mapTo<Int>().one())
            assertEquals(
                5,
                it.createQuery("SELECT COUNT(id) FROM message_recipients").mapTo<Int>().one()
            )
        }

        // then sent message is shown as one
        val sentMessages = db.read { it.getMessagesSentByAccount(employee1Account, 10, 1) }
        assertEquals(1, sentMessages.total)
        assertEquals(1, sentMessages.data.size)
        assertEquals(recipientNames, sentMessages.data.flatMap { it.recipientNames })
        assertEquals(
            setOf(person1Account, person2Account, person3Account, person4Account, person5Account),
            sentMessages.data.flatMap { msg -> msg.recipients.map { it.id } }.toSet()
        )
        assertEquals(title, sentMessages.data[0].threadTitle)
        assertEquals(MessageType.MESSAGE, sentMessages.data[0].type)
        assertEquals(content, sentMessages.data[0].content)

        // then threads are grouped properly
        // person 1 and 2: common child
        // person 2 and 3: common child
        // person 4: no child
        val person1Threads = getMessageThreads(person1)
        val person2Threads = getMessageThreads(person2)
        val person3Threads = getMessageThreads(person3)
        val person4Threads = getMessageThreads(person4)
        val person5Threads = getMessageThreads(person5)

        assertEquals(1, person1Threads.size)
        assertEquals(1, person2Threads.size)
        assertEquals(1, person3Threads.size)
        assertEquals(1, person4Threads.size)
        assertEquals(1, person5Threads.size)
        assertEquals(person1Threads, person2Threads)
        assertEquals(person3Threads, person5Threads)
        assertNotEquals(person1Threads, person3Threads)
        assertNotEquals(person1Threads, person4Threads)
        assertNotEquals(person3Threads, person4Threads)

        val allThreads =
            listOf(person1Threads, person2Threads, person3Threads, person4Threads, person5Threads)
                .flatten()
        assertEquals(5, allThreads.size)
        allThreads.forEach {
            assertEquals(title, it.title)
            assertEquals(content, it.messages[0].content)
        }

        // when person 1 replies to thread
        replyToMessage(
            person1,
            person1Threads.first().messages.first().id,
            setOf(employee1Account, person2Account),
            "Hello"
        )

        // then only the participants should get the message
        val employeeThreads = getMessageThreads(employee1Account, employee1)
        assertEquals(
            listOf(Pair(employee1Account, content), Pair(person1Account, "Hello")),
            employeeThreads.map { it.toSenderContentPairs() }.flatten()
        )
        assertEquals(employeeThreads, getMessageThreads(person1))
        assertEquals(
            listOf(Pair(employee1Account, content), Pair(person1Account, "Hello")),
            getMessageThreads(person2).map { it.toSenderContentPairs() }.flatten()
        )

        assertEquals(person3Threads, getMessageThreads(person3))
        assertEquals(person4Threads, getMessageThreads(person4))
    }

    @Test
    fun `a bulletin cannot be replied to by the recipients`() {
        // when a bulletin thread is created
        postNewThread(
            title = "Tiedote",
            message = "Juhannus tulee pian",
            messageType = MessageType.BULLETIN,
            sender = employee1Account,
            recipients = listOf(MessageRecipient(MessageRecipientType.CHILD, testChild_1.id)),
            user = employee1
        )

        // then the recipient can see it
        val thread = getMessageThreads(person1).first()
        assertEquals("Tiedote", thread.title)
        assertEquals(MessageType.BULLETIN, thread.type)
        assertEquals(
            listOf(Pair(employee1Account, "Juhannus tulee pian")),
            thread.toSenderContentPairs()
        )

        // when the recipient tries to reply to the bulletin, it is denied
        assertThrows<Forbidden> {
            replyToMessage(
                user = person1,
                messageId = thread.messages.first().id,
                recipientAccountIds = setOf(thread.messages.first().sender.id),
                content = "Kiitos tiedosta"
            )
        }

        // when the author himself replies to the bulletin, it succeeds
        //
        // NOTE: This will not be implemented for now, because author
        //       replying to their own message (without other replies)
        //       lacks spec. It would be bad UX to only allow replies
        //       to own bulletin only. (Date 25.11.2021)
        replyToMessage(
            sender = employee1Account,
            user = employee1,
            messageId = thread.messages.last().id,
            recipientAccountIds = setOf(person1Account),
            content = "Nauttikaa siitä"
        )

        // then the recipient can see it
        assertEquals(
            listOf(
                Pair(employee1Account, "Juhannus tulee pian"),
                Pair(employee1Account, "Nauttikaa siitä")
            ),
            getMessageThreads(person1).first().toSenderContentPairs()
        )
    }

    @Test
    fun `messages can be marked read`() {
        // when a message thread is created
        postNewThread(
            title = "t1",
            message = "m1",
            messageType = MessageType.MESSAGE,
            sender = employee1Account,
            recipients = listOf(MessageRecipient(MessageRecipientType.CHILD, testChild_1.id)),
            user = employee1
        )

        // then
        val person1UnreadMessages = getUnreadReceivedMessages(person1Account, person1)
        assertEquals(1, person1UnreadMessages.size)
        assertEquals(1, getUnreadReceivedMessages(person2Account, person2).size)
        assertEquals(0, getUnreadReceivedMessages(employee1Account, employee1).size)

        // when a person replies to the thread
        replyToMessage(
            person1,
            person1UnreadMessages.first().id,
            setOf(employee1Account, person2Account),
            "reply"
        )

        // then
        assertEquals(1, getUnreadReceivedMessages(employee1Account, employee1).size)
        assertEquals(1, getUnreadReceivedMessages(person1Account, person1).size)
        assertEquals(2, getUnreadReceivedMessages(person2Account, person2).size)

        // when a thread is marked read
        markThreadRead(person2, getMessageThreads(person2).first().id)

        // then the thread is marked read
        assertEquals(1, getUnreadReceivedMessages(employee1Account, employee1).size)
        assertEquals(1, getUnreadReceivedMessages(person1Account, person1).size)
        assertEquals(0, getUnreadReceivedMessages(person2Account, person2).size)
    }

    @Test
    fun `messages can have attachments`() {
        val draftId = db.transaction { it.initDraft(employee1Account) }

        assertEquals(1, db.read { it.getDrafts(employee1Account) }.size)

        // when an attachment it uploaded
        val attachmentId = uploadMessageAttachment(employee1, draftId)

        // then another employee cannot read or delete the attachment
        assertThrows<Forbidden> {
            attachmentsController.getAttachment(
                dbInstance(),
                employee2,
                clock,
                attachmentId,
                "evaka-logo.png"
            )
        }
        assertThrows<Forbidden> {
            attachmentsController.deleteAttachmentHandler(
                dbInstance(),
                employee2,
                clock,
                attachmentId
            )
        }

        // then the author can read and delete the attachment
        attachmentsController.getAttachment(
            dbInstance(),
            employee1,
            clock,
            attachmentId,
            "evaka-logo.png"
        )
        attachmentsController.deleteAttachmentHandler(dbInstance(), employee1, clock, attachmentId)

        // a user cannot upload attachments to another user's draft
        assertThrows<Forbidden> { uploadMessageAttachment(employee2, draftId) }

        val attachmentIds =
            setOf(
                uploadMessageAttachment(employee1, draftId),
                uploadMessageAttachment(employee1, draftId)
            )

        // when a message thread with attachment is created
        postNewThread(
            title = "t1",
            message = "m1",
            messageType = MessageType.MESSAGE,
            sender = employee1Account,
            recipients = listOf(MessageRecipient(MessageRecipientType.CHILD, testChild_1.id)),
            user = employee1,
            attachmentIds = attachmentIds,
            draftId = draftId
        )

        // then
        // the draft is deleted
        assertEquals(0, db.read { it.getDrafts(employee1Account) }.size)

        // the attachments are associated to a message
        assertEquals(
            2,
            db.read {
                it.createQuery(
                        "SELECT COUNT(*) FROM attachment WHERE message_content_id IS NOT NULL"
                    )
                    .mapTo<Int>()
                    .one()
            }
        )

        // the author can read the attachment
        attachmentsController.getAttachment(
            dbInstance(),
            employee1,
            clock,
            attachmentIds.first(),
            "evaka-logo.png"
        )
        // another employee cannot read the attachment
        assertThrows<Forbidden> {
            attachmentsController.getAttachment(
                dbInstance(),
                employee2,
                clock,
                attachmentIds.first(),
                "evaka-logo.png"
            )
        }

        // the recipient can read the attachment
        val threads = getMessageThreads(person1)
        assertEquals(1, threads.size)
        val messages = threads.first().messages
        assertEquals(1, messages.size)
        val receivedAttachments = messages.first().attachments
        assertEquals(attachmentIds, receivedAttachments.map { it.id }.toSet())
        attachmentsController.getAttachment(
            dbInstance(),
            person1,
            clock,
            attachmentIds.first(),
            "evaka-logo.png"
        )

        // another citizen cannot read the attachment
        assertThrows<Forbidden> {
            attachmentsController.getAttachment(
                dbInstance(),
                person3,
                clock,
                attachmentIds.first(),
                "evaka-logo.png"
            )
        }
    }

    @Test
    fun `employee with access to two groups cannot send messages as group1 to group2`() {
        postNewThread(
            title = "Juhannus",
            message = "Juhannus tulee pian",
            messageType = MessageType.MESSAGE,
            sender = group1Account,
            recipients = listOf(MessageRecipient(MessageRecipientType.GROUP, groupId2)),
            user = employee1,
        )
        assertEquals(0, getMessageThreads(person4).size)

        postNewThread(
            title = "Juhannus",
            message = "Juhannus tulee pian",
            messageType = MessageType.MESSAGE,
            sender = group2Account,
            recipients = listOf(MessageRecipient(MessageRecipientType.GROUP, groupId2)),
            user = employee1,
        )
        assertEquals(1, getMessageThreads(person4).size)
    }

    @Test
    fun `employee with access to two groups cannot send messages as group1 to child in group2`() {
        postNewThread(
            title = "Juhannus",
            message = "Juhannus tulee pian",
            messageType = MessageType.MESSAGE,
            sender = group1Account,
            recipients = listOf(MessageRecipient(MessageRecipientType.CHILD, testChild_4.id)),
            user = employee1,
        )
        assertEquals(0, getMessageThreads(person4).size)

        postNewThread(
            title = "Juhannus",
            message = "Juhannus tulee pian",
            messageType = MessageType.MESSAGE,
            sender = group2Account,
            recipients = listOf(MessageRecipient(MessageRecipientType.CHILD, testChild_4.id)),
            user = employee1,
        )
        assertEquals(1, getMessageThreads(person4).size)
    }

    @Test
    fun `unread message counts and marking messages read`() {
        postNewThread(
            title = "Juhannus",
            message = "Juhannus tulee pian",
            messageType = MessageType.MESSAGE,
            sender = employee1Account,
            recipients = listOf(MessageRecipient(MessageRecipientType.CHILD, testChild_1.id)),
            user = employee1
        )
        assertEquals(0, unreadMessagesCount(employee1Account, employee1))
        assertEquals(1, unreadMessagesCount(person1))
        assertEquals(1, unreadMessagesCount(person2))

        // citizen reads the message
        markThreadRead(person1, getMessageThreads(person1).first().id)
        assertEquals(0, unreadMessagesCount(employee1Account, employee1))
        assertEquals(0, unreadMessagesCount(person1))
        assertEquals(1, unreadMessagesCount(person2))

        // thread is replied
        replyToMessage(
            user = person1,
            messageId = getMessageThreads(person1).first().messages.last().id,
            recipientAccountIds = setOf(person2Account, employee1Account),
            content = "Juhannus on jo ohi"
        )
        assertEquals(1, unreadMessagesCount(employee1Account, employee1))
        assertEquals(0, unreadMessagesCount(person1))
        assertEquals(2, unreadMessagesCount(person2))
    }

    private fun getUnreadReceivedMessages(
        accountId: MessageAccountId,
        user: AuthenticatedUser.Citizen
    ) =
        getMessageThreads(user).flatMap {
            it.messages.filter { m -> m.sender.id != accountId && m.readAt == null }
        }

    private fun getUnreadReceivedMessages(
        accountId: MessageAccountId,
        user: AuthenticatedUser.Employee
    ) =
        getMessageThreads(accountId, user).flatMap {
            it.messages.filter { m -> m.sender.id != accountId && m.readAt == null }
        }

    private fun uploadMessageAttachment(
        user: AuthenticatedUser.Employee,
        draftId: MessageDraftId
    ): AttachmentId =
        attachmentsController.uploadMessageAttachment(
            dbInstance(),
            user,
            clock,
            draftId,
            MockMultipartFile("evaka-logo.png", "evaka-logo.png", null, pngFile.readBytes())
        )

    private fun postNewThread(
        title: String,
        message: String,
        messageType: MessageType,
        sender: MessageAccountId,
        recipients: List<MessageRecipient>,
        recipientNames: List<String> = listOf(),
        user: AuthenticatedUser.Employee,
        attachmentIds: Set<AttachmentId> = setOf(),
        draftId: MessageDraftId? = null,
    ) {
        messageController.createMessage(
            dbInstance(),
            user,
            MockEvakaClock(sendTime),
            sender,
            MessageController.PostMessageBody(
                title = title,
                content = message,
                type = messageType,
                recipients = recipients.toSet(),
                recipientNames = recipientNames,
                attachmentIds = attachmentIds,
                draftId = draftId,
                urgent = false
            )
        )
        asyncJobRunner.runPendingJobsSync(MockEvakaClock(readTime))
    }

    private fun replyToMessage(
        user: AuthenticatedUser.Citizen,
        messageId: MessageId,
        recipientAccountIds: Set<MessageAccountId>,
        content: String
    ) {
        messageControllerCitizen.replyToThread(
            dbInstance(),
            user,
            MockEvakaClock(sendTime),
            messageId,
            ReplyToMessageBody(content = content, recipientAccountIds = recipientAccountIds)
        )
        asyncJobRunner.runPendingJobsSync(MockEvakaClock(readTime))
    }

    private fun replyToMessage(
        user: AuthenticatedUser.Employee,
        sender: MessageAccountId,
        messageId: MessageId,
        recipientAccountIds: Set<MessageAccountId>,
        content: String
    ) {
        messageController.replyToThread(
            dbInstance(),
            user,
            MockEvakaClock(sendTime),
            sender,
            messageId,
            ReplyToMessageBody(content = content, recipientAccountIds = recipientAccountIds)
        )
        asyncJobRunner.runPendingJobsSync(MockEvakaClock(readTime))
    }

    private fun markThreadRead(user: AuthenticatedUser.Citizen, threadId: MessageThreadId) {
        messageControllerCitizen.markThreadRead(
            dbInstance(),
            user,
            MockEvakaClock(readTime),
            threadId
        )
    }

    private fun getMessageThreads(
        user: AuthenticatedUser.Citizen,
    ): List<MessageThread> {
        return messageControllerCitizen
            .getReceivedMessages(
                dbInstance(),
                user,
                MockEvakaClock(readTime),
                page = 1,
                pageSize = 100
            )
            .data
    }

    private fun getMessageThreads(
        accountId: MessageAccountId,
        user: AuthenticatedUser.Employee,
    ): List<MessageThread> {
        return messageController
            .getReceivedMessages(
                dbInstance(),
                user,
                MockEvakaClock(readTime),
                accountId,
                page = 1,
                pageSize = 100
            )
            .data
    }

    private fun getSentMessages(
        accountId: MessageAccountId,
        user: AuthenticatedUser.Employee
    ): List<SentMessage> {
        return messageController
            .getSentMessages(
                dbInstance(),
                user,
                MockEvakaClock(readTime),
                accountId,
                page = 1,
                pageSize = 100
            )
            .data
    }

    private fun getCitizenReceivers(
        user: AuthenticatedUser.Citizen
    ): MessageControllerCitizen.GetReceiversResponse {
        return messageControllerCitizen.getReceivers(dbInstance(), user, MockEvakaClock(readTime))
    }

    private fun unreadMessagesCount(user: AuthenticatedUser.Citizen): Int {
        return messageControllerCitizen
            .getUnreadMessages(dbInstance(), user, MockEvakaClock(readTime))
            .first()
            .unreadCount
    }

    private fun unreadMessagesCount(
        accountId: MessageAccountId,
        user: AuthenticatedUser.Employee
    ): Int {
        return messageController
            .getUnreadMessages(dbInstance(), user, MockEvakaClock(readTime))
            .find { it.accountId == accountId }
            ?.unreadCount
            ?: throw Exception("No unread count for account $accountId")
    }
}

fun MessageThread.toSenderContentPairs(): List<Pair<MessageAccountId, String>> =
    this.messages.map { Pair(it.sender.id, it.content) }

fun SentMessage.toContentRecipientsPair(): Pair<String, Set<MessageAccountId>> =
    Pair(this.content, this.recipients.map { it.id }.toSet())
