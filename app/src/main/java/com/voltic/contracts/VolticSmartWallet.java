package com.voltic.contracts;

import io.reactivex.Flowable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.CustomError;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes1;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.abi.datatypes.generated.Uint96;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.BaseEventResponse;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple3;
import org.web3j.tuples.generated.Tuple7;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the 
 * <a href="https://github.com/LFDT-web3j/web3j/tree/main/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 1.8.0.
 */
@SuppressWarnings("rawtypes")
@Generated("org.web3j.codegen.SolidityFunctionWrapperGenerator")
public class VolticSmartWallet extends Contract {
    public static final String BINARY = "61016060405234801562000011575f80fd5b5060405180604001604052806011815260200170159bdb1d1a58d4db585c9d15d85b1b195d607a1b815250604051806040016040528060018152602001603160f81b81525062000067826200013660201b60201c565b61012052620000768162000136565b61014052815160208084019190912060e052815190820120610100524660a0526200010360e05161010051604080517f8b73c3c69bb8fe3d512ecc4cf759cc79239f7b179b0ffacaa9a75d522b39400f60208201529081019290925260608201524660808201523060a08201525f9060c00160405160208183030381529060405280519060200120905090565b60805250503060c05260017f9b779b17422d0df92223018b32b4d1fa46e071723d6817e2486d003becc55f0055620001f6565b5f80829050601f815111156200016c578260405163305a27a960e01b815260040162000163919062000181565b60405180910390fd5b80516200017982620001cf565b179392505050565b5f602080835283518060208501525f5b81811015620001af5785810183015185820160400152820162000191565b505f604082860101526040601f19601f8301168501019250505092915050565b80516020808301519190811015620001f0575f198160200360031b1b821691505b50919050565b60805160a05160c05160e0516101005161012051610140516113f5620002485f395f610cc801525f610c9d01525f610dc501525f610d9d01525f610cf801525f610d2201525f610d4c01526113f55ff3fe6080604052600436106100a8575f3560e01c80637ecebe00116100625780637ecebe001461028957806384b0196e146102b45780639213bda7146102db578063d0e30db0146102fa578063f56cc47814610302578063f698da251461033d575f80fd5b80631b6f49781461012b5780632e1a7d4d1461014c5780634f6c171d1461016b5780635c6c44de146101ae57806370a08231146101cd578063741a679714610206575f80fd5b3661012757345f036100cd57604051631f2a200560e01b815260040160405180910390fd5b335f90815260026020526040812080543492906100eb9084906110db565b909155505060405134815233907f2da466a7b24304f47e87fa2e1e5a81b9831ce54fec19055ce277ca2f39ba42c49060200160405180910390a2005b5f80fd5b348015610136575f80fd5b5061014a610145366004611109565b610351565b005b348015610157575f80fd5b5061014a6101663660046111b1565b61073c565b348015610176575f80fd5b506101996101853660046111c8565b60046020525f908152604090205460ff1681565b60405190151581526020015b60405180910390f35b3480156101b9575f80fd5b5061014a6101c83660046111e8565b610891565b3480156101d8575f80fd5b506101f86101e73660046111c8565b60026020525f908152604090205481565b6040519081526020016101a5565b348015610211575f80fd5b506102596102203660046111c8565b60066020525f90815260409020546001600160601b0380821691600160601b810490911690600160c01b900467ffffffffffffffff1683565b604080516001600160601b03948516815293909216602084015267ffffffffffffffff16908201526060016101a5565b348015610294575f80fd5b506101f86102a33660046111c8565b60036020525f908152604090205481565b3480156102bf575f80fd5b506102c861097c565b6040516101a59796959493929190611270565b3480156102e6575f80fd5b5061014a6102f5366004611307565b6109be565b61014a610a12565b34801561030d575f80fd5b5061033061031c3660046111c8565b60056020525f908152604090205460ff1681565b6040516101a5919061135a565b348015610348575f80fd5b506101f8610a8c565b610359610a9a565b6001600160a01b038716158061037657506001600160a01b038616155b156103945760405163d92e233d60e01b815260040160405180910390fd5b6001600160a01b0386163014806103bc5750866001600160a01b0316866001600160a01b0316145b156103da57604051638aa3a72f60e01b815260040160405180910390fd5b845f036103fa57604051631f2a200560e01b815260040160405180910390fd5b8242111561041b5760405163f87d927160e01b815260040160405180910390fd5b61042862278d00426110db565b83111561044857604051633a9eba7960e21b815260040160405180910390fd5b6001600160a01b0387165f9081526004602052604090205460ff16156104815760405163f5726c7160e01b815260040160405180910390fd5b6001600160a01b0387165f9081526003602052604090205484146104b757604051623f613760e71b815260040160405180910390fd5b604080517f5c1bf0c7836a670d8a6dfe6e044205a2a77d2aaf1c8b0b7b81e9f46b0f7d8c306020808301919091526001600160a01b038a811683850152891660608301526080820188905260a0820187905260c08083018790528351808403909101815260e090920190925280519101205f61053282610ab5565b90505f6105748286868080601f0160208091040260200160405190810160405280939291908181526020018383808284375f92019190915250610ae792505050565b9050896001600160a01b0316816001600160a01b0316146105a857604051638baa579f60e01b815260040160405180910390fd5b6105b38760016110db565b6001600160a01b038b165f90815260036020908152604080832093909355600290522054888110156105f857604051631e9acf1760e31b815260040160405180910390fd5b6106028b8a610b0f565b61060c8982611368565b6001600160a01b038c81165f90815260026020526040808220939093559151908c16908b908381818185875af1925050503d805f8114610667576040519150601f19603f3d011682016040523d82523d5f602084013e61066c565b606091505b50509050806106c25760405162461bcd60e51b815260206004820152601760248201527f7061796d656e74207472616e73666572206661696c656400000000000000000060448201526064015b60405180910390fd5b8a6001600160a01b03168c6001600160a01b03167ff9526c97e462282f793fa26260fdbb98fb81047df50b1249179bfffae687094a8c8c604051610710929190918252602082015260400190565b60405180910390a3505050505061073360015f805160206113a083398151915255565b50505050505050565b610744610a9a565b805f0361076457604051631f2a200560e01b815260040160405180910390fd5b335f908152600260205260409020548181101561079457604051631e9acf1760e31b815260040160405180910390fd5b61079e8282611368565b335f8181526002602052604080822093909355915184908381818185875af1925050503d805f81146107eb576040519150601f19603f3d011682016040523d82523d5f602084013e6107f0565b606091505b50509050806108415760405162461bcd60e51b815260206004820152601860248201527f7769746864726177207472616e73666572206661696c6564000000000000000060448201526064016106b9565b60405183815233907f7084f5476618d8e60b11ef0d7d3f06914655adb8793e28ff7f018d4c76d505d59060200160405180910390a2505061088e60015f805160206113a083398151915255565b50565b335f908152600560205260409020805483919060ff191660018360028111156108bc576108bc611326565b0217905550604080516060810182526001600160601b0380841682525f602080840182815267ffffffffffffffff428116868801908152338086526006909452938790209551865492519451909116600160c01b026001600160c01b03948616600160601b026001600160c01b0319909316919095161717919091169190911790915590517fee25b1ed623a3a7776bd95fc2ae92f6bb0c4ffc4604d36e90f234278adb6c36e90610970908590859061137b565b60405180910390a25050565b5f6060805f805f606061098d610c96565b610995610cc1565b604080515f80825260208201909252600f60f81b9b939a50919850469750309650945092509050565b335f81815260046020908152604091829020805460ff191685151590811790915591519182527f343c5e14ab2197d8084e1c9aa183a93a66ff3f887132bbbb25d04af80047ece8910160405180910390a250565b345f03610a3257604051631f2a200560e01b815260040160405180910390fd5b335f9081526002602052604081208054349290610a509084906110db565b909155505060405134815233907f2da466a7b24304f47e87fa2e1e5a81b9831ce54fec19055ce277ca2f39ba42c49060200160405180910390a2565b5f610a95610cec565b905090565b610aa2610e15565b60025f805160206113a083398151915255565b5f610ae1610ac1610cec565b8360405161190160f01b8152600281019290925260228201526042902090565b92915050565b5f805f80610af58686610e46565b925092509250610b058282610e8f565b5090949350505050565b6001600160a01b0382165f908152600660209081526040808320815160608101835290546001600160601b03808216808452600160601b830490911694830194909452600160c01b900467ffffffffffffffff16918101919091529103610b7557505050565b6001600160a01b0383165f90815260056020526040812054610b999060ff16610f4b565b905080826040015167ffffffffffffffff16610bb591906110db565b4210610bd25767ffffffffffffffff421660408301525f60208301525b5f8383602001516001600160601b0316610bec91906110db565b83519091506001600160601b0316811115610c1a57604051639bd0c54560e01b815260040160405180910390fd5b6001600160601b0390811660208085019182526001600160a01b039096165f9081526006909652604095869020845181549251979095015167ffffffffffffffff16600160c01b026001600160c01b03978416600160601b026001600160c01b0319909316959093169490941717949094169390931790555050565b6060610a957f0000000000000000000000000000000000000000000000000000000000000000610f9b565b6060610a957f0000000000000000000000000000000000000000000000000000000000000000610f9b565b5f306001600160a01b037f000000000000000000000000000000000000000000000000000000000000000016148015610d4457507f000000000000000000000000000000000000000000000000000000000000000046145b15610d6e57507f000000000000000000000000000000000000000000000000000000000000000090565b610a95604080517f8b73c3c69bb8fe3d512ecc4cf759cc79239f7b179b0ffacaa9a75d522b39400f60208201527f0000000000000000000000000000000000000000000000000000000000000000918101919091527f000000000000000000000000000000000000000000000000000000000000000060608201524660808201523060a08201525f9060c00160405160208183030381529060405280519060200120905090565b5f805160206113a083398151915254600203610e4457604051633ee5aeb560e01b815260040160405180910390fd5b565b5f805f8351604103610e7d576020840151604085015160608601515f1a610e6f88828585610fd8565b955095509550505050610e88565b505081515f91506002905b9250925092565b5f826003811115610ea257610ea2611326565b03610eab575050565b6001826003811115610ebf57610ebf611326565b03610edd5760405163f645eedf60e01b815260040160405180910390fd5b6002826003811115610ef157610ef1611326565b03610f125760405163fce698f760e01b8152600481018290526024016106b9565b6003826003811115610f2657610f26611326565b03610f47576040516335e2f38360e21b8152600481018290526024016106b9565b5050565b5f80826002811115610f5f57610f5f611326565b03610f6e575062015180919050565b6001826002811115610f8257610f82611326565b03610f91575062093a80919050565b5062278d00919050565b60605f610fa7836110a0565b6040805160208082528183019092529192505f91906020820181803683375050509182525060208101929092525090565b5f80807f7fffffffffffffffffffffffffffffff5d576e7357a4501ddfe92f46681b20a084111561101157505f91506003905082611096565b604080515f808252602082018084528a905260ff891692820192909252606081018790526080810186905260019060a0016020604051602081039080840390855afa158015611062573d5f803e3d5ffd5b5050604051601f1901519150506001600160a01b03811661108d57505f925060019150829050611096565b92505f91508190505b9450945094915050565b5f60ff8216601f811115610ae157604051632cd44ac360e21b815260040160405180910390fd5b634e487b7160e01b5f52601160045260245ffd5b80820180821115610ae157610ae16110c7565b80356001600160a01b0381168114611104575f80fd5b919050565b5f805f805f805f60c0888a03121561111f575f80fd5b611128886110ee565b9650611136602089016110ee565b955060408801359450606088013593506080880135925060a088013567ffffffffffffffff80821115611167575f80fd5b818a0191508a601f83011261117a575f80fd5b813581811115611188575f80fd5b8b6020828501011115611199575f80fd5b60208301945080935050505092959891949750929550565b5f602082840312156111c1575f80fd5b5035919050565b5f602082840312156111d8575f80fd5b6111e1826110ee565b9392505050565b5f80604083850312156111f9575f80fd5b823560038110611207575f80fd5b915060208301356001600160601b0381168114611222575f80fd5b809150509250929050565b5f81518084525f5b8181101561125157602081850181015186830182015201611235565b505f602082860101526020601f19601f83011685010191505092915050565b60ff60f81b881681525f602060e0602084015261129060e084018a61122d565b83810360408501526112a2818a61122d565b606085018990526001600160a01b038816608086015260a0850187905284810360c0860152855180825260208088019350909101905f5b818110156112f5578351835292840192918401916001016112d9565b50909c9b505050505050505050505050565b5f60208284031215611317575f80fd5b813580151581146111e1575f80fd5b634e487b7160e01b5f52602160045260245ffd5b6003811061135657634e487b7160e01b5f52602160045260245ffd5b9052565b60208101610ae1828461133a565b81810381811115610ae157610ae16110c7565b60408101611389828561133a565b6001600160601b0383166020830152939250505056fe9b779b17422d0df92223018b32b4d1fa46e071723d6817e2486d003becc55f00a264697066735822122072c3ac55d49b88be3a4620281826738b18f4db4d2b0df52aef78e32bf221573a64736f6c63430008180033\n";

