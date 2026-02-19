# 150_project-window-introspection

## Goal
Расширить state API данными о текущем UI-состоянии Cubism.

## Scope
- Активная вкладка и список открытых вкладок/документов.
- Тип активного документа (model/animation/etc).
- Базовая информация об активном окне/режиме редактирования.
- Snapshot consistency в рамках одного вызова.

## Deliverables
- Endpoint `GET /state/ui`.
- Расширение `GET /state` секцией `ui`.
- Док по гарантиям консистентности и ограничениям.
