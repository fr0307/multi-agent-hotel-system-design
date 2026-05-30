Critic feedback to address (this is revision {{revision}} of the current iteration):
- revisions_used has reached {{revision}} out of {{max_revisions}}; after this revision the Critic will be the final gate and no further revision attempts remain for this iteration.
- Treatment of issues by severity is mandatory:
  (a) For each BLOCK issue: you MUST demonstrate the new structural evidence directly inside step5_components and step6_diagram (concrete component, interface, protocol, deployment mechanism, or metric mechanism). Vague prose is not acceptable.
  (b) For each WARN issue: you MUST either fix it with a concrete structural change, or explicitly justify why the current design already satisfies the driver, citing the component or interface that does so.
  (c) NIT issues MAY be ignored at your discretion and do not block acceptance.
- Do NOT restate the prior design verbatim. Emit only the deltas: changed or newly added components, interfaces, flows, and diagram edges that resolve the issues above.
- Every BLOCK issue must be traceable to at least one concrete change in this revision's step5_components or step6_diagram; unresolved BLOCK issues will cause the Critic to fail this iteration.
- Keep all previously-passing structural evidence intact; do not regress drivers that were already satisfied in earlier revisions or iterations.
- Address the issues in the order listed below so the Critic can map your deltas one-to-one.
Issues to address (verbatim):
{{issues}}
