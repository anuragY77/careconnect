# Prompt Templates for Working With the AI Assistant

Use these to keep the assistant grounded in the project documents. Replace the bracketed parts.

## 1. Start of Session (load context, no code)

```
Read these files before making any changes:
PRD.md
ARCHITECTURE.md
RULES.md
SECURITY.md
DECISIONS.md
TASKS.md
MEMORY.md

Do not modify anything yet.
1. Summarize the product and the current phase.
2. Summarize the architecture rules that apply.
3. Tell me what is missing or contradictory.
4. Explain your implementation plan for [TASK-xxx].
Do not write code yet.
```

## 2. Implement a Task

```
CONTEXT
We are building CareConnect. Read PRD.md, ARCHITECTURE.md, RULES.md and SECURITY.md.
Phase: [phase]. Task: [TASK-xxx]. Requirement: [FR-xx], use case [UC-xx].

TASK
[one small, specific thing]

FILES
Relevant files or packages: [list]

CONSTRAINTS
- Follow the existing architecture and module boundaries.
- Reuse existing code. Do not modify unrelated files.
- No invented clinical rules; use PRD business rules [BR-xx] only.
- Validate input; verify authorization and audit.
- Only synthetic data.

ACCEPTANCE CRITERIA
- [criterion 1]
- [criterion 2]

TESTING
Add unit and integration tests, including failure paths and an authorization test.
Run lint, type check, tests and build.

REPORT
1. Files changed
2. What was implemented
3. Tests executed and results
4. Remaining issues
```

## 3. Review Before Merge

```
Review the implementation against PRD.md, ARCHITECTURE.md, RULES.md, SECURITY.md, TEST_PLAN.md and DESIGN.md.
Check: correctness, business rules [BR-xx], architecture and module boundaries, security and authorization,
audit coverage, error handling, accessibility, responsive design, performance, duplication, tests.
Do not modify anything yet. List all issues by severity first.
```

## 4. Debug

```
ERROR
[paste error]

EXPECTED BEHAVIOR
[what should happen]

ACTUAL BEHAVIOR
[what happens]

STEPS TO REPRODUCE
1. ...
2. ...

CONSTRAINT
Do not change the database schema or unrelated code.

Do not modify code yet. Find the root cause:
1. What is failing and why?
2. Which file or class is responsible?
3. What is the smallest fix?
4. How will we test the fix?
Then implement the smallest fix, add a regression test and run the relevant tests.
```

## 5. Safety Rule Work (extra care)

```
We are working on medication safety rule [rule name].
The behavior is defined only by PRD.md BR-03, BR-04, BR-05 and BR-17 and by DECISIONS.md.
Do not add, change or assume any clinical thresholds or drug data yourself.
If the rule needs data or a decision that is not in the documents, stop and ask me.
Write unit tests for: hit, miss, boundary values and missing data.
```

## 6. Update Documents After a Task

```
The task [TASK-xxx] is complete. Update:
- TASKS.md (mark complete, add follow-up tasks if any)
- MEMORY.md (status, current task, known issues, next step)
- DECISIONS.md (only if a decision was made)
- ARCHITECTURE.md or PRD.md (only if the implementation changed what they describe)
Show me the diff of the documents. Do not change code.
```

## 7. Check Documents Against Each Other

```
Compare PRD.md, ARCHITECTURE.md, PHASES.md and TASKS.md.
Report any requirement without a use case, test, module, phase or task,
any name that differs between the class diagram, data model and architecture,
and any rule that contradicts another. Do not edit anything yet.
```

## 8. Rules of Thumb

- Never ask for the whole application in one prompt. Ask for one small, testable task.
- Ask for a plan and review it before code.
- When unsure, ask the assistant to list assumptions instead of choosing silently.
- Commit after each working task, so a bad change can be rolled back.
