// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.messaging

import fi.espoo.evaka.attachment.MessageAttachment
import fi.espoo.evaka.shared.AttachmentId
import fi.espoo.evaka.shared.ChildId
import fi.espoo.evaka.shared.DaycareId
import fi.espoo.evaka.shared.GroupId
import fi.espoo.evaka.shared.Id
import fi.espoo.evaka.shared.MessageAccountId
import fi.espoo.evaka.shared.MessageContentId
import fi.espoo.evaka.shared.MessageId
import fi.espoo.evaka.shared.MessageRecipientId
import fi.espoo.evaka.shared.MessageThreadId
import fi.espoo.evaka.shared.Paged
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.domain.EvakaClock
import fi.espoo.evaka.shared.domain.HelsinkiDateTime
import fi.espoo.evaka.shared.mapToPaged
import mu.KotlinLogging
import org.jdbi.v3.core.mapper.Nested
import org.jdbi.v3.json.Json
import java.time.LocalDate

val logger = KotlinLogging.logger {}

fun Database.Read.getUnreadMessagesCounts(accountIds: Set<MessageAccountId>): Set<UnreadCountByAccount> {
    // language=SQL
    val sql = """
        SELECT 
            acc.id as account_id,
            SUM(CASE WHEN mr.id IS NOT NULL AND mr.read_at IS NULL AND NOT mt.is_copy THEN 1 ELSE 0 END) as unread_count,
            SUM(CASE WHEN mr.id IS NOT NULL AND mr.read_at IS NULL AND mt.is_copy THEN 1 ELSE 0 END) as unread_copy_count
        FROM message_account acc
        LEFT JOIN message_recipients mr ON mr.recipient_id = acc.id
        LEFT JOIN message m ON mr.message_id = m.id
        LEFT JOIN message_thread mt ON m.thread_id = mt.id
        WHERE acc.id = ANY(:accountIds)
        GROUP BY acc.id
    """.trimIndent()

    return this.createQuery(sql)
        .bind("accountIds", accountIds)
        .mapTo<UnreadCountByAccount>().toSet()
}

fun Database.Read.getUnreadMessagesCountsByDaycare(daycareId: DaycareId): Set<UnreadCountByAccountAndGroup> {
    // language=SQL
    val sql = """
        SELECT
            acc.id as account_id,
            acc.daycare_group_id as group_id,
            SUM(CASE WHEN mr.id IS NOT NULL AND mr.read_at IS NULL AND NOT mt.is_copy THEN 1 ELSE 0 END) as unread_count,
            SUM(CASE WHEN mr.id IS NOT NULL AND mr.read_at IS NULL AND mt.is_copy THEN 1 ELSE 0 END) as unread_copy_count
        FROM message_account acc
        LEFT JOIN message_recipients mr ON mr.recipient_id = acc.id
        LEFT JOIN message m ON mr.message_id = m.id
        LEFT JOIN message_thread mt ON m.thread_id = mt.id
        JOIN daycare_group dg ON acc.daycare_group_id = dg.id AND dg.daycare_id = :daycareId
        WHERE acc.active = true
        GROUP BY acc.id, acc.daycare_group_id
    """.trimIndent()

    return this.createQuery(sql)
        .bind("daycareId", daycareId)
        .mapTo<UnreadCountByAccountAndGroup>().toSet()
}

fun Database.Transaction.markThreadRead(clock: EvakaClock, accountId: MessageAccountId, threadId: MessageThreadId): Int {
    // language=SQL
    val sql = """
UPDATE message_recipients rec
SET read_at = :now
FROM message msg
WHERE rec.message_id = msg.id
  AND msg.thread_id = :threadId
  AND rec.recipient_id = :accountId
  AND read_at IS NULL;
    """.trimIndent()

    return this.createUpdate(sql)
        .bind("now", clock.now())
        .bind("accountId", accountId)
        .bind("threadId", threadId)
        .execute()
}

