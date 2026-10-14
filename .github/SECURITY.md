# Security policy

FwKnop-ng handles secrets (Rijndael and HMAC keys, GPG key selection) and builds packets meant to open firewall ports. Security reports are taken seriously.

## Supported versions

Only the [latest release](https://github.com/gildas-le-drogoff/FwKnop-ng/releases/latest) receives security fixes.

## Reporting a vulnerability

**Do not open a public issue.** Report it privately through [GitHub security advisories](https://github.com/gildas-le-drogoff/FwKnop-ng/security/advisories/new).

Please include:

- the affected version and Android version;
- a description of the issue and its impact;
- steps to reproduce or a proof of concept;
- if relevant, the SPA configuration involved (encryption, HMAC digest, transport) — **never your real keys**.

You will get an acknowledgement as soon as possible. Once a fix is released, the advisory is published and you are credited, unless you prefer to stay anonymous.

## Scope

In scope:

- SPA packet construction, encryption and HMAC (e.g. a packet accepted by `fwknopd` that should not be, replay, downgrade);
- storage of configurations and keys (Android Keystore, Tink, SQLCipher), app lock;
- encrypted backup/restore, QR code import/export, NFC triggers;
- leaks of keys or configurations (logs, intents, clipboard, backups, other apps);
- network requests other than those listed in the [README](../README.md#privacy).

Out of scope:

- vulnerabilities in the `fwknopd` server or the SPA protocol itself — report them [upstream](https://github.com/mrash/fwknop);
- attacks requiring an unlocked, rooted or already compromised device;
- weak keys chosen by the user.
