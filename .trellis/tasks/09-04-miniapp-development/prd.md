# 小程序功能开发

## Goal

在小程序分支上进行后续微信小程序功能开发

## Requirements

- TBD

## Acceptance Criteria

- [ ] TBD

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
# 小程序教师端选项班学生同步修复

## 需求
- 当前选项班没有本学期学生时，小程序不得继续显示上学期残留学生。
- 活动同步时，未在本活动确认选课的学生应清空对应选项班归属。

## 验收标准
- 本活动无确认学生的选项班接口返回空列表。
- 学生历史确认选课不会作为当前活动的选项班兜底数据。
- 其他行政班和已有本活动确认选课的选项班行为不变。