fun Database.Transaction.insertMessage(
    now: HelsinkiDateTime,
    contentId: MessageContentId,
    threadId: MessageThreadId,
    sender: MessageAccountId,
    recipientNames: List<String>,
    repliesToMessageId: MessageId? = null,
): MessageId {
    // language=SQL
    val insertMessageSql = """
        INSERT INTO message (content_id, thread_id, sender_id, sender_name, replies_to, sent_at, recipient_names)
        SELECT :contentId, :threadId, :senderId, name_view.account_name, :repliesToId, :now, :recipientNames
        FROM message_account_name_view name_view
        WHERE name_view.id = :senderId
        RETURNING id
    """.trimIndent()
    return createQuery(insertMessageSql)
        .bind("now", now)
        .bind("contentId", contentId)
        .bind("threadId", threadId)
        .bind("repliesToId", repliesToMessageId)
        .bind("senderId", sender)
        .bind("recipientNames", recipientNames)
        .mapTo<MessageId>()
        .one()
}

fun Database.Transaction.insertMessageContent(
    content: String,
    sender: MessageAccountId
): MessageContentId {
    // language=SQL
    val messageContentSql = "INSERT INTO message_content (content, author_id) VALUES (:content, :authorId) RETURNING id"
    return createQuery(messageContentSql)
        .bind("content", content)
        .bind("authorId", sender)
        .mapTo<MessageContentId>()
        .one()
}

fun Database.Transaction.insertRecipients(
    recipientAccountIds: Set<MessageAccountId>,
    messageId: MessageId,
) {
    // language=SQL
    val insertRecipientsSql =
        "INSERT INTO message_recipients (message_id, recipient_id) VALUES (:messageId, :accountId)"

    val batch = this.prepareBatch(insertRecipientsSql)
    recipientAccountIds.forEach { batch.bind("messageId", messageId).bind("accountId", it).add() }
    batch.execute()
}

fun Database.Transaction.insertMessageThreadChildren(
    childIds: Set<ChildId>,
    threadId: MessageThreadId,
) {
    // language=SQL
    val insertChildrenSql =
        "INSERT INTO message_thread_children (thread_id, child_id) VALUES (:threadId, :childId)"

    val batch = this.prepareBatch(insertChildrenSql)
    childIds.forEach { batch.bind("threadId", threadId).bind("childId", it).add() }
    batch.execute()
}

fun Database.Transaction.upsertThreadParticipants(
    threadId: MessageThreadId,
    senderId: MessageAccountId,
    receiverIds: Set<MessageAccountId>,
    now: HelsinkiDateTime,
) {
    this.createUpdate(
        """
        INSERT INTO message_thread_participant as tp (thread_id, participant_id, last_message_timestamp, last_sent_timestamp)
        VALUES (:threadId, :accountId, :now, :now)
        ON CONFLICT (thread_id, participant_id) DO UPDATE SET last_message_timestamp = :now, last_sent_timestamp = :now
    """
    )
        .bind("threadId", threadId)
        .bind("accountId", senderId)
        .bind("now", now)
        .execute()

    val batch = this.prepareBatch(
        """
        INSERT INTO message_thread_participant as tp (thread_id, participant_id, last_message_timestamp, last_received_timestamp)
        VALUES (:threadId, :accountId, :now, :now)
        ON CONFLICT (thread_id, participant_id) DO UPDATE SET last_message_timestamp = :now, last_received_timestamp = :now
    """
    )
    receiverIds.forEach {
        batch
            .bind("threadId", threadId)
            .bind("accountId", it)
            .bind("now", now)
            .add()
    }
    batch.execute()
}

fun Database.Transaction.insertThread(
    type: MessageType,
    title: String,
    urgent: Boolean,
    isCopy: Boolean
): MessageThreadId {
    // language=SQL
    val insertThreadSql = "INSERT INTO message_thread (message_type, title, urgent, is_copy) VALUES (:messageType, :title, :urgent, :isCopy) RETURNING id"
    return createQuery(insertThreadSql)
        .bind("messageType", type)
        .bind("title", title)
        .bind("urgent", urgent)
        .bind("isCopy", isCopy)
        .mapTo<MessageThreadId>()
        .one()
}

fun Database.Transaction.reAssociateMessageAttachments(attachmentIds: Set<AttachmentId>, messageContentId: MessageContentId): Int {
    return createUpdate(
        """
UPDATE attachment
SET
    message_content_id = :messageContentId,
    message_draft_id = NULL
WHERE
    id = ANY(:attachmentIds)
        """.trimIndent()
    )
        .bind("attachmentIds", attachmentIds)
        .bind("messageContentId", messageContentId)
        .execute()
}

