export default {
    title: 'Assistant Invitations',
    tabs: {
        received: 'Received',
        sent: 'Sent',
    },
    status: {
        pending: 'Pending',
        accepted: 'Accepted',
        declined: 'Declined',
        withdrawn: 'Withdrawn',
    },
    actions: {
        accept: 'Accept',
        decline: 'Decline',
        withdraw: 'Withdraw',
    },
    empty: {
        received: 'No invitations received',
        sent: 'No invitations sent',
    },
    message: 'Message',
    invitedAt: 'Invited At',
    course: 'Course',
    inviter: 'Inviter',
    invitee: 'Invitee',
    alert: {
        acceptSuccess: 'Invitation accepted. You are now an assistant for this course.',
        declineSuccess: 'Invitation declined',
        withdrawSuccess: 'Invitation withdrawn',
        acceptFailed: 'Failed to accept invitation',
        declineFailed: 'Failed to decline invitation',
        withdrawFailed: 'Failed to withdraw invitation',
    },
    confirm: {
        withdrawTitle: 'Withdraw Invitation',
        withdrawMessage: 'Are you sure you want to withdraw this invitation?',
    },
}
