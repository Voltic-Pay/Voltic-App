# Contributing to Voltic

First off — thanks for even considering it. Voltic is a solo-built, early-alpha,
self-custodial crypto wallet, so contributions genuinely matter here. This doc
covers how to get set up, what's safe to touch, and what needs the most help.

> **Current status:** testnet-only (Arbitrum Sepolia). Spending limits / vault
> contract are known unstable — see [Areas That Need Help](#areas-that-need-help).

---

## Before You Do Anything: Security First

This is a **wallet**. It touches private keys, seed phrases, and (eventually)
real money. That changes the rules a bit:

- **Never commit secrets.** No API keys, no test seed phrases with real funds,
  no `local.properties`.
- **Found a security issue** (key leakage, signature bugs, replay attacks,
  anything that could let someone steal funds)? **Do not open a public issue.**
  Email/DM me privately first so it can be fixed before it's public knowledge.
  Regular bugs (UI glitches, crashes, bad copy) are fine as normal GitHub issues.
- If you're touching `WalletManager`, `ArbitrumClient` signing functions, the
  HCE service (`VolticHceService`), or anything under `chain/` or
  `transport/nfc/` — assume it's security-sensitive and be extra careful with
  what you change and why.

---

## Getting Set Up

1. **Prerequisites**
   - Android Studio 
   - JDK 17+
   - An NFC-capable Android devices if you're testing tap-to-pay 
   
2. **Clone & configure**
   ```bash
   git clone https://github.com/voltic-pay/voltic.git
   cd voltic
   ```
   Add a `local.properties` with your own Arbiscan key (get one free at
   arbiscan.io) if you want transaction history to work locally:
   ```properties
   ARBISCAN_API_KEY=YOUR_KEY_HERE
   ```

3. **Build**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Testnet funds** — see the README's "Getting Started" section for the
   faucet + bridge steps. You'll need Arbitrum Sepolia ETH to test anything
   that touches chain state.


---

## Making a Pull Request

1. Fork, branch off `main` (`feature/short-description` or `fix/short-description`)
2. Keep PRs focused — one fix/feature per PR, not a grab-bag
3. Test on a real device if the change touches NFC, camera/QR scanning, or
   biometrics — these don't behave the same (or at all) in emulators
4. Describe **what** changed and **why** in the PR description; screenshots or
   a short screen recording are hugely appreciated for UI changes
5. If it touches signing, the vault contract interaction, or transaction
   building — say so explicitly in the PR so it gets extra scrutiny

---

## Reporting Bugs

Open a GitHub issue with:
- What you did, what you expected, what happened instead
- exact steps to reproduce 
- Device + Android version
- Logs if you have them (you can use Logcat note that not everything in the app have logging as it was build on hurry)

---

## Questions

If something in the codebase doesn't make sense, you're not sure whether an
idea fits the project OR you have a radical changes open an issue to discuss before a PR
to save your and my time, especially given this is still solo-maintained.