data class ReceivedMessageResultItem(
    val count: Int,
    val id: MessageThreadId,
    val title: String,
    val type: MessageType,
    val urgent: Boolean,
    val messageId: MessageId,
    val sentAt: HelsinkiDateTime,
    val content: String,
    val senderId: MessageAccountId,
    val senderName: String,
    val senderAccountType: AccountType,
    val readAt: HelsinkiDateTime? = null,
    val recipientId: MessageAccountId,
    val recipientName: String,
    val recipientAccountType: AccountType,
    @Json
    val children: List<MessageChild>,
    @Json
    val attachments: List<MessageAttachment>
)

private data class ReceivedThread(
    val id: MessageThreadId,
    val title: String,
    val type: MessageType,
    val urgent: Boolean,
    @Json
    val children: List<MessageChild>,
)

private data class ThreadMessage(
    val threadId: MessageThreadId,
    @Nested
    val message: Message
)

/** Return all threads that are visible to the account through sent and received messages **/
fun Database.Read.getThreads(accountId: MessageAccountId, pageSize: Int, page: Int): Paged<MessageThread> {
    val threads = createQuery(
        """
SELECT
    COUNT(*) OVER () AS count,
    t.id,
    t.title,
    t.message_type AS type,
    t.urgent,
    coalesce((
        SELECT json_agg(json_build_object(
            'childId', mtc.child_id,
            'firstName', p.first_name,
            'lastName', p.last_name,
            'preferredName', p.preferred_name
        ))
        FROM message_thread_children mtc
        JOIN person p ON p.id = mtc.child_id
        WHERE mtc.thread_id = t.id
    ), '[]'::json) AS children
FROM message_thread_participant tp
JOIN message_thread t on t.id = tp.thread_id
WHERE tp.participant_id = :accountId
ORDER BY tp.last_message_timestamp DESC
LIMIT :pageSize OFFSET :offset
        """
    )
        .bind("accountId", accountId)
        .bind("pageSize", pageSize)
        .bind("offset", (page - 1) * pageSize)
        .mapToPaged<ReceivedThread>(pageSize)

    val messagesByThread = getThreadMessages(accountId, threads.data.map { it.id })
    return combineThreadsAndMessages(accountId, threads, messagesByThread)
}

/** Return all threads in which the account has received messages **/
fun Database.Read.getReceivedThreads(accountId: MessageAccountId, pageSize: Int, page: Int): Paged<MessageThread> {
    val threads = createQuery(
        """
SELECT
    COUNT(*) OVER () AS count,
    t.id,
    t.title,
    t.message_type AS type,
    t.urgent,
    coalesce((
        SELECT json_agg(json_build_object(
            'childId', mtc.child_id,
            'firstName', p.first_name,
            'lastName', p.last_name,
            'preferredName', p.preferred_name
        ))
        FROM message_thread_children mtc
        JOIN person p ON p.id = mtc.child_id
        WHERE mtc.thread_id = t.id
    ), '[]'::json) AS children
FROM message_thread_participant tp
JOIN message_thread t on t.id = tp.thread_id
WHERE
    tp.participant_id = :accountId AND
    tp.last_received_timestamp IS NOT NULL
ORDER BY tp.last_received_timestamp DESC
LIMIT :pageSize OFFSET :offset
        """
    )
        .bind("accountId", accountId)
        .bind("pageSize", pageSize)
        .bind("offset", (page - 1) * pageSize)
        .mapToPaged<ReceivedThread>(pageSize)

    val messagesByThread = getThreadMessages(accountId, threads.data.map { it.id })
    return combineThreadsAndMessages(accountId, threads, messagesByThread)
}

private fun Database.Read.getThreadMessages(accountId: MessageAccountId, threadIds: List<MessageThreadId>): Map<MessageThreadId, List<ThreadMessage>> {
    if (threadIds.isEmpty()) return mapOf()
    return createQuery(
        """
SELECT
    m.id,
    m.thread_id,
    m.sent_at,
    mc.content,
    mr_self.read_at,
    (
        SELECT json_build_object('id', mav.id, 'name', mav.name, 'type', mav.type)
        FROM message_account_view mav
        WHERE mav.id = m.sender_id
    ) AS sender,
    (
        SELECT json_agg(json_build_object('id', mav.id, 'name', mav.name, 'type', mav.type))
        FROM message_recipients mr
        JOIN message_account_view mav ON mav.id = mr.recipient_id
        WHERE mr.message_id = m.id
    ) AS recipients,
    COALESCE((
        SELECT json_agg(json_build_object('id', a.id, 'name', a.name, 'contentType', a.content_type))
        FROM attachment a
        WHERE a.message_content_id = mc.id
    ), '[]'::json) AS attachments
FROM message m
JOIN message_content mc ON mc.id = m.content_id
LEFT JOIN message_recipients mr_self ON mr_self.message_id = m.id AND mr_self.recipient_id = :accountId
WHERE
    m.thread_id = ANY(:threadIds) AND
    (m.sender_id = :accountId OR EXISTS (
        SELECT 1
        FROM message_recipients mr
        WHERE mr.message_id = m.id AND mr.recipient_id = :accountId
    ))
ORDER BY m.sent_at
            """
    )
        .bind("accountId", accountId)
        .bind("threadIds", threadIds)
        .mapTo<ThreadMessage>()
        .groupBy { it.threadId }
}

