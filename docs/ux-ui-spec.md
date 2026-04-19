# Claude Email Android UX/UI Spec

Status: draft  
Last updated: 2026-04-19

## 1. UX Positioning

This app should look and feel like a focused mobile tool for command-and-reply work with `claude-email`.

Design target:

- calmer than Slack
- more premium than a utility app
- more readable than Outlook
- more human than a terminal

The user should feel:

- in control
- fast
- informed
- not overloaded by raw email detail

## 2. Core UX Principles

### Make email invisible where possible

The user should think in:

- conversations
- commands
- replies
- agent messages
- tasks or workstreams

The user should not have to think in:

- IMAP folders
- SMTP transactions
- raw headers
- MIME structure

### Optimize for scanning first, deep reading second

Command and reply work is often:

- short and urgent
- output-heavy
- interruption-driven

So the UI must make it easy to:

- see what changed
- see which thread needs attention
- reopen context quickly

### Treat reliability as part of UX

If a send failed, that is a UX event.  
If sync is stale, that is a UX event.  
If account auth broke, that is a UX event.

These must be visible, plain, and actionable.

### Preserve focus

The app should minimize:

- setup confusion
- navigation depth
- decorative noise
- unnecessary options
- identity confusion between agents, tasks, and conversations

## 3. Information Architecture

Recommended nav structure:

- `Home`
- `Conversation`
- `Settings`
- `Diagnostics`

Avoid bottom navigation with many tabs. The app does not have enough equal-weight destinations to justify it.

Recommended structure:

- one main feed
- one deep conversation surface
- settings and diagnostics behind top-right access

Foundational rule:

- the UI must clearly separate `agent`, `task`, and `conversation`
- the same agent may appear in multiple conversations without those conversations visually collapsing together

## 4. Primary Screen Specs

### 4.1 Welcome / Setup

Purpose:

- explain what the app is
- set the expectation of one account only
- collect configuration with minimal stress

Must include:

- strong title
- short plain-language explanation
- setup progress indicator
- connection test step
- success state before entering the main app

Recommended steps:

1. Mail account
2. IMAP/SMTP details
3. `claude-email` service address
4. Security mode
5. Test connection

UX requirements:

- each step validates inline
- errors point to the exact field involved
- the user never loses already-entered values on failure

### 4.2 Home

Purpose:

- give a fast picture of what needs attention
- act as the launch point for all conversations

Must show:

- account health
- sync freshness
- recent conversations
- which agent each conversation belongs to
- unread counts
- failed-send count if any

Layout direction:

- strong header
- compact status rail near top
- thread list as the main body
- persistent `New command` action

Thread row content:

- title
- agent badge
- latest message preview
- timestamp
- unread indicator
- state chip if sending/failed/waiting
- attachment indicator when relevant

Home should support fast separation of new versus old work:

- default sorting by recent activity
- clear grouping or filtering for `Active`, `Waiting`, and `Archived`
- obvious `New conversation` action

### 4.3 Conversation

Purpose:

- read command/reply exchanges comfortably
- reply quickly
- understand status without leaving the screen

Must support:

- long output
- copying text
- collapsed blocks for large replies
- timestamps on demand
- retry for failed sends
- file attachments
- clearly starting a fresh conversation from the same screen

Message presentation:

- user commands visually distinct from service replies
- command messages may use monospace accents
- system/status items should be smaller and quieter
- raw headers hidden behind a details action
- attachments should appear as intentional cards with type icon, name, size, and action

Composer requirements:

- large touch target
- multiline input
- clear primary send button
- visible sending state
- disabled send only when truly invalid
- visible attachment tray when files are selected
- explicit control for `New conversation` versus `Reply in this conversation` when needed

### 4.4 Settings

Purpose:

- make the single-account model explicit
- expose account, sync, and security configuration

Must contain:

- mailbox identity
- IMAP settings
- SMTP settings
- `claude-email` address
- security mode
- sync frequency
- notification settings
- reset account action

### 4.5 Diagnostics

Purpose:

- reduce support friction
- help debug mail/protocol problems without exposing the user to raw internals by default

Must show:

- last successful sync time
- last send attempt result
- current protocol mode
- current mailbox hostnames
- sanitized technical log snippets

## 5. Agent / Task / Conversation Identity

