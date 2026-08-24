to deploy you own contract you should fill these  
```

MNEMONIC="you 23 words" 
RPC_URL=rpc
ARBISCAN_API_KEY=api key you can use a free one

```
then you will do 
```
forge install OpenZeppelin/openzeppelin-contracts
forge build
forge test
```

then 
```

forge script script/Deploy.s.sol -r https://sepolia-rollup.arbitrum.io/rpc 


```
if you are ready to deoply to a real chain add `--broadcast`
then you will generate the wraper interface for java:
```
jq '.abi' out/VolticSmartWallet.sol/VolticSmartWallet.json > VolticSmartWallet.abi
jq -r '.bytecode.object' out/VolticSmartWallet.sol/VolticSmartWallet.json | sed 's/^0x//' > VolticSmartWallet.bin

web3j generate solidity \
  -a VolticSmartWallet.abi \
  -b VolticSmartWallet.bin \
  -o ../app/src/main/java \
  -p com.voltic.contracts

```

this will produce a java files with it you can interact to the contract
- you need also to get edit /app/src/main/java/com/voltic/app/chain/ArbitrumClient.kt as set the RPC_URL and RPC and Chain ID contract adress
- some values are hard coded ... i will chang them later








