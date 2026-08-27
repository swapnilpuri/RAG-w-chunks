# Synthetic Question Generation Notes

Use this prompt pattern when creating evaluation questions:

```text
Given the synthetic documents below, create JSONL evaluation questions for a RAG system.
Include easy single-document questions, medium multi-document questions, hard comparison questions, and out-of-scope questions.
Each record must include id, question, expected_sources, expected_answer_contains, should_answer, difficulty, and category.
```

Question mix target:

- 40 percent single-document factual
- 25 percent multi-document or comparison
- 20 percent incident/root-cause
- 15 percent out-of-scope refusal

Out-of-scope questions should be plausible but absent from the documents.
