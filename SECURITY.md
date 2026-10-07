# Security Policy

## Reporting a Vulnerability

Do **not** open a public GitHub issue for security vulnerabilities.

To report a vulnerability:
- Use the **"Report a vulnerability"** button on the [Security tab](https://github.com/cocodedk/Claude-Email-App/security) of this repository (GitHub private advisory)

We will acknowledge within 5 business days and aim to release a fix within 30 days of confirmation.

## Credential Handling

Claude-Email-App stores your mail password and shared secret on the device, encrypted, with a key kept in the Android Keystore. They leave the device in two ways, both on purpose: the mail password goes to your own mail servers over encrypted connections, to sign in, and the shared secret goes in the commands you send to your claude-email service. Android may include the app's encrypted data in its backups. If you believe a build leaks credentials in any other way, treat it as a high-priority vulnerability and report via the channel above.

## Supported Versions

| Version | Supported |
|---------|-----------|
| latest  | ✅ |
| older   | ❌ |
