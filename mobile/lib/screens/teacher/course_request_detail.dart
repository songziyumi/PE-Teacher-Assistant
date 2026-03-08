import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../models/course_request.dart';
import '../../services/teacher_service.dart';

class CourseRequestDetailScreen extends StatefulWidget {
  final int requestId;
  const CourseRequestDetailScreen({super.key, required this.requestId});

  @override
  State<CourseRequestDetailScreen> createState() =>
      _CourseRequestDetailScreenState();
}

class _CourseRequestDetailScreenState extends State<CourseRequestDetailScreen> {
  final TextEditingController _remarkController = TextEditingController();
  bool _loading = true;
  bool _submitting = false;
  CourseRequest? _request;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _remarkController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final req = await TeacherService.getCourseRequestDetail(widget.requestId);
      if (!mounted) return;
      _request = req;
      if ((req.handleRemark ?? '').trim().isNotEmpty) {
        _remarkController.text = req.handleRemark!.trim();
      }
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('加载详情失败: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _handle(bool approve) async {
    if (_request == null || _submitting) return;
    setState(() => _submitting = true);
    try {
      final remark = _remarkController.text.trim();
      if (approve) {
        await TeacherService.approveCourseRequest(
          _request!.id,
          remark: remark.isEmpty ? null : remark,
        );
      } else {
        await TeacherService.rejectCourseRequest(
          _request!.id,
          remark: remark.isEmpty ? null : remark,
        );
      }
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(approve ? '已同意申请' : '已拒绝申请')));
      context.pop(true);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('操作失败: $e')));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  String _formatDate(DateTime? dt) {
    if (dt == null) return '-';
    final local = dt.toLocal().toString();
    return local.length >= 16 ? local.substring(0, 16) : local;
  }

  Color _statusColor(String status) {
    switch (status) {
      case 'APPROVED':
        return Colors.green;
      case 'REJECTED':
        return Colors.red;
      default:
        return Colors.orange;
    }
  }

  @override
  Widget build(BuildContext context) {
    final req = _request;
    return Scaffold(
      appBar: AppBar(title: const Text('审批详情')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : req == null
              ? Center(
                  child: ElevatedButton(
                    onPressed: _load,
                    child: const Text('重试'),
                  ),
                )
              : SingleChildScrollView(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              req.senderName ?? '未知学生',
                              style: const TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 10, vertical: 4),
                            decoration: BoxDecoration(
                              color: _statusColor(req.status).withOpacity(0.12),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: Text(
                              req.statusLabel,
                              style: TextStyle(
                                color: _statusColor(req.status),
                                fontSize: 12,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      _kv('课程', req.relatedCourseName ?? '-'),
                      _kv('主题', req.subject ?? '-'),
                      _kv('申请内容',
                          (req.content == null || req.content!.trim().isEmpty)
                              ? '-'
                              : req.content!),
                      _kv('申请时间', _formatDate(req.sentAt)),
                      const Divider(height: 28),
                      _kv('当前审批人', req.handledByName ?? '-'),
                      _kv('当前审批时间', _formatDate(req.handledAt)),
                      _kv(
                        '当前审批备注',
                        (req.handleRemark == null ||
                                req.handleRemark!.trim().isEmpty)
                            ? '-'
                            : req.handleRemark!,
                      ),
                      const SizedBox(height: 10),
                      const Text(
                        '审批日志',
                        style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
                      ),
                      const SizedBox(height: 8),
                      if (req.auditLogs.isEmpty)
                        const Text('暂无审批日志', style: TextStyle(color: Colors.grey))
                      else
                        ...req.auditLogs.map((log) {
                          final status = log.afterStatus ?? '';
                          final color = _statusColor(status);
                          return Container(
                            margin: const EdgeInsets.only(bottom: 8),
                            padding: const EdgeInsets.all(10),
                            decoration: BoxDecoration(
                              border: Border.all(color: color.withOpacity(0.3)),
                              borderRadius: BorderRadius.circular(10),
                              color: color.withOpacity(0.06),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    Text(
                                      log.actionLabel,
                                      style: TextStyle(
                                        color: color,
                                        fontWeight: FontWeight.bold,
                                      ),
                                    ),
                                    const SizedBox(width: 10),
                                    Text(
                                      _formatDate(log.handledAt),
                                      style: const TextStyle(
                                        color: Colors.black54,
                                        fontSize: 12,
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 4),
                                Text('审批人：${log.operatorTeacherName ?? '-'}'),
                                Text(
                                  '备注：${(log.remark == null || log.remark!.trim().isEmpty) ? '-' : log.remark!}',
                                ),
                              ],
                            ),
                          );
                        }),
                      if (req.isPending) ...[
                        const SizedBox(height: 14),
                        TextField(
                          controller: _remarkController,
                          maxLength: 500,
                          maxLines: 3,
                          decoration: const InputDecoration(
                            border: OutlineInputBorder(),
                            labelText: '审批备注（可选）',
                          ),
                        ),
                        const SizedBox(height: 10),
                        Row(
                          children: [
                            Expanded(
                              child: OutlinedButton(
                                onPressed:
                                    _submitting ? null : () => _handle(false),
                                style: OutlinedButton.styleFrom(
                                    foregroundColor: Colors.red),
                                child: const Text('拒绝'),
                              ),
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: ElevatedButton(
                                onPressed:
                                    _submitting ? null : () => _handle(true),
                                child: Text(_submitting ? '处理中...' : '同意'),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ],
                  ),
                ),
    );
  }

  Widget _kv(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: RichText(
        text: TextSpan(
          style: const TextStyle(fontSize: 14, color: Colors.black87),
          children: [
            TextSpan(
              text: '$label：',
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
            TextSpan(text: value),
          ],
        ),
      ),
    );
  }
}
