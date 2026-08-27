# AppB Customer Loyalty

## Overview

AppB Customer Loyalty is a synthetic rewards platform used by Northwind Retail Labs. It calculates loyalty points, tier upgrades, promotional rewards, and redemption eligibility.

## Core Capabilities

The system awards points for purchases, referrals, product reviews, and seasonal campaigns. Customers can redeem points for coupons, shipping discounts, and partner offers.

## Tier Model

The loyalty tiers are Silver, Gold, and Platinum. Silver starts at 0 points, Gold starts at 10,000 lifetime points, and Platinum starts at 35,000 lifetime points.

## Architecture

Purchase events are consumed from the `orders.completed` Kafka topic. The Loyalty Rules Engine evaluates earning rules and writes point transactions to PostgreSQL. Redis is used to cache the customer tier summary.

## Business Rules

Points from standard purchases expire after 18 months. Promotional points expire after 90 days. Refund events reverse previously awarded points using the original order ID.

## Known Operational Risks

The most common issue is rule misconfiguration during marketing campaigns. A campaign rule must be approved by both Marketing Operations and Loyalty Engineering before activation.

## Ownership

The owning team is Customer Growth. The primary support channel is `#customer-growth-support`.
