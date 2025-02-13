// SPDX-FileCopyrightText: 2017-2022 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import sortBy from 'lodash/sortBy'
import uniqBy from 'lodash/uniqBy'
import React, {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState
} from 'react'
import { useSearchParams } from 'react-router-dom'
import { useTheme } from 'styled-components'

import { Failure, Loading, Paged, Result } from 'lib-common/api'
import {
  DraftContent,
  Message,
  MessageThread,
  AuthorizedMessageAccount,
  SentMessage,
  ThreadReply,
  UnreadCountByAccount,
  MessageCopy
} from 'lib-common/generated/api-types/messaging'
import HelsinkiDateTime from 'lib-common/helsinki-date-time'
import { UUID } from 'lib-common/types'
import { usePeriodicRefresh } from 'lib-common/utils/usePeriodicRefresh'
import { useApiState, useRestApi } from 'lib-common/utils/useRestApi'
import { NotificationsContext } from 'lib-components/Notifications'
import {
  GroupMessageAccount,
  isGroupMessageAccount,
  isMunicipalMessageAccount,
  isPersonalMessageAccount,
  isServiceWorkerMessageAccount
} from 'lib-components/messages/types'
import { SelectOption } from 'lib-components/molecules/Select'
import { faCheck } from 'lib-icons'

import { client } from '../../api/client'
import { UserContext } from '../../state/user'

import { UndoMessage } from './UndoMessageNotification'
import {
  getMessageCopies,
  getMessageDrafts,
  getArchivedMessages,
  getMessagingAccounts,
  getReceivedMessages,
  getSentMessages,
  getUnreadCounts,
  markThreadRead,
  replyToThread,
  ReplyToThreadParams,
  getThread
} from './api'
import {
  AccountView,
  groupMessageBoxes,
  isValidView,
  municipalMessageBoxes,
  personalMessageBoxes,
  serviceWorkerMessageBoxes
} from './types-view'

const PAGE_SIZE = 20
type RepliesByThread = Record<UUID, string>
export type CancelableMessage = {
  accountId: UUID
  sentAt: HelsinkiDateTime
} & ({ messageId: UUID } | { contentId: UUID })

export interface MessagesState {
  accounts: Result<AuthorizedMessageAccount[]>
  municipalAccount: AuthorizedMessageAccount | undefined
  serviceWorkerAccount: AuthorizedMessageAccount | undefined
  personalAccount: AuthorizedMessageAccount | undefined
  groupAccounts: GroupMessageAccount[]
  unitOptions: SelectOption[]
  selectedDraft: DraftContent | undefined
  setSelectedDraft: (draft: DraftContent | undefined) => void
  selectedAccount: AccountView | undefined
  selectAccount: (v: AccountView) => void
  selectDefaultAccount: () => void
  selectUnit: (v: string) => void
  page: number
  setPage: (page: number) => void
  pages: number | undefined
  setPages: (pages: number) => void
  receivedMessages: Result<MessageThread[]>
  sentMessages: Result<SentMessage[]>
  messageDrafts: Result<DraftContent[]>
  messageCopies: Result<MessageCopy[]>
  archivedMessages: Result<MessageThread[]>
  setSelectedThread: (threadId: UUID) => void
  selectedThread: MessageThread | undefined
  selectThread: (thread: MessageThread | undefined) => void
  sendReply: (params: ReplyToThreadParams) => Promise<Result<unknown>>
  setReplyContent: (threadId: UUID, content: string) => void
  getReplyContent: (threadId: UUID) => string
  refreshMessages: (account?: UUID) => void
  unreadCountsByAccount: Result<UnreadCountByAccount[]>
  sentMessagesAsThreads: Result<MessageThread[]>
  messageCopiesAsThreads: Result<MessageThread[]>
  openMessageUndo: (m: CancelableMessage) => void
  prefilledRecipient: string | null
  prefilledTitle: string | null
  relatedApplicationId: UUID | null
  accountAllowsNewMessage: () => boolean
}

