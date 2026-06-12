export default {
    title: '助教邀请',
    tabs: {
        received: '收到的邀请',
        sent: '发出的邀请',
    },
    status: {
        pending: '待处理',
        accepted: '已接受',
        declined: '已拒绝',
        withdrawn: '已撤回',
    },
    actions: {
        accept: '接受',
        decline: '拒绝',
        withdraw: '撤回',
    },
    empty: {
        received: '暂无收到的邀请',
        sent: '暂无发出的邀请',
    },
    message: '邀请留言',
    invitedAt: '邀请时间',
    course: '课程',
    inviter: '邀请人',
    invitee: '被邀请人',
    alert: {
        acceptSuccess: '已接受邀请，您现在是该课程的助教',
        declineSuccess: '已拒绝邀请',
        withdrawSuccess: '已撤回邀请',
        acceptFailed: '接受邀请失败',
        declineFailed: '拒绝邀请失败',
        withdrawFailed: '撤回邀请失败',
    },
    confirm: {
        withdrawTitle: '撤回邀请',
        withdrawMessage: '确定要撤回对该教师的邀请吗？',
    },
}
