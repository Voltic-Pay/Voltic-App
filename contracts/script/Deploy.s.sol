// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Script, console2} from "forge-std/Script.sol";
import {VolticSmartWallet} from "../src/VolticSmartWallet.sol";

contract Deploy is Script {
    function run() public {
        // Load the private key from your .env file
        uint256 deployerPrivateKey = vm.envUint("PRIVATE_KEY");

        // Start broadcasting transactions to the real network
        vm.startBroadcast(deployerPrivateKey);

        // Deploy the contract!
        VolticSmartWallet wallet = new VolticSmartWallet();

        // Stop broadcasting
        vm.stopBroadcast();

        // Log the address so you can copy-paste it into your Android app!
        console2.log("VolticSmartWallet deployed at:", address(wallet));
    }
}
