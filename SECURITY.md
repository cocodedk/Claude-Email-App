# Security Policy

## Reporting a Vulnerability

Do **not** open a public GitHub issue for security vulnerabilities.

To report a vulnerability:
- Use the **"Report a vulnerability"** button on the [Security tab](https://github.com/cocodedk/Claude-Email-App/security) of this repository (GitHub private advisory)
- Or email: babak@cocode.dk

We will acknowledge within 5 business days and aim to release a fix within 30 days of confirmation.

## Credential Handling

Claude-Email-App stores IMAP/SMTP credentials locally using the Android Keystore. Credentials never leave the device. If you believe a build leaks credentials, treat it as a high-priority vulnerability and report via the channels above.

## Supported Versions

| Version | Supported |
|---------|-----------|
| latest  | ✅ |
| older   | ❌ |
