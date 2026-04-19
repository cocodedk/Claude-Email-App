# Claude Email Android Docs

This repository starts as a dedicated Android client for `../claude-email`.

Recommended product direction:

- Do **not** build a generic Outlook clone.
- Build a **single-account, claude-email-first** client that uses email transport under the hood but presents the experience as a focused command-and-reply app.
- Make it feel closer to a **messaging app for agents and tasks** than a traditional inbox.
- Use **IMAP for receiving/sync** and **SMTP for sending**. IMAP alone is not a send protocol.

Docs in this folder:

- [android-app-plan.md](./android-app-plan.md): product, architecture, delivery phases, and technical scope.
- [ux-ui-spec.md](./ux-ui-spec.md): UX, UI, navigation, visual direction, interaction quality bar, and accessibility requirements.

Planning assumption for v1:

- The Android app should work against the **current** `claude-email` service with minimal server changes.
- That means the first usable version should support the current email model now, then add the stronger encrypted-envelope flow later.
- The long-term UX requires **conversation sessions** separate from raw agent identity; the current backend does not model that clearly yet.
