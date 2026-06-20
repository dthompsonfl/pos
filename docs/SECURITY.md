# EnterprisePOS Security Guide

## Overview

EnterprisePOS implements defense-in-depth security for payment data, employee authentication, and audit compliance. All security measures are designed to meet PCI-DSS requirements for card-present environments.

## Authentication

### Employee PIN

- PINs are hashed using Argon2id with unique salts per employee
- Minimum 4-digit PIN enforced at entry
- Progressive lockout: 3 failed attempts trigger 5-minute lock, 6 attempts trigger 30-minute lock, 10 attempts require manager override
- PINs are never stored in plaintext or logged

### Session Management

- Sessions expire after 30 minutes of inactivity
- Sessions are bound to device and employee ID
- Re-authentication required for high-risk operations (refunds, voids, manager overrides)
- Session tokens are rotated on every sensitive operation

### Role-Based Access Control

| Role | Permissions |
|------|-------------|
| MANAGER | Full access including refunds, voids, employee management, and report access |
| CASHIER | Order creation, payment processing, and basic reporting |
| KITCHEN | View orders, mark items prepared, no payment access |
| ADMIN | System configuration, security settings, and audit log access |

## Data Protection

### Encryption at Rest

- Sensitive fields encrypted with AES-256-GCM using hardware-backed keystore when available
- Encryption keys are stored in Android Keystore with strongbox requirement when supported
- Database file is encrypted using SQLCipher with 256-bit key
- Key rotation every 90 days with automatic re-encryption

### Encryption in Transit

- All API communication uses TLS 1.3 with certificate pinning
- WebSocket connections use WSS with same certificate pinning
- Payment terminal communication uses Stripe's encrypted protocol
- No sensitive data in URL parameters or query strings

### Key Management

- Encryption keys are never hardcoded
- Keys are derived from hardware security module when available
- Key derivation uses PBKDF2 with 100,000 iterations minimum
- Master key encrypted with device credential (PIN/pattern/password)

## Payment Security

### Card Data

- Full PAN is never stored on device
- Only last 4 digits and tokenized references are retained
- CVV/CVC is never captured or logged
- Stripe Terminal handles all card data in PCI-compliant hardware

### Offline Payments

- Offline transactions are encrypted and stored in secure queue
- Queue uses AES-256-GCM with keys derived from device keystore
- Maximum offline queue depth: 100 transactions
- Automatic sync when connectivity restored
- Failed syncs retry with exponential backoff

## Audit Logging

### Immutable Audit Trail

All security-relevant events are logged with:
- Timestamp (UTC)
- Employee ID and name
- Store and register ID
- Action type (enum)
- Entity type and ID
- Before/after state for modifications
- Digital signature for tamper detection

### Log Storage

- Local encrypted SQLite database with append-only semantics
- Sync to central server every 60 seconds
- Retention: 7 years for PCI compliance
- No deletion or modification capability in app

### Audited Events

- Employee login/logout
- Order creation, modification, void, refund
- Payment authorization, capture, refund
- Discount application and override
- Hardware configuration changes
- Security settings changes
- Failed authentication attempts

## Network Security

### Certificate Pinning

- SHA-256 hashes of production certificate public keys embedded in app
- Pinning enforced on all API and WebSocket connections
- Certificate transparency log monitoring for early detection
- Automatic block on pinning failures with admin alert

### API Security

- Bearer token authentication with 15-minute expiration
- Refresh tokens with 7-day expiration and rotation
- Rate limiting: 1000 requests per minute per token
- Request signing with HMAC-SHA256 for sensitive endpoints
- IP allowlisting for admin operations

## Device Security

### Root Detection

- Runtime root detection using multiple indicators
- Root detection triggers immediate session termination
- Alert sent to admin dashboard on root detection
- App refuses to run on rooted or jailbroken devices

### Screen Security

- Screenshots disabled for sensitive screens (payment, PIN entry)
- Screen recording blocked during payment processing
- Automatic screen lock when app backgrounded
- Secure flag set on payment activities

### Tamper Detection

- App signature verification on startup
- Integrity check for critical code sections
- Debug build detection and blocking
- Emulator detection for production builds

## Compliance

### PCI-DSS

- Cardholder data environment isolated from general app
- Annual security assessment by QSA
- Quarterly vulnerability scans
- Secure software development lifecycle (SSDLC)

### GDPR

- Customer data encrypted and anonymized
- Right to deletion supported via admin API
- Data processing agreements with all sub-processors
- Privacy by design in all features

### SOC 2 Type II

- Access controls audited quarterly
- Change management process enforced
- Incident response plan with 24-hour SLA
- Backup and recovery tested annually

## Incident Response

### Security Incident Types

| Severity | Examples | Response Time |
|----------|----------|---------------|
| Critical | Data breach, payment fraud | 15 minutes |
| High | Unauthorized access, malware | 1 hour |
| Medium | Policy violation, misconfiguration | 24 hours |
| Low | Documentation issue, minor finding | 7 days |

### Response Process

1. Detection: Automated monitoring and manual reporting
2. Containment: Isolate affected systems immediately
3. Investigation: Determine scope and root cause
4. Remediation: Fix vulnerability and verify
5. Communication: Notify affected parties per SLA
6. Post-incident: Update controls and documentation

## Security Testing

- Static analysis with Semgrep and Detekt on every build
- Dependency vulnerability scanning with OWASP Dependency Check
- Penetration testing annually by third-party firm
- Bug bounty program for responsible disclosure
- Security code review required for all payment-related changes

## Reporting Security Issues

Report security vulnerabilities to security@enterprisepos.com with:
- Description of the vulnerability
- Steps to reproduce
- Potential impact assessment
- Suggested remediation (if any)

Response within 24 hours for critical issues, 72 hours for all others.

## Security Checklist

Before every release:

- [ ] No secrets or credentials in source code
- [ ] All dependencies scanned for known vulnerabilities
- [ ] Encryption keys use hardware-backed storage
- [ ] Certificate pinning updated if certificates changed
- [ ] Root detection and tamper detection active
- [ ] Audit logging covers all required events
- [ ] Role permissions enforced in all code paths
- [ ] Session timeout configured correctly
- [ ] Offline payment encryption verified
- [ ] Security unit tests pass