private fun combineThreadsAndMessages(accountId: MessageAccountId, threads: Paged<ReceivedThread>, messagesByThread: Map<MessageThreadId, List<ThreadMessage>>): Paged<MessageThread> {
    return threads.flatMap { thread ->
        val messages = messagesByThread[thread.id]?.map { it.message }
        if (messages == null) {
            logger.warn("Thread ${thread.id} has no messages for account $accountId")
            listOf()
        } else {
            listOf(
                MessageThread(
                    id = thread.id,
                    type = thread.type,
                    title = thread.title,
                    urgent = thread.urgent,
                    children = thread.children,
                    messages = messages,
                )
            )
        }
    }
}

data class MessageCopy(
    val threadId: MessageThreadId,
    val messageId: MessageId,
    val title: String,
    val type: MessageType,
    val urgent: Boolean,
    val sentAt: HelsinkiDateTime,
    val content: String,
    val senderId: MessageAccountId,
    val senderName: String,
    val senderAccountType: AccountType,
    val readAt: HelsinkiDateTime? = null,
    val recipientId: MessageAccountId,
    val recipientName: String,
    val recipientAccountType: AccountType,
    val recipientNames: List<String>,
    @Json
    val attachments: List<MessageAttachment>
)

fun Database.Read.getMessageCopiesByAccount(accountId: MessageAccountId, pageSize: Int, page: Int): Paged<MessageCopy> {
    // language=SQL
    val sql = """
SELECT
    COUNT(*) OVER () AS count,
    t.id AS thread_id,
    m.id AS message_id,
    t.title,
    t.message_type AS type,
    t.urgent,
    m.sent_at,
    m.sender_name,
    m.sender_id,
    sender_acc.type AS sender_account_type,
    m.content_id,
    c.content,
    rec.read_at,
    rec.recipient_id,
    acc.account_name recipient_name,
    recipient_acc.type AS recipient_account_type,
    m.recipient_names,
    (
        SELECT coalesce(jsonb_agg(json_build_object(
           'id', att.id,
           'name', att.name,
           'contentType', att.content_type
        )), '[]'::jsonb)
        FROM attachment att WHERE att.message_content_id = m.content_id
    ) AS attachments
FROM message_recipients rec
JOIN message m ON rec.message_id = m.id
JOIN message_content c ON m.content_id = c.id
JOIN message_account_name_view acc ON rec.recipient_id = acc.id
JOIN message_account sender_acc ON sender_acc.id = m.sender_id
JOIN message_account recipient_acc ON recipient_acc.id = rec.recipient_id
JOIN message_thread t ON m.thread_id = t.id
WHERE rec.recipient_id = :accountId AND t.is_copy
ORDER BY m.sent_at DESC
LIMIT :pageSize OFFSET :offset
"""

    return createQuery(sql)
        .bind("accountId", accountId)
        .bind("offset", (page - 1) * pageSize)
        .bind("pageSize", pageSize)
        .mapToPaged(pageSize)
}

data class MessageResultItem(
    val id: MessageId,
    val senderId: MessageAccountId,
    val senderName: String,
    val senderAccountType: AccountType,
    val recipientId: MessageAccountId,
    val recipientName: String,
    val recipientAccountType: AccountType,
    val sentAt: HelsinkiDateTime,
    val content: String,
    @Json
    val attachments: List<MessageAttachment>
)

