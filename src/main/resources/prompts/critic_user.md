Context:
- prior_knowledge: {{prior_knowledge}}
- compacted_history: {{compacted_history}}
- iteration: {{iteration}}
- goal: {{goal}}
- required_drivers: {{required_drivers}}
- prior_carryover: {{prior_carryover}}
Review target:
- design: {{design}}
- diagram_code: {{diagram_code}}
Checks:
- Do not fail the current iteration for missing drivers reserved for later iterations.
- ADD consistency and traceability to provided prior knowledge only.
- If iteration = 1: overall structure, CRN-1, CON-1, CON-2, CON-6
- If iteration = 2: HPS-1, HPS-2, HPS-3, HPS-4, HPS-5, HPS-6, CON-5
- If iteration = 3: QA-2, QA-3, QA-8
- If iteration = 4: QA-5, QA-6, QA-7, QA-9, CON-3, CON-4
Required evidence policy:
- For each required driver, check explicit evidence in design and/or diagram, not only keyword mention.
- Accept concise evidence; do not require implementation-level detail.
- Evidence must be a concrete structural element: component, interface, protocol, deployment mechanism, or metric mechanism.
- If evidence is missing, add one actionable issue with a concrete fix target.
Issue writing policy:
- Keep issues specific and non-redundant.
- Cap issues to at most 5 items.
- Each issue must be classified with a severity tag prefix:
  - [BLOCK]: required driver for the current iteration has no structural evidence in design or diagram; must be fixed before pass=true.
  - [WARN]: required driver is present but evidence is vague, inconsistent between design and diagram, or missing an interface/protocol/mechanism detail expected at this iteration's altitude.
  - [NIT]: minor clarity, naming, or traceability improvement that does not affect driver satisfaction.
- Every issue string must begin with one of [BLOCK], [WARN], or [NIT].
- The issues array must also be reflected as structured objects with a severity field in the JSON output (see Critic system prompt).
Driver check output:
- In addition to issues, emit a driver_check array.
- Include one entry per required driver for the current iteration only (per the iteration list above).
- Each entry is an object with fields:
  - driver_id (string): the driver identifier, e.g. "HPS-2", "QA-3", "CON-1", "CRN-1".
  - evidence_in_design (string): a brief quote or paraphrase pointing to where the driver is addressed in design; empty string if none.
  - evidence_in_diagram (string): the node, edge, or subgraph label in diagram_code that addresses the driver; empty string if none.
  - verdict (string): one of "met" | "partial" | "missing".
    - met: explicit structural evidence in both design and diagram (or in the artifact where the driver naturally belongs).
    - partial: evidence exists but is vague, only in one artifact when both are expected, or lacks an interface/protocol/mechanism detail.
    - missing: no structural evidence.
- driver_check must be internally consistent with issues:
  - any driver with verdict="missing" must produce a [BLOCK] issue.
  - any driver with verdict="partial" must produce at least a [WARN] issue (unless already covered by a [BLOCK]).
Carryover handling:
- If prior_carryover is non-empty, treat each carry-over item as an explicit required check; the new design must address it. Surface unresolved carry-over items as [BLOCK] issues.
Revision control:
- if any issue has severity [BLOCK], set pass=false.
- if all remaining issues are [WARN] or [NIT] and all required drivers have verdict "met" or "partial", pass may be true; otherwise set pass=false and list actionable issues.
