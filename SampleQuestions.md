# RAG Application Test Queries

This document contains test queries for evaluating the RAG (Retrieval-Augmented Generation) application's ability to answer questions based on the provided knowledge base documents.

## Documents in Knowledge Base

1. Apache Spark Cheat Sheet (ApacheSparkCheatSheet.pdf)
2. Apache Kafka Cheat Sheet (KafkaCheatSheet.pdf)
3. Credit Risk Assessment Application (AppF_CreditRiskAssessment.docx)
4. Applications Spreadsheet (Applications.xlsx)

---

## Category 1: Queries with References in Documents

These queries can be answered using information from the provided documents.

### Apache Spark Queries

**Query 1.1:**
> What are the main components of Apache Spark and what does each component do?

**Expected Source:** ApacheSparkCheatSheet.pdf

---

**Query 1.2:**
> How do you create RDDs in Apache Spark?

**Expected Source:** ApacheSparkCheatSheet.pdf

---

**Query 1.3:**
> What is the difference between transformations and actions on RDDs in Spark?

**Expected Source:** ApacheSparkCheatSheet.pdf

---

**Query 1.4:**
> How do you perform aggregations and grouping operations in Spark DataFrames?

**Expected Source:** ApacheSparkCheatSheet.pdf

---

**Query 1.5:**
> What are the key features of Apache Spark mentioned in the cheat sheet?

**Expected Source:** ApacheSparkCheatSheet.pdf

---

### Apache Kafka Queries

**Query 2.1:**
> How do you create a Kafka producer in Java to send messages to a topic?

**Expected Source:** KafkaCheatSheet.pdf

---

**Query 2.2:**
> What are the main components of the Kafka publish-subscribe system?

**Expected Source:** KafkaCheatSheet.pdf

---

**Query 2.3:**
> How do you configure and start a Kafka consumer to read messages from a topic?

**Expected Source:** KafkaCheatSheet.pdf

---

**Query 2.4:**
> What is the Kafka Connect framework and what is it used for?

**Expected Source:** KafkaCheatSheet.pdf

---

**Query 2.5:**
> How do you implement a word count application using Kafka Streams?

**Expected Source:** KafkaCheatSheet.pdf

---

### Credit Risk Assessment Queries

**Query 3.1:**
> What is the purpose of the Credit Risk Assessment application described in Appendix F?

**Expected Source:** AppF_CreditRiskAssessment.docx

---

**Query 3.2:**
> What technology stack or components are used in the Credit Risk Assessment system?

**Expected Source:** AppF_CreditRiskAssessment.docx

---

**Query 3.3:**
> How does the Credit Risk Assessment application process data or make predictions?

**Expected Source:** AppF_CreditRiskAssessment.docx

---

**Query 3.4:**
> What are the inputs and outputs of the Credit Risk Assessment model?

**Expected Source:** AppF_CreditRiskAssessment.docx

---

**Query 3.5:**
> Are there any performance metrics or accuracy measurements mentioned for the Credit Risk Assessment model?

**Expected Source:** AppF_CreditRiskAssessment.docx

---

**Query 3.6:**
> What type of data is used to train the ML models in the Credit Risk Assessment application?

**Expected Source:** AppF_CreditRiskAssessment.docx

---

### Applications Spreadsheet Queries

**Query 4.1:**
> What are the different types of applications listed and their current status?

**Expected Source:** Applications.xlsx

---

**Query 4.2:**
> How many applications are documented in the spreadsheet?

**Expected Source:** Applications.xlsx

---

---

## Category 2: Queries WITHOUT References in Documents

These queries cannot be answered from the provided documents and should result in the RAG application indicating that the information is not available in the knowledge base.

### Technology Comparison Queries

**Query 5.1:**
> What is the difference between Apache Flink and Apache Storm for stream processing?

**Expected Response:** Information not available in knowledge base (Neither Flink nor Storm are covered)

---

**Query 5.2:**
> What are the differences between relational and NoSQL databases in terms of ACID compliance?

**Expected Response:** Information not available in knowledge base (Database comparisons are not discussed)

---

### Security & Authentication Queries

**Query 5.3:**
> How do you implement OAuth 2.0 authentication in a microservices architecture?

**Expected Response:** Information not available in knowledge base (Authentication protocols and microservices security are not discussed)

---

### Deployment & Infrastructure Queries

**Query 5.4:**
> What are the best practices for deploying machine learning models in production using Kubernetes?

**Expected Response:** Information not available in knowledge base (While Spark MLlib is mentioned, Kubernetes deployment specifics are not covered)

---

### Blockchain & Supply Chain Queries

**Query 5.5:**
> How does blockchain technology work in supply chain management?

**Expected Response:** Information not available in knowledge base (Blockchain is not mentioned in any documents)

---

### Additional Out-of-Scope Queries

**Query 5.6:**
> What are the key principles of Domain-Driven Design in software architecture?

**Expected Response:** Information not available in knowledge base

---

**Query 5.7:**
> How do you optimize PostgreSQL queries for better performance?

**Expected Response:** Information not available in knowledge base

---

**Query 5.8:**
> What are the best practices for implementing CI/CD pipelines with Jenkins?

**Expected Response:** Information not available in knowledge base

---

**Query 5.9:**
> How does React's virtual DOM differ from Angular's change detection mechanism?

**Expected Response:** Information not available in knowledge base

---

**Query 5.10:**
> What are the security considerations when implementing a GraphQL API?

**Expected Response:** Information not available in knowledge base

---

## Testing Guidelines

### For Category 1 Queries (With References):
- The RAG application should provide accurate answers with proper citations
- Responses should reference the specific document source
- Answers should be directly supported by the document content

### For Category 2 Queries (Without References):
- The RAG application should acknowledge that the information is not available
- It should NOT attempt to generate answers from its general knowledge
- It should NOT hallucinate or make up information
- Appropriate responses include:
  - "This information is not available in the provided documents."
  - "I cannot find relevant information about this topic in the knowledge base."
  - "The documents do not contain information about [topic]."

---

## Notes

- This README is designed to test the RAG application's ability to distinguish between information it has access to and information it doesn't have
- Proper handling of out-of-scope queries is crucial for avoiding hallucinations
- The application should always prioritize accuracy and transparency about its limitations

---

**Last Updated:** October 23, 2025