fun Database.Read.getMessage(id: MessageId): Message {
    val sql = """
        SELECT
            m.id,
            m.sender_id,
            m.sender_name,
            sender_acc.type as sender_account_type,
            m.sent_at,
            c.content,
            rec.recipient_id,
            recipient_acc_name.account_name recipient_name,
            recipient_acc.type AS recipient_account_type,
            (
                SELECT coalesce(jsonb_agg(json_build_object(
                   'id', att.id,
                   'name', att.name,
                   'contentType', att.content_type
                )), '[]'::jsonb)
                FROM attachment att WHERE att.message_content_id = m.content_id
            ) AS attachments
        FROM message m
        JOIN message_content c ON m.content_id = c.id
        JOIN message_recipients rec ON m.id = rec.message_id
        JOIN message_account recipient_acc ON recipient_acc.id = rec.recipient_id
        JOIN message_account sender_acc ON m.sender_id = sender_acc.id
        JOIN message_account_name_view recipient_acc_name ON rec.recipient_id = recipient_acc_name.id
        WHERE m.id = :id
    """.trimIndent()

    return this.createQuery(sql)
        .bind("id", id)
        .mapTo<MessageResultItem>()
        .groupBy { it.id }
        .map { (id, messages) ->
            Message(
                id = id,
                content = messages[0].content,
                sentAt = messages[0].sentAt,
                sender = MessageAccount(
                    id = messages[0].senderId,
                    name = messages[0].senderName,
                    type = messages[0].senderAccountType
                ),
                recipients = messages.map { MessageAccount(it.recipientId, it.recipientName, it.recipientAccountType) }.toSet(),
                attachments = messages[0].attachments
            )
        }
        .single()
}

fun Database.Read.getCitizenReceivers(today: LocalDate, accountId: MessageAccountId): Map<MessageAccount, List<ChildId>> {
    data class MessageAccountWithChildId(
        val id: MessageAccountId,
        val name: String,
        val type: AccountType,
        val childId: ChildId
    )
    // language=SQL
    val sql = """
WITH backup_care_placements AS (
    SELECT p.id, p.unit_id, p.child_id, p.group_id
    FROM guardian g
    JOIN backup_care p ON p.child_id = g.child_id AND daterange(p.start_date, p.end_date, '[]') @> :today
    WHERE guardian_id = (SELECT person_id AS id FROM message_account WHERE id = :accountId)
    AND NOT EXISTS (
        SELECT 1 FROM messaging_blocklist b
        WHERE b.child_id = p.child_id
        AND b.blocked_recipient = g.guardian_id
    )
    AND EXISTS (
        SELECT 1 FROM daycare u
        WHERE p.unit_id = u.id AND 'MESSAGING' = ANY(u.enabled_pilot_features)
    )

    UNION

    SELECT p.id, p.unit_id, p.child_id, p.group_id
    FROM fridge_child fg
    JOIN backup_care p ON fg.child_id = p.child_id AND daterange(p.start_date, p.end_date, '[]') @> :today
    WHERE daterange(fg.start_date, fg.end_date, '[]') @> :today
    AND fg.head_of_child = (SELECT person_id AS id FROM message_account WHERE id = :accountId)
    AND fg.conflict = false
    AND NOT EXISTS (
        SELECT 1 FROM messaging_blocklist b
        WHERE b.child_id = p.child_id
        AND b.blocked_recipient = fg.head_of_child
    )
    AND EXISTS (
        SELECT 1 FROM daycare u
        WHERE p.unit_id = u.id AND 'MESSAGING' = ANY(u.enabled_pilot_features)
    )
), placements AS (
    SELECT p.id, p.unit_id, p.child_id
    FROM guardian g
    JOIN placement p ON p.child_id = g.child_id AND daterange(p.start_date, p.end_date, '[]') @> :today
    WHERE guardian_id = (SELECT person_id AS id FROM message_account WHERE id = :accountId)
    AND NOT EXISTS (
        SELECT 1 FROM messaging_blocklist b
        WHERE b.child_id = p.child_id
        AND b.blocked_recipient = g.guardian_id
    )
    AND NOT EXISTS (
        SELECT 1 FROM backup_care_placements bc
        WHERE bc.child_id = p.child_id
    )
    AND EXISTS (
        SELECT 1 FROM daycare u
        WHERE p.unit_id = u.id AND 'MESSAGING' = ANY(u.enabled_pilot_features)
    )

    UNION

    SELECT p.id, p.unit_id, p.child_id
    FROM fridge_child fg
    JOIN placement p ON fg.child_id = p.child_id AND daterange(p.start_date, p.end_date, '[]') @> :today
    WHERE daterange(fg.start_date, fg.end_date, '[]') @> :today
    AND fg.head_of_child = (SELECT person_id AS id FROM message_account WHERE id = :accountId)
    AND fg.conflict = false
    AND NOT EXISTS (
        SELECT 1 FROM messaging_blocklist b
        WHERE b.child_id = p.child_id
        AND b.blocked_recipient = fg.head_of_child
    )
    AND NOT EXISTS (
        SELECT 1 FROM backup_care_placements bc
        WHERE bc.child_id = p.child_id
    )
    AND EXISTS (
        SELECT 1 FROM daycare u
        WHERE p.unit_id = u.id AND 'MESSAGING' = ANY(u.enabled_pilot_features)
    )
),
relevant_placements AS (
    SELECT p.id, p.unit_id, p.child_id
    FROM placements p

    UNION

    SELECT bc.id, bc.unit_id, bc.child_id
    FROM backup_care_placements bc
),
personal_accounts AS (
    SELECT acc.id, acc_name.account_name AS name, 'PERSONAL' AS type, p.child_id
    FROM (SELECT DISTINCT unit_id, child_id FROM relevant_placements) p
    JOIN daycare_acl acl ON acl.daycare_id = p.unit_id
    JOIN message_account acc ON acc.employee_id = acl.employee_id
    JOIN message_account_name_view acc_name ON acc_name.id = acc.id
    WHERE active IS TRUE
),
group_accounts AS (
    SELECT acc.id, g.name, 'GROUP' AS type, p.child_id
    FROM placements p
    JOIN daycare_group_placement dgp ON dgp.daycare_placement_id = p.id AND :today BETWEEN dgp.start_date AND dgp.end_date
    JOIN daycare_group g ON g.id = dgp.daycare_group_id
    JOIN message_account acc on g.id = acc.daycare_group_id

    UNION ALL

    SELECT acc.id, g.name, 'GROUP' AS type, p.child_id
    FROM backup_care_placements p
    JOIN daycare_group g ON g.id = p.group_id
    JOIN message_account acc on g.id = acc.daycare_group_id
),
mixed_accounts AS (
    SELECT id, name, type, child_id FROM personal_accounts
    UNION ALL
    SELECT id, name, type, child_id FROM group_accounts
)
SELECT id, name, type, child_id FROM mixed_accounts
ORDER BY type, name  -- groups first
    """.trimIndent()

    return this.createQuery(sql)
        .bind("accountId", accountId)
        .bind("today", today)
        .mapTo<MessageAccountWithChildId>()
        .groupBy({ MessageAccount(it.id, it.name, it.type) }, { it.childId })
}

