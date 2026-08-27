# Synthetic Document Generation Notes

Use this prompt pattern when creating new synthetic documents:

```text
Create a realistic but fully synthetic internal application document for a RAG demo.
The document must not include real company, customer, employee, credential, or financial data.
Include: overview, capabilities, architecture, data stores, operational risks, ownership, and support channel.
Use clear headings and concrete facts that can be tested with question-answer evaluation.
```

Guidelines:

- Prefer specific but fake application names.
- Include enough factual detail for retrieval tests.
- Include some overlapping vocabulary across documents to make retrieval realistic.
- Avoid real secrets, real customer names, and real incident details.
