# Synthetic Incident Reports

## Incident IR-2026-014: Payment Callback Delay

Date: 2026-03-18
Impacted application: AppA Payment Gateway
Severity: SEV-2
Duration: 47 minutes

During a flash sale, bank callback events were delayed. Payment transactions stayed in `PENDING_CONFIRMATION`, and some customers retried checkout. The root cause was saturation in the callback worker pool. The mitigation was to increase callback consumers from 8 to 24 and temporarily lower retry backoff.

Follow-up actions:

- Add autoscaling based on callback queue depth.
- Add an alert when pending confirmations exceed 5,000 transactions.
- Run a load test before the next sale event.

## Incident IR-2026-021: Loyalty Rule Over-Award

Date: 2026-04-07
Impacted application: AppB Customer Loyalty
Severity: SEV-3
Duration: 2 hours 12 minutes

A campaign rule awarded 5x points instead of 2x points for electronics purchases. The root cause was a manual configuration error that bypassed the approval checklist. The mitigation was to disable the campaign rule and run a correction job.

Follow-up actions:

- Enforce two-person approval in the campaign rule admin UI.
- Add pre-activation simulation for high-value campaigns.
- Emit an anomaly alert when points awarded exceed the expected campaign range.

## Incident IR-2026-033: Onboarding Equipment Blocked

Date: 2026-05-12
Impacted application: AppC HR Onboarding
Severity: SEV-3
Duration: 1 business day

Equipment request creation was blocked for new hires in the Toronto office. The root cause was an invalid office location mapping between AppC and the IT service desk. The mitigation was to patch the location mapping and replay failed onboarding tasks.

Follow-up actions:

- Validate office mappings nightly.
- Add dead-letter monitoring for equipment request events.
- Include office location in the onboarding readiness dashboard.