fun Database.Read.getMessagesSentByAccount(accountId: MessageAccountId, pageSize: Int, page: Int): Paged<SentMessage> {
    // language=SQL
    val sql = """
WITH pageable_messages AS (
    SELECT
        m.content_id,
        m.sent_at,
        m.recipient_names,
        t.title,
        t.message_type,
        t.urgent,
        COUNT(*) OVER () AS count
    FROM message m
    JOIN message_thread t ON m.thread_id = t.id
    WHERE sender_id = :accountId
    GROUP BY content_id, sent_at, recipient_names, title, message_type, urgent
    ORDER BY sent_at DESC
    LIMIT :pageSize OFFSET :offset
),
recipients AS (
    SELECT
        m.content_id,
        rec.recipient_id,
        name_view.account_name,
        acc.type AS account_type
    FROM message_recipients rec
    JOIN message m ON rec.message_id = m.id
    JOIN message_account_name_view name_view ON rec.recipient_id = name_view.id
    JOIN message_account acc ON acc.id = rec.recipient_id
)

SELECT
    msg.count,
    msg.content_id,
    msg.sent_at,
    msg.recipient_names,
    msg.title AS thread_title,
    msg.message_type AS type,
    msg.urgent,
    mc.content,
    (SELECT jsonb_agg(json_build_object(
           'id', rec.recipient_id,
           'name', rec.account_name,
           'type', rec.account_type
       ))) AS recipients,
    (SELECT coalesce(jsonb_agg(json_build_object(
           'id', att.id,
           'name', att.name,
           'contentType', att.content_type
        )), '[]'::jsonb)
        FROM attachment att WHERE att.message_content_id = msg.content_id
        ) AS attachments
FROM pageable_messages msg
JOIN recipients rec ON msg.content_id = rec.content_id
JOIN message_content mc ON msg.content_id = mc.id
GROUP BY msg.count, msg.content_id, msg.sent_at, msg.recipient_names, mc.content, msg.message_type, msg.urgent, msg.title
ORDER BY msg.sent_at DESC
    """.trimIndent()

    return this.createQuery(sql)
        .bind("accountId", accountId)
        .bind("offset", (page - 1) * pageSize)
        .bind("pageSize", pageSize)
        .mapToPaged(pageSize)
}

