package com.voltic.app.chain

import com.voltic.app.BuildConfig

sealed class ChainConfig(
    val chainId: Long,
    val chainName: String,
    val explorerUrl: String,
    val vaultAddress: String,
    val arbitrumRpcs: List<String>,
    val ethereumRpcs: List<String>
) {
    object Sepolia : ChainConfig(
        chainId = 421614L,
        chainName = "Arbitrum Sepolia",
        explorerUrl = "https://sepolia.arbiscan.io",
        vaultAddress = "0x2EB9cD3C24C7cA7F7Eb7e563Be14C7Dd60504B6e",
        arbitrumRpcs = listOf(
            "https://sepolia-rollup.arbitrum.io/rpc",
            "https://arbitrum-sepolia.drpc.org",
            "https://arbitrum-sepolia-rpc.publicnode.com"
        ),
        ethereumRpcs = listOf(
            "https://rpc.ankr.com/eth",
            "https://ethereum-rpc.publicnode.com",
            "https://cloudflare-eth.com"
        )
    )

    object Mainnet : ChainConfig(
        chainId = 42161L,
        chainName = "Arbitrum One",
        explorerUrl = "https://arbiscan.io",
        vaultAddress = "0xb84b1abe962534917e9f5f7945315f309cd36fa4",
        arbitrumRpcs = listOf(
            "https://arb1.arbitrum.io/rpc",
            "https://arbitrum-one-rpc.publicnode.com",
            "https://public.1rpc.io/arb",
            "https://arb1.lava.build"
        ),
        ethereumRpcs = listOf(
            "https://rpc.ankr.com/eth",
            "https://ethereum-rpc.publicnode.com",
            "https://cloudflare-eth.com"
        )
    )

    companion object {
        val current: ChainConfig
            get() = if (BuildConfig.FLAVOR == "sepolia") Sepolia else Mainnet
    }
}
