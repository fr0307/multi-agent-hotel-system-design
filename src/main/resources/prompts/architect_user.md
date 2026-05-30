Context:
- prior_knowledge: {{prior_knowledge}}
- compacted_history: {{compacted_history}}
- iteration: {{iteration}}
- goal: {{goal}}
- required_drivers: {{required_drivers}}
- prior_carryover: {{prior_carryover}}
Constraints:
- do not include QA checks; only design
- cover all required drivers for the current iteration goal
- make interfaces and key flows explicit and traceable in diagram_code
- compared with prior iterations, increase detail only where required by the current iteration goal
- If prior_carryover is non-empty, you MUST address each carry-over item in this iteration.
{{critic_block}}
