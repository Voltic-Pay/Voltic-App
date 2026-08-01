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
```
jq '.abi' out/VolticFactory.sol/VolticFactory.json > VolticFactory.abi

jq -r '.bytecode.object' out/VolticFactory.sol/VolticFactory.json | sed 's/^0x//' > VolticFactory.bin


web3j generate solidity \
  -a VolticFactory.abi \
  -b VolticFactory.bin \
  -o ../app/src/main/java \
  -p com.voltic.contracts


```
and same for the other file
```
jq '.abi' out/VolticWallet.sol/VolticWallet.json > VolticWallet.abi
jq -r '.bytecode.object' out/VolticWallet.sol/VolticWallet.json | sed 's/^0x//' > VolticWallet.bin

web3j generate solidity \
  -a VolticWallet.abi \
  -b VolticWallet.bin \
  -o ../app/src/main/java \
  -p com.voltic.contracts
```

this will produce a java files with it you can interact to the contract
- you need also to get edit /app/src/main/java/com/voltic/app/chain/ArbitrumClient.kt as set the RPC_URL and RPC and Chain ID contract adress
- some values are hard coded ... i will chang them later








