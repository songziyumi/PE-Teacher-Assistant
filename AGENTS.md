## 验证命令
- 编译：mvn -q -Dmaven.repo.local=.m2repo -DskipTests compile
- 分析：dart analyze --no-fatal-warnings
- 超时限制：300秒

## 执行约束
- 默认使用单条命令顺序执行；只有在调用格式已经确认无误且确有必要时，才使用并行工具。
- 同一类工具调用或参数格式错误，连续出现 2 次后必须停止重试，改为最小可行的单条命令路径。
- 收集上下文时先做最小读取，不一次性堆叠多个命令；先验证命令可执行，再扩大搜索范围。
- 如果判断问题来自工具调用方式而不是仓库代码，必须立即切换方案，不得在同一错误模式上空转。
- 在开始编辑前，先确认当前读取和搜索命令已经成功执行；若未成功，不得继续假设性推进。

## Session Safeguards
- On Windows PowerShell, avoid direct shell-based rewriting of Java/HTML files with Chinese text or annotation-heavy content; prefer restoring clean file content first, then apply the smallest possible change.
- After any source-file recovery or encoding repair, rewrite files as UTF-8 without BOM before compiling.
- Before runtime verification, ensure only one target app instance is serving the validation port; do not trust browser behavior when multiple Java processes may still be running.
- For high-risk files (security config, dashboard/controller/template, entity/service constants), compile immediately after each focused fix instead of batching multiple risky edits.
- If behavior differs from compiled source, verify the active listener PID and compiled class signatures before changing more code.
