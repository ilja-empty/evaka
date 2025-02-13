// SPDX-FileCopyrightText: 2017-2023 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { faReply } from '@fortawesome/free-solid-svg-icons'
import React, {
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState
} from 'react'
import styled, { css } from 'styled-components'

import {
  Message,
  MessageChild,
  MessageThread
} from 'lib-common/generated/api-types/messaging'
import { formatFirstName } from 'lib-common/names'
import { UUID } from 'lib-common/types'
import { scrollRefIntoView } from 'lib-common/utils/scrolling'
import HorizontalLine from 'lib-components/atoms/HorizontalLine'
import Linkify from 'lib-components/atoms/Linkify'
import InlineButton from 'lib-components/atoms/buttons/InlineButton'
import {
  FixedSpaceColumn,
  FixedSpaceRow
} from 'lib-components/layout/flex-helpers'
import { MessageReplyEditor } from 'lib-components/messages/MessageReplyEditor'
import { ThreadContainer } from 'lib-components/messages/ThreadListItem'
import FileDownloadButton from 'lib-components/molecules/FileDownloadButton'
import { fontWeights, InformationText } from 'lib-components/typography'
import { useRecipients } from 'lib-components/utils/useReplyRecipients'
import { defaultMargins, Gap } from 'lib-components/white-space'
import colors from 'lib-customizations/common'

import TopBar from '../common/TopBar'
import { useTranslation } from '../common/i18n'

import { getAttachmentUrl } from './api'
import { MessageContext } from './state'

interface Props {
  accountId: UUID
  thread: MessageThread
  onBack: () => void
}

export default React.memo(function ThreadView({
  accountId,
  thread: { id: threadId, messages, title, type, children },
  onBack
}: Props) {
  const { i18n } = useTranslation()
  const { sendReply, setReplyContent, getReplyContent } =
    useContext(MessageContext)

  const { onToggleRecipient, recipients } = useRecipients(messages, accountId)
  const [replyEditorVisible, setReplyEditorVisible] = useState(false)

  useEffect(() => setReplyEditorVisible(false), [threadId])

  const autoScrollRef = useRef<HTMLSpanElement>(null)
  useEffect(() => {
    scrollRefIntoView(autoScrollRef)
  }, [messages, replyEditorVisible])

  const titleRowRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    titleRowRef.current?.focus()
  }, [threadId])

  const lastMessageRef = useRef<HTMLLIElement>(null)

  const onUpdateContent = useCallback(
    (content: string) => setReplyContent(threadId, content),
    [setReplyContent, threadId]
  )

  const onDiscard = useCallback(() => {
    setReplyContent(threadId, '')
    setReplyEditorVisible(false)
  }, [setReplyContent, setReplyEditorVisible, threadId])

  const replyContent = getReplyContent(threadId)
  const onSubmit = useCallback(async () => {
    const result = await sendReply({
      accountId,
      content: replyContent,
      messageId: messages.slice(-1)[0].id,
      recipientAccountIds: recipients.filter((r) => r.selected).map((r) => r.id)
    })
    if (result.isSuccess) {
      setReplyEditorVisible(false)
    }
    return result
  }, [accountId, messages, recipients, replyContent, sendReply])

  const sendEnabled = !!replyContent && recipients.some((r) => r.selected)

  const endOfMessagesRef = useRef<HTMLDivElement | null>(null)
  useEffect(() => {
    scrollRefIntoView(lastMessageRef)
  }, [])

  return (
    <MobileThreadContainer data-qa="thread-reader">
      <TopBar title={title} onBack={onBack} invertedColors />
      <Gap size="s" />
      <MessageList>
        {messages.map((message, i) => (
          <React.Fragment key={message.id}>
            <SingleMessage
              message={message}
              relatedChildren={children}
              ref={i === messages.length - 1 ? lastMessageRef : undefined}
            />
            <Gap size="xs" />
          </React.Fragment>
        ))}
      </MessageList>
      <div ref={endOfMessagesRef} />
      {replyEditorVisible ? (
        <ReplyEditorContainer>
          <MessageReplyEditor
            onSubmit={onSubmit}
            onUpdateContent={onUpdateContent}
            onDiscard={onDiscard}
            recipients={recipients}
            onToggleRecipient={onToggleRecipient}
            replyContent={replyContent}
            sendEnabled={sendEnabled}
          />
        </ReplyEditorContainer>
      ) : (
        messages.length > 0 && (
          <>
            <Gap size="s" />
            <ActionRow justifyContent="space-between">
              {type === 'MESSAGE' ? (
                <ReplyToThreadButton
                  icon={faReply}
                  onClick={() => setReplyEditorVisible(true)}
                  data-qa="message-reply-editor-btn"
                  text={i18n.messages.thread.reply}
                />
              ) : (
                <div />
              )}
            </ActionRow>
            <Gap size="m" />
          </>
        )
      )}
      {replyEditorVisible && <span ref={autoScrollRef} />}
    </MobileThreadContainer>
  )
})

const MobileThreadContainer = styled(ThreadContainer)`
  height: 100vh;
  overflow-y: auto;
`

const TitleRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;

  & + & {
    margin-top: ${defaultMargins.L};
  }
`

const MessageList = styled.ul`
  margin: 0;
  padding: 0;
  list-style: none;
`

// eslint-disable-next-line react/display-name
const SingleMessage = React.memo(
  React.forwardRef(function SingleMessage(
    {
      message,
      relatedChildren
    }: {
      message: Message
      relatedChildren: MessageChild[]
    },
    ref: React.ForwardedRef<HTMLLIElement>
  ) {
    const childNames =
      relatedChildren.length > 0
        ? relatedChildren.map((child) => formatFirstName(child)).join(', ')
        : null
    const sender =
      message.sender.name +
      (message.sender.type === 'CITIZEN' ? ` (${childNames})` : '')
    const recipients = message.recipients
      .map((r) => r.name + (r.type === 'CITIZEN' ? ` (${childNames})` : ''))
      .join(', ')
    return (
      <MessageContainer tabIndex={-1} ref={ref}>
        <TitleRow>
          <SenderName data-qa="single-message-sender-name">{sender}</SenderName>
          <InformationText>
            {message.sentAt.toLocalDate().format()}
          </InformationText>
        </TitleRow>
        <InformationText>{recipients}</InformationText>
        <MessageContent data-qa="single-message-content">
          <Linkify text={message.content} />
        </MessageContent>
        {message.attachments.length > 0 && (
          <>
            <HorizontalLine slim />
            <FixedSpaceColumn spacing="xs">
              {message.attachments.map((attachment) => (
                <FileDownloadButton
                  key={attachment.id}
                  file={attachment}
                  getFileUrl={getAttachmentUrl}
                  icon
                  data-qa="attachment"
                />
              ))}
            </FixedSpaceColumn>
          </>
        )}
      </MessageContainer>
    )
  })
)

const SenderName = styled.div`
  font-weight: ${fontWeights.semibold};
`

const MessageContent = styled.div`
  padding-top: ${defaultMargins.s};
  white-space: pre-line;
`

const ActionRow = styled(FixedSpaceRow)`
  padding: 0 ${defaultMargins.xs} ${defaultMargins.xs} ${defaultMargins.xs};
`

const ReplyToThreadButton = styled(InlineButton)`
  align-self: flex-start;
`

const messageContainerStyles = css`
  background-color: ${colors.grayscale.g0};
  padding: ${defaultMargins.s};
`

const MessageContainer = styled.li`
  ${messageContainerStyles}
  h2 {
    margin: 0;
  }
`

const ReplyEditorContainer = styled.div`
  ${messageContainerStyles}
`
