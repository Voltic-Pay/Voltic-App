# Voltic ⚡

> [!CAUTION]
> **TESTNET ONLY:** Voltic is currently available on **testnet only** (Arbitrum Sepolia) for testing and development purposes. Do NOT send real ETH or assets to this wallet. Real-net support is coming very soon! YOU can test on main net now by going to app/src/main/java/com/voltic/app/chain/ArbitrumClient.kt and changing the rpc and chain ID to real ones
> **Spending Limits are unstable** due to time constrains I wasn't able to get the smart contract to work as it should be .expected not using it unless you want to test and help 
## What is Voltic?

Voltic is a modern, privacy-first P2P self custodial payment application designed to make crypto as easy as a credit card tap, without the middleman. It leverages NFC and QR technology to enable instant, seamless transfers directly between users.

## Why Voltic?

*   **True P2P Privacy:** Conduct your payments directly. No big companies spying on your spending habits.
*   **Zero Middleman Fees:** Stop losing 2% of every transaction to massive payment processors. Keep your money where it belongs.
*   **KYC-Free:** We believe in financial privacy. No intrusive "Know Your Customer" procedures required.
*   **Tap-to-Pay Simplicity:** The power of the blockchain with the convenience of a modern wallet. Just tap your phone against a merchant or scan a QR code.
*   **Focused Usability:** Unlike other wallets that clutter the UI with 100 coins across 10 different networks, Voltic chooses one robust network to ensure the best possible user experience.
*   **full self custody** No one can control your accout but YOU ,Send anywhere anytime you want to Anyone around the world .

## Features

*   **Dual-Payment Modes:** Use **NFC** for that familiar tap-to-pay feel or **QR Codes** for universal compatibility.
*   **Hybrid Offline NFC:** Voltic can initiate NFC payments even while the sender is offline (as long as you are sure you have the funds). The sender signs the transaction locally, and the merchant broadcasts it to the network!
*   **Bank-Grade UI:** Bold, expressive Material 3 design that feels as professional as a high-end banking app.
*   **Secure by Design:** Biometric protection for sensitive actions like backing up your seed phrase.

## Future Trajectory 🚀

We are just getting started. Our roadmap includes:

1.  **Enhanced Device Integration:** Better account management and multi-device synchronization
2.  **Wearable Hardware:** We are actively working on porting the core Voltic logic to small **ESP32-C3** microcontrollers. With a tiny battery, Voltic could fit into a ring, a watch, or any wearable form factor.
3.  **Per Device spending limits based**  on ERC-4773 with One smart account with multiple authorized keys

---

## Getting Started


### for testnets
get to releases download apk it will inform you that it could be unsafe click install anyway
create a wallet save the seed phrase 
go to https://github.com/Voltic-Pay/Voltic-App/edit or other etherum faucent networks get some eth set you account public adresss -copy it from app dashboard-
go to meta mask install it click resote a wallet put the seed phrase
go to https://portal.arbitrum.io/bridge connect your metamask then bride to eth arbitrum 
**this is only for testnet**

### for real payments with real moeny
you can use any swap sites like uni swap choose eth arbitrum choose it set you public address and pay 
then you can open the app and you will see your funds 
**until now this app is only on testnet for testing until it achieve a rock solid stability then i will make a real network release**
**as above you can test that on your own** -don't do it if you are not a developer-

### Prerequisites

*   Android Studio Ladybug (or newer)
*   JDK 17+
*   An Android device with NFC support (for Tap-to-Pay)

### Building from Source

1.  **Clone the Repository**
    ```bash
    git clone https://github.com/voltic-pay/voltic.git
    cd voltic
    ```

2.  **Configure Environment**
    Create a `local.properties` file in the root directory and add your Arbiscan API key if you want to see transaction history:
    ```properties
    ARBISCAN_API_KEY=YOUR_KEY_HERE
    ```

3.  **Build the Project**
    Open the project in Android Studio or run via terminal:
    ```bash
    ./gradlew assembleDebug
    ```

4.  **Find your APK**
    Once the build finishes, you can find the debug APK at:
    `app/build/outputs/apk/debug/app-debug.apk`

---
5. **(optional) Deply your own contract)
    If you want to see how to deploy you own custom contract ,see the README.md in /contracts 


