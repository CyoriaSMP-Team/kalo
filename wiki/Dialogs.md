# 💬 Dialogs

Dialogs are server-side modal menus with a title, body and clickable buttons. One
definition produces a Java data-pack dialog and a Bedrock form.

## Basic Dialog

```yaml
welcome:
  type: dialog
  title: "Welcome"
  body:
    - "Choose an option"
    - "Pick a place to start"
  columns: 2
  after: seamless
  can-close-with-escape: true
  buttons:
    - label: Spawn
      command: spawn
      tooltip: Back to the middle
      width: 150
    - label: Shop
      console:
        - "warp shop {player}"
        - "give {player} bread"
    - label: Website
      url: "https://example.com"
    - label: Close
```

## Options

| Option | Type | Description |
|--------|------|-------------|
| `title` | string | Dialog title, 1–120 characters |
| `body` | string or list | Plain message body, up to 4096 characters; a list becomes lines |
| `columns` | int | Button columns, 1–4 (default `1`) |
| `can-close-with-escape` | bool | Whether Escape closes the dialog (default `true`) |
| `after` | string | `seamless` (default), `close`, `none`, `wait_for_response` |
| `buttons` | list | 1–12 buttons |

Each button has:

| Option | Type | Description |
|--------|------|-------------|
| `label` | string | Button text, 1–120 characters |
| `tooltip` | string | Optional hover tooltip, up to 256 characters |
| `width` | int | Button width, 1–1024 (default `150`) |
| `command` | string or list | Run as the player; up to 16 commands |
| `console` | string or list | Run as the console; up to 16 commands |
| `url` | string | HTTPS URL |
| `copy` | string | Copy text to the clipboard |
| `suggest` | string | Put a command in the player's chat box |
| `dialog` | string | Open another dialog, e.g. `mypack:next` |
| `type` / `value` | string | Legacy single-action form |

A button has **one action kind**. Use a list under `command:` or `console:` for several
commands; they run in order and stop at the first failure. A button with no action closes
the dialog.

Commands run with the player's own permissions. Console commands run with console
permissions. Kalo never places a console command or a command list in the client payload.

## Click Keys

A command button is sent to the client as `/kalo click <key>`, not as its command. The
key is derived from the dialog, the button position and every command on the button. The
server accepts it only while that exact key belongs to the dialog the player was last
shown. Editing the definition changes the key, so an open dialog cannot run a command
from a newer definition.

The key route is what makes console buttons and multi-command buttons safe: typing
`/kalo click <key>` is not enough by itself, and `/kalo click` has no permission gate
because the shown-dialog check is the gate.

## After Modes

| Value | Behaviour |
|-------|-----------|
| `seamless` | Keep the dialog up until the click is handled, then close unless the click opened another dialog |
| `close` | Close immediately when the button is pressed |
| `none` | Stay open; useful for page links and dialogs that update in place |
| `wait_for_response` | Show the waiting screen until Kalo answers the click |

On Bedrock, `none` re-sends the form after the click; the other modes are best-effort
because a Bedrock form closes when a button is pressed.

## Raw Java JSON

Anything the YAML keys cannot express — input controls, notice/confirmation/dialog-list
types, item bodies — can be supplied as the Java `minecraft:dialog` object:

```yaml
shop:
  type: dialog
  json: dialogs/shop.json
  console-commands:
    "abc123": "warp shop {player}"
```

The file is passed through unchanged to the Java data pack. It must be one JSON object and
at most 256 KiB. With `json:`, the file controls the Java dialog; YAML `after`, `columns`
and `can-close-with-escape` are not applied to it. For Bedrock, Kalo reads the title,
body, `actions` and action types to build the closest form; unsupported Java-only
elements are omitted.

`console-commands` authorizes console commands for raw JSON that sends
`/kalo click <key>`. Keys are 1–32 lower-case letters or digits. An action in the raw JSON
may still use `open_url`, `copy_to_clipboard`, `suggest_command` or `show_dialog`; the
runtime handles those natively on Java and best-effort on Bedrock.

## Commands

```bash
/kalo dialog testpack:welcome
/kalo dialog testpack:welcome Steve
/kalo dialog open Alex testpack:welcome Steve
```

The optional last value is `{target}`; without it, `{target}` is the player the dialog is
shown to.

Permission: `kalo.command.dialog`. A `session-content.permissions` entry for the dialog
key also gates it. `open` checks the target player's entitlement.

`/kalo click <key>` is the internal click route used by generated dialogs. It is available
to all players but does nothing unless the key belongs to the last Kalo dialog that player
was shown.

## Java

Kalo writes an owned data pack to:

```
<world>/datapacks/kalo-dialogs/
├── pack.mcmeta
└── data/<pack>/dialog/<name>.json
```

Java **1.21.6 or newer** is required for the dialog registry. After the first install or
a content change, run `/reload` or restart so the server reads the new registry entry.
Removing a dialog deletes only Kalo's owned file; no other data pack is touched.

`/kalo dialog` uses vanilla `/dialog show` with the player UUID as the target, so the
command works even when a Bedrock gamertag contains spaces.

When a dialog contains `{player}`, `{target}`, `{uuid}` or `{world}` in its title, body,
button labels or tooltips, Kalo resolves those values at open time and sends the dialog
inline instead of using the static data-pack id. The generated data pack remains the
fallback and keeps the unresolved text. An inline payload larger than 30,000 characters
falls back to the static id.

## Bedrock

Bedrock has no dialog registry. When `bedrock-forms.enabled: true` and Floodgate or
Geyser shares the server, Kalo sends the same title, body and buttons as a Cumulus form.
Tooltips are appended to the button text. Command and console buttons use the same stored
definition and after-mode handling as Java; URL buttons send a clickable link in chat.

If no Bedrock forms transport is available, the command falls back to the Java dialog
path and reports failure rather than silently doing nothing.

## Limits

- YAML titles and bodies are plain text. Raw JSON can carry rich Java components.
- Open-time placeholders apply when Kalo opens the dialog. Another plugin opening the
  registered data-pack id directly sees the unresolved static text.
- An inline placeholder payload larger than 30,000 characters falls back to the static
  data-pack id.
- `wait_for_response` assumes every click reaches Kalo; a client-only URL click does not.
- The Bedrock form transport is optional and build-tested; live client/form delivery is
  not verified in CI.
