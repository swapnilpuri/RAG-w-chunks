# AppC HR Onboarding

## Overview

AppC HR Onboarding is a synthetic internal workflow application for employee onboarding. It coordinates paperwork, equipment requests, identity setup, and manager tasks for new hires.

## Workflow Stages

The onboarding workflow has five stages: offer accepted, identity created, equipment requested, compliance completed, and manager confirmation.

## Integrations

AppC integrates with the HRIS system for employee records, the identity provider for account creation, the IT service desk for laptop requests, and the learning platform for mandatory training.

## SLA Targets

Identity setup should finish within 4 business hours after offer acceptance. Laptop request creation should finish within 1 business day. Compliance training should be completed before the employee start date.

## Data Handling

The application stores employee ID, manager ID, start date, department, office location, workflow status, and task completion timestamps. It must not store social security numbers or bank account details.

## Known Operational Risks

The most common issue is missing manager approval, which blocks equipment fulfillment. A reminder is sent after 24 hours and escalated after 48 hours.

## Ownership

The owning team is People Systems. The primary support channel is `#people-systems-support`.