This is one of the most important UX problems in the app.

Rules:

- an agent name alone is never enough as a conversation label
- every conversation should have a visible title
- the title can be generated initially, but the user should be able to rename it
- the agent should appear as a badge or secondary label
- old conversations should be archivable without making the agent disappear from the system

Recommended conversation header pattern:

- main line: task or conversation title
- secondary line: agent name, relative time, and state

Bad example:

- `agent-fits`

Good example:

- `Refactor auth retry flow`
- `agent-fits • waiting for reply • last active yesterday`

## 6. Visual Direction

Recommended visual language:

- precise
- editorial
- restrained
- warm-tech rather than cold enterprise

### Typography

Use a deliberate font stack, not default Android styling.

Recommended direction:

- headings: `Space Grotesk`
- body: `IBM Plex Sans`
- command/output accents: `IBM Plex Mono`

Why:

- the headings feel specific and modern
- body text stays readable at dense sizes
- monospace accents reinforce command intent without making the whole app look like a terminal

### Color

Avoid default white-plus-purple Material styling.

Recommended palette direction:

- background: warm off-white or deep graphite
- foreground: near-black or soft white depending on theme
- accent: cyan or teal
- warning: amber
- error: brick red
- success: muted green

The app should feel trustworthy and calm, not playful.

### Shape

- medium corner radii
- cards that feel solid, not bubbly
- chips for state
- clear separation between content blocks

### Motion

Use motion sparingly and purposefully:

- subtle thread-list entrance stagger
- smooth composer resize
- message send state transition
- soft pull-to-refresh feedback

Avoid busy micro-animations.

Messaging-app inspiration is appropriate, but only in structure and clarity.

Do not copy consumer-chat visuals too literally. This is still a work tool.

## 7. Interaction Rules

### Status visibility

Every important network or sync state must have a UI representation:

- sending
- sent
- failed
- syncing
- stale
- offline
- auth broken

Every important conversation state must also have a UI representation:

- active
- waiting on agent
- waiting on user
- archived
- failed send

### Large reply handling

Large outputs are expected.  
The UI should:

- show the first meaningful slice
- offer `Expand`
- support `Copy all`
- keep scroll performance stable

Attachments are expected eventually.  
The UI should:

- keep attachment cards compact in the message flow
- avoid giant previews by default
- show upload and download progress when relevant

### Refresh behavior

The user should always have a clear way to force refresh:

- pull to refresh on Home and Conversation
- visible last-sync timestamp
- immediate visual acknowledgment that refresh started

### Empty states

Empty states should feel intentional:

- no threads yet
- waiting for first reply
- account not configured
- offline mode

Also include:

- no active conversations
- no archived conversations
- no attachments in this conversation

Avoid generic "Nothing here" language.

## 8. Copy Tone

Copy should be:

- plain
- calm
- direct

Examples:

- `Checking IMAP connection`
- `SMTP login failed`
- `Waiting for reply`
- `Sent. Looking for a response.`
- `Account needs attention`
- `Starting a new conversation`
- `Attachment too large to send`
- `This file type is not supported yet`

Avoid:

- enterprise jargon
- vague sync language
- clever or jokey copy

## 9. Accessibility Requirements

- minimum 4.5:1 contrast for standard text
- dynamic type support
- 48dp minimum touch targets
- screen-reader labels for status chips and message actions
- motion should respect reduced-motion preferences where possible
- color must never be the only state signal

Accessibility is not a polish phase item. It is part of the first design pass.

## 10. UX Quality Bar

The app is not ready to build deeply until these are true in the first interactive prototype:

- setup can be completed without confusion
- the Home screen tells the user what matters in under 3 seconds
- a new command can be sent in one obvious flow
- a reply thread feels better than reading the same exchange in Outlook
- send, sync, and failure states are all understandable without developer knowledge
- a user can tell the difference between two conversations with the same agent instantly
- attachment actions are understandable without reading technical labels

## 11. Prototype Checklist

Before deep transport work, prototype these screens with realistic content:

- setup flow
- home thread list
- long-output conversation
- two conversations with the same agent on different days
- conversation with one or more attachments
- failed-send state
- stale-sync state

If those screens are weak, the app direction is weak, regardless of protocol quality.
