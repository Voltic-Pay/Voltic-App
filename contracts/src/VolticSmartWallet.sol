// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {EIP712} from "@openzeppelin/contracts/utils/cryptography/EIP712.sol";
import {ECDSA} from "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

/// @title VolticSmartWallet
/// @notice Single shared-vault contract acting as a secure "Checking Account".
///         Users deposit ETH here to safely use wearables/NFC with strict spending limits.
contract VolticSmartWallet is EIP712, ReentrancyGuard {
    using ECDSA for bytes32;

    enum LimitPeriod { Daily, Weekly, Monthly }

    struct SpendLimit {
        uint96 amount;          // max wei spendable per period, 0 = no limit
        uint96 spentInPeriod;   // fits tightly into 1 storage slot with amount and periodStart
        uint64 periodStart;     // timestamp the current period window began
    }

    mapping(address => uint256) public balanceOf;
    mapping(address => uint256) public nonces;
    mapping(address => bool) public isDisabled;
    mapping(address => LimitPeriod) public spendPeriod;
    mapping(address => SpendLimit) public spendLimits;

    // The maximum time in the future a signature can be valid for
    uint256 private constant MAX_DEADLINE_HORIZON = 30 days;

    bytes32 private constant PAYMENT_TYPEHASH = keccak256(
        "Payment(address owner,address to,uint256 amount,uint256 nonce,uint256 deadline)"
    );

    event Deposited(address indexed user, uint256 amount);
    event Withdrawn(address indexed user, uint256 amount);
    event PaymentExecuted(address indexed owner, address indexed to, uint256 amount, uint256 nonce);
    event KillSwitchSet(address indexed user, bool disabled);
    event SpendLimitSet(address indexed user, LimitPeriod period, uint96 amount);

    error WalletDisabled();
    error InvalidSignature();
    error ExpiredDeadline();
    error DeadlineTooFarInFuture();
    error NonceAlreadyUsed();
    error InsufficientBalance();
    error SpendLimitExceeded();
    error ZeroAddress();
    error InvalidToAddress();
    error ZeroAmount();

    constructor() EIP712("VolticSmartWallet", "1") {}

    function deposit() external payable {
        if (msg.value == 0) revert ZeroAmount();
        balanceOf[msg.sender] += msg.value;
        emit Deposited(msg.sender, msg.value);
    }

    receive() external payable {
        if (msg.value == 0) revert ZeroAmount();
        balanceOf[msg.sender] += msg.value;
        emit Deposited(msg.sender, msg.value);
    }

    function withdraw(uint256 amount) external nonReentrant {
        if (amount == 0) revert ZeroAmount();
        uint256 bal = balanceOf[msg.sender];
        if (bal < amount) revert InsufficientBalance();

        balanceOf[msg.sender] = bal - amount;
        (bool ok, ) = msg.sender.call{value: amount}("");
        require(ok, "withdraw transfer failed");

        emit Withdrawn(msg.sender, amount);
    }

    function setKillSwitch(bool disabled) external {
        isDisabled[msg.sender] = disabled;
        emit KillSwitchSet(msg.sender, disabled);
    }

    function setSpendLimit(LimitPeriod period, uint96 amount) external {
        spendPeriod[msg.sender] = period;
        spendLimits[msg.sender] = SpendLimit({
            amount: amount,
            spentInPeriod: 0,
            periodStart: uint64(block.timestamp)
        });
        emit SpendLimitSet(msg.sender, period, amount);
    }

    function executePayment(
        address owner,
        address to,
        uint256 amount,
        uint256 nonce,
        uint256 deadline,
        bytes calldata signature
    ) external nonReentrant {
        // --- GUARD RAILS ---
        if (owner == address(0) || to == address(0)) revert ZeroAddress();
        if (to == address(this) || to == owner) revert InvalidToAddress();
        if (amount == 0) revert ZeroAmount();

        // Time Guard Rails
        if (block.timestamp > deadline) revert ExpiredDeadline();
        if (deadline > block.timestamp + MAX_DEADLINE_HORIZON) revert DeadlineTooFarInFuture();

        // State Guard Rails
        if (isDisabled[owner]) revert WalletDisabled();
        if (nonce != nonces[owner]) revert NonceAlreadyUsed();

        // --- CRYPTOGRAPHY ---
        bytes32 structHash = keccak256(
            abi.encode(PAYMENT_TYPEHASH, owner, to, amount, nonce, deadline)
        );
        bytes32 digest = _hashTypedDataV4(structHash);

        // OpenZeppelin ECDSA protects against malleability issues automatically
        address recovered = ECDSA.recover(digest, signature);
        if (recovered != owner) revert InvalidSignature();

        // --- STATE UPDATES (CEI Pattern) ---
        nonces[owner] = nonce + 1;

        uint256 bal = balanceOf[owner];
        if (bal < amount) revert InsufficientBalance();

        _enforceAndUpdateSpendLimit(owner, amount);

        balanceOf[owner] = bal - amount;

        // --- EXTERNAL CALL ---
        (bool ok, ) = to.call{value: amount}("");
        require(ok, "payment transfer failed");

        emit PaymentExecuted(owner, to, amount, nonce);
    }

    function _periodSeconds(LimitPeriod period) internal pure returns (uint256) {
        if (period == LimitPeriod.Daily) return 1 days;
        if (period == LimitPeriod.Weekly) return 7 days;
        return 30 days;
    }

    function _enforceAndUpdateSpendLimit(address owner, uint256 amount) internal {
        SpendLimit memory lim = spendLimits[owner];
        if (lim.amount == 0) return;

        uint256 periodLen = _periodSeconds(spendPeriod[owner]);

        if (block.timestamp >= uint256(lim.periodStart) + periodLen) {
            lim.periodStart = uint64(block.timestamp);
            lim.spentInPeriod = 0;
        }

        uint256 newSpent = uint256(lim.spentInPeriod) + amount;
        if (newSpent > lim.amount) revert SpendLimitExceeded();

        // forge-lint: disable-next-line(unsafe-typecast)
        lim.spentInPeriod = uint96(newSpent);
        spendLimits[owner] = lim;
    }

    function domainSeparator() external view returns (bytes32) {
        return _domainSeparatorV4();
    }
}