const defaultState: MessagesState = {
  accounts: Loading.of(),
  municipalAccount: undefined,
  serviceWorkerAccount: undefined,
  personalAccount: undefined,
  groupAccounts: [],
  unitOptions: [],
  selectedDraft: undefined,
  setSelectedDraft: () => undefined,
  selectedAccount: undefined,
  selectAccount: () => undefined,
  selectDefaultAccount: () => undefined,
  selectUnit: () => undefined,
  page: 1,
  setPage: () => undefined,
  pages: undefined,
  setPages: () => undefined,
  receivedMessages: Loading.of(),
  sentMessages: Loading.of(),
  messageDrafts: Loading.of(),
  messageCopies: Loading.of(),
  archivedMessages: Loading.of(),
  setSelectedThread: () => undefined,
  selectedThread: undefined,
  selectThread: () => undefined,
  sendReply: () => Promise.resolve(Failure.of({ message: 'Not initialized' })),
  getReplyContent: () => '',
  setReplyContent: () => undefined,
  refreshMessages: () => undefined,
  unreadCountsByAccount: Loading.of(),
  sentMessagesAsThreads: Loading.of(),
  messageCopiesAsThreads: Loading.of(),
  openMessageUndo: () => undefined,
  prefilledRecipient: null,
  prefilledTitle: null,
  relatedApplicationId: null,
  accountAllowsNewMessage: () => false
}

export const MessageContext = createContext<MessagesState>(defaultState)

const appendMessageAndMoveThreadToTopOfList =
  (threadId: UUID, message: Message) => (state: Result<MessageThread[]>) =>
    state.map((threads) => {
      const thread = threads.find((t) => t.id === threadId)
      if (!thread) return threads
      const otherThreads = threads.filter((t) => t.id !== threadId)
      return [
        {
          ...thread,
          messages: [...thread.messages, message]
        },
        ...otherThreads
      ]
    })