data class ThreadWithParticipants(
    val threadId: MessageThreadId,
    val type: MessageType,
    val isCopy: Boolean,
    val senders: Set<MessageAccountId>,
    val recipients: Set<MessageAccountId>
)

fun Database.Read.getThreadByMessageId(messageId: MessageId): ThreadWithParticipants? {
    val sql = """
        SELECT
            t.id AS threadId,
            t.message_type AS type,
            t.is_copy,
            (SELECT array_agg(m2.sender_id)) as senders,
            (SELECT array_agg(rec.recipient_id)) as recipients
            FROM message m
            JOIN message_thread t ON m.thread_id = t.id
            JOIN message m2 ON m2.thread_id = t.id
            JOIN message_recipients rec ON rec.message_id = m2.id
            WHERE m.id = :messageId
            GROUP BY t.id, t.message_type
    """.trimIndent()
    return this.createQuery(sql)
        .bind("messageId", messageId)
        .mapTo<ThreadWithParticipants>()
        .firstOrNull()
}

data class MessageReceiversResult(
    val childId: ChildId,
    val groupId: GroupId,
    val groupName: String,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: LocalDate
)

fun Database.Read.getReceiversForNewMessage(
    employeeOrMobileId: Id<*>,
    unitId: DaycareId
): List<MessageReceiversResponse> {
    // language=sql
    val sql = """
        WITH children AS (
            SELECT pl.child_id, dg.id group_id, dg.name group_name
            FROM daycare_group dg
            JOIN daycare_group_placement gpl ON dg.id = gpl.daycare_group_id AND daterange(gpl.start_date, gpl.end_date, '[]') @> :date
            JOIN placement pl ON gpl.daycare_placement_id = pl.id
            JOIN daycare d ON pl.unit_id = d.id
            WHERE pl.unit_id = :unitId AND EXISTS (
                SELECT 1
                FROM child_daycare_acl(:date)
                JOIN mobile_device_daycare_acl_view USING (daycare_id)
                WHERE mobile_device_id = :employeeOrMobileId
                AND child_id = pl.child_id

                UNION ALL

                SELECT 1
                FROM employee_child_daycare_acl(:date)
                WHERE employee_id = :employeeOrMobileId
                AND child_id = pl.child_id
            )
            AND 'MESSAGING' = ANY(d.enabled_pilot_features)

            UNION ALL

            SELECT bc.child_id, dg.id group_id, dg.name group_name
            FROM daycare_group dg
            JOIN backup_care bc ON dg.id = bc.group_id AND daterange(bc.start_date, bc.end_date, '[]') @> :date
            JOIN daycare d ON bc.unit_id = d.id
            WHERE d.id = :unitId AND EXISTS (
                SELECT 1
                FROM child_daycare_acl(:date)
                JOIN mobile_device_daycare_acl_view USING (daycare_id)
                WHERE mobile_device_id = :employeeOrMobileId
                AND child_id = bc.child_id

                UNION ALL

                SELECT 1
                FROM employee_child_daycare_acl(:date)
                WHERE employee_id = :employeeOrMobileId
                AND child_id = bc.child_id
            )
            AND 'MESSAGING' = ANY(d.enabled_pilot_features)
        )
        SELECT DISTINCT
            c.child_id,
            c.group_id,
            c.group_name,
            p.first_name,
            p.last_name,
            p.date_of_birth
        FROM children c
        JOIN person p ON c.child_id = p.id
        WHERE EXISTS (
            SELECT 1
            FROM guardian g
            LEFT JOIN messaging_blocklist bl ON g.guardian_id = bl.blocked_recipient AND c.child_id = bl.child_id
            WHERE g.child_id = c.child_id AND bl.id IS NULL
        )
    """.trimIndent()

    return this.createQuery(sql)
        .bind("employeeOrMobileId", employeeOrMobileId)
        .bind("date", HelsinkiDateTime.now().toLocalDate())
        .bind("unitId", unitId)
        .mapTo<MessageReceiversResult>()
        .toList()
        .groupBy { it.groupId }
        .map { (groupId, receiverChildren) ->
            MessageReceiversResponse(
                groupId = groupId,
                groupName = receiverChildren.first().groupName,
                receivers = receiverChildren
                    .map { child ->
                        MessageReceiver(
                            childId = child.childId,
                            childFirstName = child.firstName,
                            childLastName = child.lastName,
                            childDateOfBirth = child.dateOfBirth
                        )
                    }
            )
        }
}