    private static String librariesLinkedBinary;

    public static final String FUNC_BALANCEOF = "balanceOf";

    public static final String FUNC_DEPOSIT = "deposit";

    public static final String FUNC_DOMAINSEPARATOR = "domainSeparator";

    public static final String FUNC_EIP712DOMAIN = "eip712Domain";

    public static final String FUNC_EXECUTEPAYMENT = "executePayment";

    public static final String FUNC_ISDISABLED = "isDisabled";

    public static final String FUNC_NONCES = "nonces";

    public static final String FUNC_SETKILLSWITCH = "setKillSwitch";

    public static final String FUNC_SETSPENDLIMIT = "setSpendLimit";

    public static final String FUNC_SPENDLIMITS = "spendLimits";

    public static final String FUNC_SPENDPERIOD = "spendPeriod";

    public static final String FUNC_WITHDRAW = "withdraw";

    public static final Event DEPOSITED_EVENT = new Event("Deposited", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event EIP712DOMAINCHANGED_EVENT = new Event("EIP712DomainChanged", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final Event KILLSWITCHSET_EVENT = new Event("KillSwitchSet", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Bool>() {}));
    ;

    public static final Event PAYMENTEXECUTED_EVENT = new Event("PaymentExecuted", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event SPENDLIMITSET_EVENT = new Event("SpendLimitSet", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint8>() {}, new TypeReference<Uint96>() {}));
    ;

    public static final Event WITHDRAWN_EVENT = new Event("Withdrawn", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}));
    ;

    public static final CustomError DEADLINETOOFARINFUTURE_ERROR = new CustomError("DeadlineTooFarInFuture", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError ECDSAINVALIDSIGNATURE_ERROR = new CustomError("ECDSAInvalidSignature", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError ECDSAINVALIDSIGNATURELENGTH_ERROR = new CustomError("ECDSAInvalidSignatureLength", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    ;

    public static final CustomError ECDSAINVALIDSIGNATURES_ERROR = new CustomError("ECDSAInvalidSignatureS", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
    ;

    public static final CustomError EXPIREDDEADLINE_ERROR = new CustomError("ExpiredDeadline", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError INSUFFICIENTBALANCE_ERROR = new CustomError("InsufficientBalance", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError INVALIDSHORTSTRING_ERROR = new CustomError("InvalidShortString", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError INVALIDSIGNATURE_ERROR = new CustomError("InvalidSignature", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError INVALIDTOADDRESS_ERROR = new CustomError("InvalidToAddress", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError NONCEALREADYUSED_ERROR = new CustomError("NonceAlreadyUsed", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError REENTRANCYGUARDREENTRANTCALL_ERROR = new CustomError("ReentrancyGuardReentrantCall", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError SPENDLIMITEXCEEDED_ERROR = new CustomError("SpendLimitExceeded", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError STRINGTOOLONG_ERROR = new CustomError("StringTooLong", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}));
    ;

    public static final CustomError WALLETDISABLED_ERROR = new CustomError("WalletDisabled", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError ZEROADDRESS_ERROR = new CustomError("ZeroAddress", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError ZEROAMOUNT_ERROR = new CustomError("ZeroAmount", 
            Arrays.<TypeReference<?>>asList());
    ;

    @Deprecated
    protected VolticSmartWallet(String contractAddress, Web3j web3j, Credentials credentials,
            BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected VolticSmartWallet(String contractAddress, Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected VolticSmartWallet(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected VolticSmartWallet(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public RemoteFunctionCall<BigInteger> balanceOf(String param0) {
        final Function function = new Function(FUNC_BALANCEOF, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> deposit(BigInteger weiValue) {
        final Function function = new Function(
                FUNC_DEPOSIT, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function, weiValue);
    }

    public RemoteFunctionCall<byte[]> domainSeparator() {
        final Function function = new Function(FUNC_DOMAINSEPARATOR, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }

    public RemoteFunctionCall<Tuple7<byte[], String, String, BigInteger, String, byte[], List<BigInteger>>> eip712Domain(
            ) {
        final Function function = new Function(FUNC_EIP712DOMAIN, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes1>() {}, new TypeReference<Utf8String>() {}, new TypeReference<Utf8String>() {}, new TypeReference<Uint256>() {}, new TypeReference<Address>() {}, new TypeReference<Bytes32>() {}, new TypeReference<DynamicArray<Uint256>>() {}));
        return new RemoteFunctionCall<Tuple7<byte[], String, String, BigInteger, String, byte[], List<BigInteger>>>(function,
                new Callable<Tuple7<byte[], String, String, BigInteger, String, byte[], List<BigInteger>>>() {
                    @Override
                    public Tuple7<byte[], String, String, BigInteger, String, byte[], List<BigInteger>> call(
                            ) throws Exception {
                        List<Type> results = executeCallMultipleValueReturn(function);
                        return new Tuple7<byte[], String, String, BigInteger, String, byte[], List<BigInteger>>(
                                (byte[]) results.get(0).getValue(), 
                                (String) results.get(1).getValue(), 
                                (String) results.get(2).getValue(), 
                                (BigInteger) results.get(3).getValue(), 
                                (String) results.get(4).getValue(), 
                                (byte[]) results.get(5).getValue(), 
                                convertToNative((List<Uint256>) results.get(6).getValue()));
                    }
                });
    }

    public RemoteFunctionCall<TransactionReceipt> executePayment(String owner, String to,
            BigInteger amount, BigInteger nonce, BigInteger deadline, byte[] signature) {
        final Function function = new Function(
                FUNC_EXECUTEPAYMENT, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, owner), 
                new org.web3j.abi.datatypes.Address(160, to), 
                new org.web3j.abi.datatypes.generated.Uint256(amount), 
                new org.web3j.abi.datatypes.generated.Uint256(nonce), 
                new org.web3j.abi.datatypes.generated.Uint256(deadline), 
                new org.web3j.abi.datatypes.DynamicBytes(signature)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<Boolean> isDisabled(String param0) {
        final Function function = new Function(FUNC_ISDISABLED, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<BigInteger> nonces(String param0) {
        final Function function = new Function(FUNC_NONCES, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> setKillSwitch(Boolean disabled) {
        final Function function = new Function(
                FUNC_SETKILLSWITCH, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Bool(disabled)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> setSpendLimit(BigInteger period,
            BigInteger amount) {
        final Function function = new Function(
                FUNC_SETSPENDLIMIT, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint8(period), 
                new org.web3j.abi.datatypes.generated.Uint96(amount)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<Tuple3<BigInteger, BigInteger, BigInteger>> spendLimits(
            String param0) {
        final Function function = new Function(FUNC_SPENDLIMITS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint96>() {}, new TypeReference<Uint96>() {}, new TypeReference<Uint64>() {}));
        return new RemoteFunctionCall<Tuple3<BigInteger, BigInteger, BigInteger>>(function,
                new Callable<Tuple3<BigInteger, BigInteger, BigInteger>>() {
                    @Override
                    public Tuple3<BigInteger, BigInteger, BigInteger> call() throws Exception {
                        List<Type> results = executeCallMultipleValueReturn(function);
                        return new Tuple3<BigInteger, BigInteger, BigInteger>(
                                (BigInteger) results.get(0).getValue(), 
                                (BigInteger) results.get(1).getValue(), 
                                (BigInteger) results.get(2).getValue());
                    }
                });
    }

    public RemoteFunctionCall<BigInteger> spendPeriod(String param0) {
        final Function function = new Function(FUNC_SPENDPERIOD, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint8>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> withdraw(BigInteger amount) {
        final Function function = new Function(
                FUNC_WITHDRAW, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(amount)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public static List<DepositedEventResponse> getDepositedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(DEPOSITED_EVENT, transactionReceipt);
        ArrayList<DepositedEventResponse> responses = new ArrayList<DepositedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            DepositedEventResponse typedResponse = new DepositedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.user = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static DepositedEventResponse getDepositedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(DEPOSITED_EVENT, log);
        DepositedEventResponse typedResponse = new DepositedEventResponse();
        typedResponse.log = log;
        typedResponse.user = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<DepositedEventResponse> depositedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getDepositedEventFromLog(log));
    }

    public Flowable<DepositedEventResponse> depositedEventFlowable(DefaultBlockParameter startBlock,
            DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(DEPOSITED_EVENT));
        return depositedEventFlowable(filter);
    }

    public static List<EIP712DomainChangedEventResponse> getEIP712DomainChangedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(EIP712DOMAINCHANGED_EVENT, transactionReceipt);
        ArrayList<EIP712DomainChangedEventResponse> responses = new ArrayList<EIP712DomainChangedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            EIP712DomainChangedEventResponse typedResponse = new EIP712DomainChangedEventResponse();
            typedResponse.log = eventValues.getLog();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static EIP712DomainChangedEventResponse getEIP712DomainChangedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(EIP712DOMAINCHANGED_EVENT, log);
        EIP712DomainChangedEventResponse typedResponse = new EIP712DomainChangedEventResponse();
        typedResponse.log = log;
        return typedResponse;
    }

    public Flowable<EIP712DomainChangedEventResponse> eIP712DomainChangedEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getEIP712DomainChangedEventFromLog(log));
    }

    public Flowable<EIP712DomainChangedEventResponse> eIP712DomainChangedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(EIP712DOMAINCHANGED_EVENT));
        return eIP712DomainChangedEventFlowable(filter);
    }

    public static List<KillSwitchSetEventResponse> getKillSwitchSetEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(KILLSWITCHSET_EVENT, transactionReceipt);
        ArrayList<KillSwitchSetEventResponse> responses = new ArrayList<KillSwitchSetEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            KillSwitchSetEventResponse typedResponse = new KillSwitchSetEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.user = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.disabled = (Boolean) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static KillSwitchSetEventResponse getKillSwitchSetEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(KILLSWITCHSET_EVENT, log);
        KillSwitchSetEventResponse typedResponse = new KillSwitchSetEventResponse();
        typedResponse.log = log;
        typedResponse.user = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.disabled = (Boolean) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<KillSwitchSetEventResponse> killSwitchSetEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getKillSwitchSetEventFromLog(log));
    }

    public Flowable<KillSwitchSetEventResponse> killSwitchSetEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(KILLSWITCHSET_EVENT));
        return killSwitchSetEventFlowable(filter);
    }

    public static List<PaymentExecutedEventResponse> getPaymentExecutedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(PAYMENTEXECUTED_EVENT, transactionReceipt);
        ArrayList<PaymentExecutedEventResponse> responses = new ArrayList<PaymentExecutedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            PaymentExecutedEventResponse typedResponse = new PaymentExecutedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.owner = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.to = (String) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static PaymentExecutedEventResponse getPaymentExecutedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(PAYMENTEXECUTED_EVENT, log);
        PaymentExecutedEventResponse typedResponse = new PaymentExecutedEventResponse();
        typedResponse.log = log;
        typedResponse.owner = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.to = (String) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
        return typedResponse;
    }

    public Flowable<PaymentExecutedEventResponse> paymentExecutedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getPaymentExecutedEventFromLog(log));
    }

    public Flowable<PaymentExecutedEventResponse> paymentExecutedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(PAYMENTEXECUTED_EVENT));
        return paymentExecutedEventFlowable(filter);
    }

    public static List<SpendLimitSetEventResponse> getSpendLimitSetEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(SPENDLIMITSET_EVENT, transactionReceipt);
        ArrayList<SpendLimitSetEventResponse> responses = new ArrayList<SpendLimitSetEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            SpendLimitSetEventResponse typedResponse = new SpendLimitSetEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.user = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.period = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static SpendLimitSetEventResponse getSpendLimitSetEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(SPENDLIMITSET_EVENT, log);
        SpendLimitSetEventResponse typedResponse = new SpendLimitSetEventResponse();
        typedResponse.log = log;
        typedResponse.user = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.period = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
        return typedResponse;
    }

    public Flowable<SpendLimitSetEventResponse> spendLimitSetEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getSpendLimitSetEventFromLog(log));
    }

    public Flowable<SpendLimitSetEventResponse> spendLimitSetEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(SPENDLIMITSET_EVENT));
        return spendLimitSetEventFlowable(filter);
    }

    public static List<WithdrawnEventResponse> getWithdrawnEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(WITHDRAWN_EVENT, transactionReceipt);
        ArrayList<WithdrawnEventResponse> responses = new ArrayList<WithdrawnEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            WithdrawnEventResponse typedResponse = new WithdrawnEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.user = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static WithdrawnEventResponse getWithdrawnEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(WITHDRAWN_EVENT, log);
        WithdrawnEventResponse typedResponse = new WithdrawnEventResponse();
        typedResponse.log = log;
        typedResponse.user = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<WithdrawnEventResponse> withdrawnEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getWithdrawnEventFromLog(log));
    }

    public Flowable<WithdrawnEventResponse> withdrawnEventFlowable(DefaultBlockParameter startBlock,
            DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(WITHDRAWN_EVENT));
        return withdrawnEventFlowable(filter);
    }

    @Deprecated
    public static VolticSmartWallet load(String contractAddress, Web3j web3j,
            Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new VolticSmartWallet(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static VolticSmartWallet load(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new VolticSmartWallet(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static VolticSmartWallet load(String contractAddress, Web3j web3j,
            Credentials credentials, ContractGasProvider contractGasProvider) {
        return new VolticSmartWallet(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static VolticSmartWallet load(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new VolticSmartWallet(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<VolticSmartWallet> deploy(Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider) {
        return deployRemoteCall(VolticSmartWallet.class, web3j, credentials, contractGasProvider, getDeploymentBinary(), "");
    }

    public static RemoteCall<VolticSmartWallet> deploy(Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(VolticSmartWallet.class, web3j, transactionManager, contractGasProvider, getDeploymentBinary(), "");
    }

    @Deprecated
    public static RemoteCall<VolticSmartWallet> deploy(Web3j web3j, Credentials credentials,
            BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(VolticSmartWallet.class, web3j, credentials, gasPrice, gasLimit, getDeploymentBinary(), "");
    }

    @Deprecated
    public static RemoteCall<VolticSmartWallet> deploy(Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(VolticSmartWallet.class, web3j, transactionManager, gasPrice, gasLimit, getDeploymentBinary(), "");
    }

    public static void linkLibraries(List<Contract.LinkReference> references) {
        librariesLinkedBinary = linkBinaryWithReferences(BINARY, references);
    }

    private static String getDeploymentBinary() {
        if (librariesLinkedBinary != null) {
            return librariesLinkedBinary;
        } else {
            return BINARY;
        }
    }

    public static class DepositedEventResponse extends BaseEventResponse {
        public String user;

        public BigInteger amount;
    }

    public static class EIP712DomainChangedEventResponse extends BaseEventResponse {
    }

    public static class KillSwitchSetEventResponse extends BaseEventResponse {
        public String user;

        public Boolean disabled;
    }

    public static class PaymentExecutedEventResponse extends BaseEventResponse {
        public String owner;

        public String to;

        public BigInteger amount;

        public BigInteger nonce;
    }

    public static class SpendLimitSetEventResponse extends BaseEventResponse {
        public String user;

        public BigInteger period;

        public BigInteger amount;
    }

    public static class WithdrawnEventResponse extends BaseEventResponse {
        public String user;

        public BigInteger amount;
    }
}