export const MessageContextProvider = React.memo(
  function MessageContextProvider({ children }: { children: JSX.Element }) {
    const theme = useTheme()
    const { user } = useContext(UserContext)
    const { addNotification, removeNotification } =
      useContext(NotificationsContext)
    const [searchParams, setSearchParams] = useSearchParams()
    const accountId = searchParams.get('accountId')
    const messageBox = searchParams.get('messageBox')
    const unitId = searchParams.get('unitId')
    const threadId = searchParams.get('threadId')
    const prefilledTitle = searchParams.get('title')
    const prefilledRecipient = searchParams.get('recipient')
    const relatedApplicationId = searchParams.get('applicationId')
    const replyBoxActive = searchParams.get('reply')

    const setParams = useCallback(
      (params: {
        accountId?: string | null
        messageBox?: string | null
        unitId?: string | null
        threadId?: string | null
      }) => {
        setSearchParams(
          {
            ...(params.accountId ? { accountId: params.accountId } : undefined),
            ...(params.messageBox
              ? { messageBox: params.messageBox }
              : undefined),
            ...(params.unitId ? { unitId: params.unitId } : undefined),
            ...(params.threadId ? { threadId: params.threadId } : undefined),
            ...(prefilledTitle ? { title: prefilledTitle } : undefined),
            ...(prefilledRecipient
              ? { recipient: prefilledRecipient }
              : undefined),
            ...(relatedApplicationId
              ? { applicationId: relatedApplicationId }
              : undefined),
            ...(replyBoxActive ? { reply: replyBoxActive } : undefined)
          },
          { replace: true }
        )
      },
      [
        setSearchParams,
        prefilledTitle,
        prefilledRecipient,
        relatedApplicationId,
        replyBoxActive
      ]
    )

    const [accounts] = useApiState(
      () =>
        user?.accessibleFeatures.messages
          ? getMessagingAccounts()
          : Promise.resolve(Loading.of<AuthorizedMessageAccount[]>()),
      [user]
    )

    const municipalAccount = useMemo(
      () =>
        accounts
          .map((accounts) => accounts.find(isMunicipalMessageAccount))
          .getOrElse(undefined),
      [accounts]
    )
    const serviceWorkerAccount = useMemo(
      () =>
        accounts
          .map((accounts) => accounts.find(isServiceWorkerMessageAccount))
          .getOrElse(undefined),
      [accounts]
    )
    const personalAccount = useMemo(
      () =>
        accounts
          .map((accounts) => accounts.find(isPersonalMessageAccount))
          .getOrElse(undefined),
      [accounts]
    )
    const groupAccounts = useMemo(
      () =>
        accounts
          .map((accounts) =>
            accounts
              .filter(isGroupMessageAccount)
              .sort((a, b) =>
                a.daycareGroup.name
                  .toLocaleLowerCase()
                  .localeCompare(b.daycareGroup.name.toLocaleLowerCase())
              )
              .sort((a, b) =>
                a.daycareGroup.unitName
                  .toLocaleLowerCase()
                  .localeCompare(b.daycareGroup.unitName.toLocaleLowerCase())
              )
          )
          .getOrElse([]),
      [accounts]
    )
    const unitOptions = useMemo(
      () =>
        sortBy(
          uniqBy(
            groupAccounts.map(({ daycareGroup }) => ({
              value: daycareGroup.unitId,
              label: daycareGroup.unitName
            })),
            (val) => val.value
          ),
          (u) => u.label
        ),
      [groupAccounts]
    )

    const [unreadCountsByAccount, refreshUnreadCounts] = useApiState(
      () =>
        user?.accessibleFeatures.messages
          ? getUnreadCounts()
          : Promise.resolve(Loading.of<UnreadCountByAccount[]>()),
      [user]
    )

    usePeriodicRefresh(client, refreshUnreadCounts, { thresholdInMinutes: 1 })

    const selectedAccount: AccountView | undefined = useMemo(() => {
      const account = accounts
        .map(
          (accounts) =>
            accounts.find((a) => a.account.id === accountId)?.account
        )
        .getOrElse(undefined)
      if (messageBox && isValidView(messageBox) && account) {
        return {
          account,
          view: messageBox,
          unitId
        }
      }
      return undefined
    }, [accountId, accounts, messageBox, unitId])

    const accountAllowsNewMessage = useCallback(
      () => selectedAccount?.account.type !== 'SERVICE_WORKER',
      [selectedAccount]
    )

    const [selectedDraft, setSelectedDraft] = useState(
      defaultState.selectedDraft
    )

    const [page, setPage] = useState<number>(1)
    const [pages, setPages] = useState<number>()
    const [receivedMessages, setReceivedMessages] = useState<
      Result<MessageThread[]>
    >(Loading.of())
    const [messageDrafts, setMessageDrafts] = useState<Result<DraftContent[]>>(
      Loading.of()
    )
    const [sentMessages, setSentMessages] = useState<Result<SentMessage[]>>(
      Loading.of()
    )
    const [messageCopies, setMessageCopies] = useState<Result<MessageCopy[]>>(
      Loading.of()
    )
    const [archivedMessages, setArchivedMessages] = useState<
      Result<MessageThread[]>
    >(Loading.of())

    const setReceivedMessagesResult = useCallback(
      (result: Result<Paged<MessageThread>>) => {
        setReceivedMessages(result.map((r) => r.data))
        if (result.isSuccess) {
          setPages(result.value.pages)
        }
      },
      []
    )
    const loadReceivedMessages = useRestApi(
      (accountId: UUID, page: number) =>
        getReceivedMessages(accountId, page, PAGE_SIZE),
      setReceivedMessagesResult
    )

    const loadMessageDrafts = useRestApi(getMessageDrafts, setMessageDrafts)

    const setSentMessagesResult = useCallback(
      (result: Result<Paged<SentMessage>>) => {
        setSentMessages(result.map((r) => r.data))
        if (result.isSuccess) {
          setPages(result.value.pages)
        }
      },
      []
    )
    const loadSentMessages = useRestApi(
      (accountId: UUID, page: number) =>
        getSentMessages(accountId, page, PAGE_SIZE),
      setSentMessagesResult
    )

    const setMessageCopiesResult = useCallback(
      (result: Result<Paged<MessageCopy>>) => {
        setMessageCopies(result.map((r) => r.data))
        if (result.isSuccess) {
          setPages(result.value.pages)
        }
      },
      []
    )
    const loadMessageCopies = useRestApi(
      (accountId: UUID, page: number) =>
        getMessageCopies(accountId, page, PAGE_SIZE),
      setMessageCopiesResult
    )

    const setArchivedMessagesResult = useCallback(
      (result: Result<Paged<MessageThread>>) => {
        setArchivedMessages(result.map((r) => r.data))
        if (result.isSuccess) {
          setPages(result.value.pages)
        }
      },
      []
    )
    const loadArchivedMessages = useRestApi(
      (accountId: UUID, page: number) =>
        getArchivedMessages(accountId, page, PAGE_SIZE),
      setArchivedMessagesResult
    )

    const [singleThread, setSingleThread] = useState<Result<MessageThread>>()

    const loadThread = useRestApi(
      (accountId: UUID, threadId: UUID | null) =>
        getThread(accountId, threadId),
      setSingleThread
    )

    // load messages if account, view or page changes
    const loadMessages = useCallback(() => {
      if (!selectedAccount) {
        return
      }
      switch (selectedAccount.view) {
        case 'received':
          return void loadReceivedMessages(selectedAccount.account.id, page)
        case 'sent':
          return void loadSentMessages(selectedAccount.account.id, page)
        case 'drafts':
          return void loadMessageDrafts(selectedAccount.account.id)
        case 'copies':
          return void loadMessageCopies(selectedAccount.account.id, page)
        case 'archive':
          return void loadArchivedMessages(selectedAccount.account.id, page)
        case 'thread':
          return void loadThread(selectedAccount.account.id, threadId)
      }
    }, [
      loadMessageDrafts,
      loadReceivedMessages,
      loadSentMessages,
      loadMessageCopies,
      loadArchivedMessages,
      loadThread,
      threadId,
      page,
      selectedAccount
    ])

    const refreshMessages = useCallback(
      (accountId?: UUID) => {
        if (!accountId || selectedAccount?.account.id === accountId) {
          loadMessages()
        }
      },
      [loadMessages, selectedAccount]
    )

    const sentMessagesAsThreads: Result<MessageThread[]> = useMemo(
      () =>
        sentMessages.map((value) =>
          selectedAccount
            ? value.map((message) => ({
                id: message.contentId,
                type: message.type,
                title: message.threadTitle,
                urgent: message.urgent,
                isCopy: false,
                participants: message.recipientNames,
                children: [],
                messages: [
                  {
                    id: message.contentId,
                    threadId: message.contentId,
                    sender: { ...selectedAccount.account },
                    sentAt: message.sentAt,
                    recipients: message.recipients,
                    readAt: HelsinkiDateTime.now(),
                    content: message.content,
                    attachments: message.attachments,
                    recipientNames: message.recipientNames
                  }
                ]
              }))
            : []
        ),
      [selectedAccount, sentMessages]
    )

    const messageCopiesAsThreads: Result<MessageThread[]> = useMemo(
      () =>
        messageCopies.map((value) =>
          value.map((message) => ({
            ...message,
            id: message.threadId,
            isCopy: true,
            participants: [message.recipientName],
            children: [],
            messages: [
              {
                id: message.messageId,
                threadId: message.threadId,
                sender: {
                  id: message.senderId,
                  name: message.senderName,
                  type: message.senderAccountType
                },
                sentAt: message.sentAt,
                recipients: [
                  {
                    id: message.recipientId,
                    name: message.recipientName,
                    type: message.recipientAccountType
                  }
                ],
                readAt: message.readAt,
                content: message.content,
                attachments: message.attachments,
                recipientNames: message.recipientNames
              }
            ]
          }))
        ),
      [messageCopies]
    )

    const setSelectedThread = useCallback(
      (threadId: string | undefined) =>
        setParams({
          threadId,
          accountId,
          messageBox,
          unitId
        }),
      [accountId, messageBox, setParams, unitId]
    )
    const selectThread = useCallback(
      (thread: MessageThread | undefined) => {
        setSelectedThread(thread?.id)
        if (!selectedAccount) throw new Error('Should never happen')

        const accountId = selectedAccount.account.id
        const hasUnreadMessages = thread?.messages.some(
          (m) => !m.readAt && m.sender.id !== accountId
        )
        if (thread && hasUnreadMessages) {
          void markThreadRead(accountId, thread.id).then(() => {
            refreshMessages(accountId)
            void refreshUnreadCounts()
          })
        }
      },
      [setSelectedThread, selectedAccount, refreshMessages, refreshUnreadCounts]
    )
    const selectedThread = useMemo(
      () =>
        [
          ...receivedMessages.getOrElse([]),
          ...sentMessagesAsThreads.getOrElse([]),
          ...messageCopiesAsThreads.getOrElse([]),
          ...archivedMessages.getOrElse([]),
          singleThread?.getOrElse(undefined)
        ].find((t) => t?.id === threadId),
      [
        receivedMessages,
        sentMessagesAsThreads,
        messageCopiesAsThreads,
        archivedMessages,
        singleThread,
        threadId
      ]
    )

    const openMessageUndo = useCallback(
      (message: CancelableMessage) => {
        addNotification(
          {
            icon: faCheck,
            iconColor: theme.colors.main.m1,
            children: (
              <UndoMessage
                message={message}
                close={() => removeNotification('undo-message')}
              />
            ),
            dataQa: 'undo-message-toast'
          },
          'undo-message'
        )
      },
      [addNotification, removeNotification, theme.colors.main.m1]
    )

    const appendMessageToSingleThread = useCallback(
      (message: Message) =>
        setSingleThread(
          (prevState: Result<MessageThread> | undefined) =>
            prevState?.map((thread) => ({
              ...thread,
              messages: [...thread.messages, message]
            }))
        ),
      [setSingleThread]
    )

    const setReplyResponse = useCallback(
      (res: Result<ThreadReply>) => {
        if (res.isSuccess) {
          const {
            value: { message, threadId }
          } = res
          if (selectedAccount?.view === 'thread') {
            appendMessageToSingleThread(message)
          } else {
            setReceivedMessages(
              appendMessageAndMoveThreadToTopOfList(threadId, message)
            )
          }
          setSelectedThread(threadId)
          setReplyContents((state) => ({ ...state, [threadId]: '' }))
        }
      },
      [selectedAccount, appendMessageToSingleThread, setSelectedThread]
    )
    const sendReply = useRestApi(
      (params: ReplyToThreadParams) =>
        replyToThread(params).then((result) => {
          if (result.isSuccess) {
            openMessageUndo({
              accountId: params.accountId,
              messageId: result.value.message.id,
              sentAt: HelsinkiDateTime.now()
            })
          }
          return result
        }),
      setReplyResponse
    )

    const [replyContents, setReplyContents] = useState<RepliesByThread>({})

    const getReplyContent = useCallback(
      (threadId: UUID) => replyContents[threadId] ?? '',
      [replyContents]
    )
    const setReplyContent = useCallback((threadId: UUID, content: string) => {
      setReplyContents((state) => ({ ...state, [threadId]: content }))
    }, [])

    const selectUnit = useCallback(
      (unitId: string) => {
        const firstUnitGroupAccount = groupAccounts.find(
          (acc) => acc.daycareGroup.unitId === unitId
        )
        if (firstUnitGroupAccount) {
          setParams({
            accountId: firstUnitGroupAccount?.account.id,
            messageBox: groupMessageBoxes[0],
            unitId
          })
        } else {
          setParams({ unitId })
        }
      },
      [groupAccounts, setParams]
    )

    const selectAccount = useCallback(
      (accountView: AccountView) =>
        setParams({
          accountId: accountView.account.id,
          messageBox: accountView.view,
          unitId: accountView.unitId
        }),
      [setParams]
    )

    const selectDefaultAccount = useCallback(() => {
      if (serviceWorkerAccount) {
        setParams({
          messageBox: messageBox ?? serviceWorkerMessageBoxes[0],
          accountId: serviceWorkerAccount.account.id,
          unitId: null,
          threadId: threadId
        })
      } else if (municipalAccount) {
        setParams({
          messageBox: messageBox ?? municipalMessageBoxes[0],
          accountId: municipalAccount.account.id,
          unitId: null,
          threadId: threadId
        })
      } else if (personalAccount) {
        setParams({
          messageBox: personalMessageBoxes[0],
          accountId: personalAccount.account.id,
          unitId: null,
          threadId: threadId
        })
      } else if (groupAccounts.length > 0) {
        setParams({
          messageBox: groupMessageBoxes[0],
          accountId: groupAccounts[0].account.id,
          unitId: groupAccounts[0].daycareGroup.unitId,
          threadId: threadId
        })
      }
    }, [
      setParams,
      groupAccounts,
      municipalAccount,
      serviceWorkerAccount,
      personalAccount,
      messageBox,
      threadId
    ])

    const value = useMemo(
      () => ({
        accounts,
        municipalAccount,
        serviceWorkerAccount,
        personalAccount,
        groupAccounts,
        unitOptions,
        selectedDraft,
        setSelectedDraft,
        selectedAccount,
        selectAccount,
        selectDefaultAccount,
        selectUnit,
        page,
        setPage,
        pages,
        setPages,
        receivedMessages,
        sentMessages,
        messageDrafts,
        messageCopies,
        archivedMessages,
        setSelectedThread,
        selectedThread,
        selectThread,
        sendReply,
        getReplyContent,
        setReplyContent,
        refreshMessages,
        unreadCountsByAccount,
        sentMessagesAsThreads,
        messageCopiesAsThreads,
        openMessageUndo,
        prefilledRecipient,
        prefilledTitle,
        relatedApplicationId,
        accountAllowsNewMessage
      }),
      [
        accounts,
        municipalAccount,
        serviceWorkerAccount,
        personalAccount,
        groupAccounts,
        unitOptions,
        selectedDraft,
        selectedAccount,
        selectAccount,
        selectDefaultAccount,
        selectUnit,
        page,
        pages,
        receivedMessages,
        sentMessages,
        messageDrafts,
        messageCopies,
        archivedMessages,
        setSelectedThread,
        selectedThread,
        selectThread,
        sendReply,
        getReplyContent,
        setReplyContent,
        refreshMessages,
        unreadCountsByAccount,
        sentMessagesAsThreads,
        messageCopiesAsThreads,
        openMessageUndo,
        prefilledRecipient,
        prefilledTitle,
        relatedApplicationId,
        accountAllowsNewMessage
      ]
    )

    return (
      <MessageContext.Provider value={value}>
        {children}
      </MessageContext.Provider>
    )
  }
)