fun Database.Read.getMessageAccountsForRecipients(
    accountId: MessageAccountId,
    recipients: Set<MessageRecipient>,
    date: LocalDate
): Map<MessageAccountId, ChildId> {
    data class MessageAccountIdToChildId(
        val accountId: MessageAccountId,
        val childId: ChildId
    )
    val groupedRecipients = recipients.groupBy { it.type }
    return this.createQuery(
        """
WITH sender AS (
    SELECT daycare_group_id, employee_id FROM message_account WHERE id = :senderId
), children AS (
    SELECT pl.child_id
    FROM realized_placement_all(:date) pl
    JOIN daycare d ON pl.unit_id = d.id
    WHERE (pl.unit_id = ANY(:unitRecipients) OR pl.group_id = ANY(:groupRecipients) OR pl.child_id = ANY(:childRecipients))
    AND EXISTS (
        SELECT 1
        FROM child_daycare_acl(:date)
        JOIN mobile_device_daycare_acl_view USING (daycare_id)
        WHERE mobile_device_id = (SELECT sender.employee_id FROM sender)
        AND child_id = pl.child_id

        UNION ALL

        SELECT 1
        FROM employee_child_daycare_acl(:date)
        WHERE employee_id = (SELECT sender.employee_id FROM sender)
        AND child_id = pl.child_id

        UNION ALL

        SELECT 1
        FROM sender
        WHERE pl.group_id = sender.daycare_group_id
    )
    AND 'MESSAGING' = ANY(d.enabled_pilot_features)
)
SELECT DISTINCT acc.id as account_id, c.child_id
FROM children c
JOIN guardian g ON g.child_id = c.child_id
JOIN message_account acc ON g.guardian_id = acc.person_id
WHERE NOT EXISTS (
    SELECT 1 FROM messaging_blocklist bl
    WHERE bl.child_id = c.child_id
    AND bl.blocked_recipient = g.guardian_id
)
"""
    )
        .bind("senderId", accountId)
        .bind("date", date)
        .bind("unitRecipients", groupedRecipients[MessageRecipientType.UNIT]?.map { it.id } ?: listOf())
        .bind("groupRecipients", groupedRecipients[MessageRecipientType.GROUP]?.map { it.id } ?: listOf())
        .bind("childRecipients", groupedRecipients[MessageRecipientType.CHILD]?.map { it.id } ?: listOf())
        .mapTo<MessageAccountIdToChildId>()
        .associate { it.accountId to it.childId }
}

fun Database.Transaction.markNotificationAsSent(id: MessageRecipientId, timestamp: HelsinkiDateTime) {
    val sql = """
        UPDATE message_recipients
        SET notification_sent_at = :timestamp
        WHERE id = :id
    """.trimIndent()
    this.createUpdate(sql)
        .bind("id", id)
        .bind("timestamp", timestamp)
        .execute()
}

fun Database.Read.getStaffCopyRecipients(
    senderId: MessageAccountId,
    unitIds: List<DaycareId>,
    groupIds: List<GroupId>,
    date: LocalDate
): Set<MessageAccountId> {
    return this.createQuery(
        """
SELECT receiver_acc.id
FROM message_account sender_acc
JOIN daycare_acl_view acl ON sender_acc.employee_id = acl.employee_id
JOIN daycare u ON u.id = acl.daycare_id
JOIN daycare_group g ON u.id = g.daycare_id
JOIN message_account receiver_acc ON g.id = receiver_acc.daycare_group_id
WHERE sender_acc.id = :senderId AND (u.id = ANY(:unitIds) OR g.id = ANY(:groupIds))
"""
    )
        .bind("senderId", senderId)
        .bind("unitIds", unitIds)
        .bind("groupIds", groupIds)
        .bind("date", date)
        .mapTo<MessageAccountId>()
        .toSet()
}
