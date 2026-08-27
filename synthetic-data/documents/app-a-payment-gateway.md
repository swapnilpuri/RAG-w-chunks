# AppA Payment Gateway

## Overview

AppA Payment Gateway is a synthetic payment processing service used by Northwind Retail Labs. It handles online checkout payments for web and mobile channels.

## Supported Payment Methods

AppA supports credit cards, debit cards, UPI, net banking, and stored wallet balance. Buy-now-pay-later is not currently supported.

## Architecture

The service exposes REST APIs through an API gateway. Payment requests are validated by the Checkout API and then published to the `payments.initiated` Kafka topic. A Payment Orchestrator consumes the event and calls the appropriate bank or wallet connector.

## Data Stores

AppA uses PostgreSQL for transaction state, Redis for short-lived idempotency keys, and Kafka for asynchronous event processing.

## Reliability Requirements

The target availability is 99.95 percent. Payment authorization should complete within 2 seconds for 95 percent of requests. Duplicate payment attempts must be prevented with idempotency keys that expire after 30 minutes.

## Known Operational Risks

The most common production issue is delayed bank callback processing during high-traffic sale events. When callbacks are delayed, transactions may remain in `PENDING_CONFIRMATION` for longer than expected.

## Ownership

The owning team is Payments Platform. The primary support channel is `#payments-platform-support`.
