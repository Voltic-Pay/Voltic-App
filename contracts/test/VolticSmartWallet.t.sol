// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Test, console2} from "forge-std/Test.sol";
import {VolticSmartWallet} from "../src/VolticSmartWallet.sol";

contract VolticSmartWalletTest is Test {
    VolticSmartWallet public vault;

    uint256 alicePk = 0xA11CE;
    address alice = vm.addr(alicePk);
    address merchant = address(0xB0B);
    address intruder = address(0xBAD);

    bytes32 private constant PAYMENT_TYPEHASH = keccak256(
        "Payment(address owner,address to,uint256 amount,uint256 nonce,uint256 deadline)"
    );

    function setUp() public {
        vault = new VolticSmartWallet();
        // Alice funds her vault balance
        vm.deal(alice, 10 ether);
        vm.prank(alice);
        vault.deposit{value: 5 ether}();
    }

    // --- Basic Accounting Tests ---

    function test_Deposit() public {
        vm.deal(intruder, 1 ether);
        vm.prank(intruder);
        vault.deposit{value: 1 ether}();

        assertEq(vault.balanceOf(intruder), 1 ether);
    }

    function test_Withdraw() public {
        vm.prank(alice);
        vault.withdraw(1 ether);

        assertEq(vault.balanceOf(alice), 4 ether);
        assertEq(alice.balance, 6 ether);
    }

    function test_WithdrawFailsInsufficientBalance() public {
        vm.prank(alice);
        vm.expectRevert(VolticSmartWallet.InsufficientBalance.selector);
        vault.withdraw(10 ether);
    }

    // --- Security & Kill Switch Tests ---

    function test_KillSwitch() public {
        vm.prank(alice);
        vault.setKillSwitch(true);
        assertTrue(vault.isDisabled(alice));

        uint256 amount = 0.1 ether;
        uint256 nonce = vault.nonces(alice);
        uint256 deadline = block.timestamp + 1 hours;
        bytes memory sig = _signVaultPayment(alicePk, alice, merchant, amount, nonce, deadline);

        vm.expectRevert(VolticSmartWallet.WalletDisabled.selector);
        vault.executePayment(alice, merchant, amount, nonce, deadline, sig);
    }

    // --- Spend Limit Tests ---

    function test_DailyLimitEnforcement() public {
        // Alice sets a 1 ETH daily limit
        vm.prank(alice);
        vault.setSpendLimit(VolticSmartWallet.LimitPeriod.Daily, 1 ether);

        uint256 nonce = vault.nonces(alice);
        uint256 deadline = block.timestamp + 1 hours;

        // Payment 1: 0.6 ETH (Success)
        bytes memory sig1 = _signVaultPayment(alicePk, alice, merchant, 0.6 ether, nonce, deadline);
        vault.executePayment(alice, merchant, 0.6 ether, nonce, deadline, sig1);

        // Payment 2: 0.5 ETH (Fail: 0.6 + 0.5 > 1.0)
        nonce++;
        bytes memory sig2 = _signVaultPayment(alicePk, alice, merchant, 0.5 ether, nonce, deadline);
        vm.expectRevert(VolticSmartWallet.SpendLimitExceeded.selector);
        vault.executePayment(alice, merchant, 0.5 ether, nonce, deadline, sig2);

        vault.setSpendLimit(VolticSmartWallet.LimitPeriod.Daily, 1 ether);

        bytes memory sig3 = _signVaultPayment(alicePk, alice, merchant, 0.6 ether, nonce, deadline);
        vault.executePayment(alice, merchant, 0.6 ether, nonce, deadline, sig3);
    }

    function test_LimitResetsAfterTime() public {
        vm.prank(alice);
        vault.setSpendLimit(VolticSmartWallet.LimitPeriod.Daily, 1 ether);

        uint256 nonce = vault.nonces(alice);
        uint256 deadline = block.timestamp + 2 days;

        // Exhaust the limit
        bytes memory sig1 = _signVaultPayment(alicePk, alice, merchant, 1 ether, nonce, deadline);
        vault.executePayment(alice, merchant, 1 ether, nonce, deadline, sig1);

        // Travel 25 hours forward
        vm.warp(block.timestamp + 25 hours);

        // Should succeed now
        nonce++;
        bytes memory sig2 = _signVaultPayment(alicePk, alice, merchant, 0.5 ether, nonce, deadline);
        vault.executePayment(alice, merchant, 0.5 ether, nonce, deadline, sig2);

        assertEq(vault.balanceOf(alice), 3.5 ether);
    }

    // --- EIP-712 Meta-Transaction Tests ---

    function test_ExecutePaymentSuccess() public {
        uint256 amount = 2 ether;
        uint256 nonce = vault.nonces(alice);
        uint256 deadline = block.timestamp + 1 hours;

        bytes memory sig = _signVaultPayment(alicePk, alice, merchant, amount, nonce, deadline);

        uint256 initialMerchantBal = merchant.balance;

        // Merchant broadcasts the transaction (Merchant pays gas, Alice pays ETH)
        vm.prank(merchant);
        vault.executePayment(alice, merchant, amount, nonce, deadline, sig);

        assertEq(merchant.balance, initialMerchantBal + amount);
        assertEq(vault.balanceOf(alice), 3 ether);
        assertEq(vault.nonces(alice), nonce + 1);
    }

    function test_RevertInvalidSignature() public {
        uint256 amount = 1 ether;
        uint256 nonce = vault.nonces(alice);
        uint256 deadline = block.timestamp + 1 hours;

        // Intruder signs the message for Alice's account
        uint256 intruderPk = 0xBAD1;
        bytes memory badSig = _signVaultPayment(intruderPk, alice, merchant, amount, nonce, deadline);

        vm.expectRevert(VolticSmartWallet.InvalidSignature.selector);
        vault.executePayment(alice, merchant, amount, nonce, deadline, badSig);
    }

    function test_RevertExpiredDeadline() public {
        uint256 amount = 1 ether;
        uint256 nonce = vault.nonces(alice);
        uint256 deadline = block.timestamp - 1; // Already expired

        bytes memory sig = _signVaultPayment(alicePk, alice, merchant, amount, nonce, deadline);

        vm.expectRevert(VolticSmartWallet.ExpiredDeadline.selector);
        vault.executePayment(alice, merchant, amount, nonce, deadline, sig);
    }

    // --- Internal EIP-712 Helper ---

    function _signVaultPayment(
        uint256 privateKey,
        address owner,
        address to,
        uint256 amount,
        uint256 nonce,
        uint256 deadline
    ) internal view returns (bytes memory) {
        bytes32 structHash = keccak256(
            abi.encode(PAYMENT_TYPEHASH, owner, to, amount, nonce, deadline)
        );

        bytes32 digest = keccak256(
            abi.encodePacked("\x19\x01", vault.domainSeparator(), structHash)
        );

        (uint8 v, bytes32 r, bytes32 s) = vm.sign(privateKey, digest);
        return abi.encodePacked(r, s, v);
    }
}
