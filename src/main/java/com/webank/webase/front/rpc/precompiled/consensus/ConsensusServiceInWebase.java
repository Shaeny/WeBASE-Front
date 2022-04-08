/*
 * Copyright 2014-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.webank.webase.front.rpc.precompiled.consensus;

import static com.webank.webase.front.util.PrecompiledUtils.NODE_TYPE_OBSERVER;
import static com.webank.webase.front.util.PrecompiledUtils.NODE_TYPE_REMOVE;
import static com.webank.webase.front.util.PrecompiledUtils.NODE_TYPE_SEALER;
import static org.fisco.bcos.sdk.contract.precompiled.consensus.ConsensusPrecompiled.FUNC_ADDOBSERVER;
import static org.fisco.bcos.sdk.contract.precompiled.consensus.ConsensusPrecompiled.FUNC_ADDSEALER;
import static org.fisco.bcos.sdk.contract.precompiled.consensus.ConsensusPrecompiled.FUNC_REMOVE;

import com.webank.webase.front.base.code.ConstantCode;
import com.webank.webase.front.base.enums.PrecompiledTypes;
import com.webank.webase.front.base.exception.FrontException;
import com.webank.webase.front.base.response.BaseResponse;
import com.webank.webase.front.rpc.precompiled.base.PrecompiledCommonInfo;
import com.webank.webase.front.rpc.precompiled.consensus.entity.NodeInfo;
import com.webank.webase.front.transaction.TransService;
import com.webank.webase.front.web3api.Web3ApiService;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.client.protocol.response.BcosGroupNodeInfo.GroupNodeInfo;
import org.fisco.bcos.sdk.client.protocol.response.SealerList.Sealer;
import org.fisco.bcos.sdk.model.PrecompiledRetCode;
import org.fisco.bcos.sdk.model.RetCode;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.fisco.bcos.sdk.transaction.codec.decode.ReceiptParser;
import org.fisco.bcos.sdk.transaction.model.exception.ContractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *  Node consensus status service;
 *  Handle transaction through webase-sign.
 */
@Slf4j
@Service
public class ConsensusServiceInWebase {

  @Autowired
  private Web3ApiService web3ApiService;
  @Autowired
  private TransService transService;

  /**
   * consensus: add sealer through webase-sign v1.5.0 增加校验群组文件是否存在，P2P连接存在
   */
  public String addSealer(String groupId, String signUserId, String nodeId, BigInteger weight) {
    // check node id
    if (!isValidNodeID(nodeId, groupId)) {
      return PrecompiledRetCode.CODE_INVALID_NODEID.toString();
    }
    List<String> sealerList = web3ApiService.getSealerStrList(groupId);
    if (sealerList.contains(nodeId)) {
      return ConstantCode.ALREADY_EXISTS_IN_SEALER_LIST.toString();
    }
    List<String> nodeIdList = web3ApiService.getGroupPeers(groupId);
    if (!nodeIdList.contains(nodeId)) {
      log.error("nodeId is not connected with others, cannot added as sealer");
      return ConstantCode.PEERS_NOT_CONNECTED.toString();
    }
    if (!containsGroupFile(groupId)) {
      throw new FrontException(ConstantCode.GENESIS_CONF_NOT_FOUND);
    }
    return this.addSealerHandle(nodeId, signUserId, groupId, weight);
  }

  public String addSealerHandle(String groupId, String signUserId, String nodeId,
      BigInteger weight) {
    List<Object> funcParams = new ArrayList<>();
    funcParams.add(nodeId);
    funcParams.add(weight);
    String contractAddress;
    boolean isWasm = web3ApiService.getWeb3j(groupId).isWASM();
    if (isWasm) {
      contractAddress = PrecompiledCommonInfo.getAddress(PrecompiledTypes.CONSENSUS_LIQUID);
    } else {
      contractAddress = PrecompiledCommonInfo.getAddress(PrecompiledTypes.CONSENSUS);
    }
    String abiStr = PrecompiledCommonInfo.getAbi(PrecompiledTypes.CONSENSUS);
    TransactionReceipt receipt =
        (TransactionReceipt) transService.transHandleWithSign(groupId,
            signUserId, contractAddress, abiStr, FUNC_ADDSEALER, funcParams, isWasm);
    return this.handleTransactionReceipt(receipt);
  }

  private boolean isValidNodeID(String _nodeID, String groupId) {
    boolean flag = false;
    List<String> nodeList = web3ApiService.getNodeList(groupId);
    for (String s : nodeList
    ) {
      if (s.equals(_nodeID)) {
        flag = true;
      }
    }
    return flag;
  }

  private boolean containsGroupFile(String groupId) {
    log.info("check front's node contains group file of groupId:{}", groupId);
    //todo check : 3.0 has no version
//        ClientVersion clientVersion = web3ApiService.getClientVersion();
//        int supportVer = CommonUtils.getVersionFromStr(clientVersion.getSupportedVersion());
//        if (supportVer < 241) {
//            log.info("client support version not support dynamic group");
//            return true;
//        }
    // INEXISTENT
//        String groupStatus = (String) web3ApiService.querySingleGroupStatus(groupId).getData();
//        if (GROUP_FILE_NOT_EXIST.equals(groupStatus)) {
//            log.error("node contains no group file to add in this group:{}", groupId);
//            return false;
//        }
    return true;
  }

  public String addObserver(String groupId, String signUserId, String nodeId) {
    // check node id
    if (!isValidNodeID(nodeId, groupId)) {
      return PrecompiledRetCode.CODE_INVALID_NODEID.toString();
    }
    List<String> observerList = web3ApiService.getObserverList(groupId);
    if (observerList.contains(nodeId)) {
      return ConstantCode.ALREADY_EXISTS_IN_OBSERVER_LIST.toString();
    }

    return this.addObserverHandle(groupId, signUserId, nodeId);
  }

  private String addObserverHandle(String groupId, String signUserId, String nodeId) {
    // trans
    List<Object> funcParams = new ArrayList<>();
    funcParams.add(nodeId);
    String contractAddress;
    boolean isWasm = web3ApiService.getWeb3j(groupId).isWASM();
    if (isWasm) {
      contractAddress = PrecompiledCommonInfo.getAddress(PrecompiledTypes.CONSENSUS_LIQUID);
    } else {
      contractAddress = PrecompiledCommonInfo.getAddress(PrecompiledTypes.CONSENSUS);
    }
    String abiStr = PrecompiledCommonInfo.getAbi(PrecompiledTypes.CONSENSUS);
    TransactionReceipt receipt =
        (TransactionReceipt) transService.transHandleWithSign(groupId,
            signUserId, contractAddress, abiStr, FUNC_ADDOBSERVER, funcParams, isWasm);
    return this.handleTransactionReceipt(receipt);
  }

  public String removeNode(String groupId, String signUserId, String nodeId) {
    List<String> groupPeers = web3ApiService.getGroupPeers(groupId);
    if (!groupPeers.contains(nodeId)) {
      return ConstantCode.ALREADY_REMOVED_FROM_THE_GROUP.toString();
    }
    // trans
    List<Object> funcParams = new ArrayList<>();
    funcParams.add(nodeId);
    String contractAddress;
    boolean isWasm = web3ApiService.getWeb3j(groupId).isWASM();
    if (isWasm) {
      contractAddress = PrecompiledCommonInfo.getAddress(PrecompiledTypes.CONSENSUS_LIQUID);
    } else {
      contractAddress = PrecompiledCommonInfo.getAddress(PrecompiledTypes.CONSENSUS);
    }
    String abiStr = PrecompiledCommonInfo.getAbi(PrecompiledTypes.CONSENSUS);
    TransactionReceipt receipt;
    try {
      receipt = (TransactionReceipt) transService.transHandleWithSign(groupId,
          signUserId, contractAddress, abiStr, FUNC_REMOVE, funcParams, isWasm);
    } catch (RuntimeException e) {
      // firstly remove node that sdk connected to the node, return the request that present
      // susscces
      // because the exception is throwed by getTransactionReceipt, we need ignore it.
      if (e.getMessage().contains("Don't send requests to this group")) {
        return ConstantCode.ALREADY_REMOVED_FROM_THE_GROUP.toString();
      } else {
        throw e;
      }
    }
    return this.handleTransactionReceipt(receipt);
  }

  private String handleTransactionReceipt(TransactionReceipt receipt) {
    log.debug("handle tx receipt of precompiled");
    try {
      RetCode sdkRetCode = ReceiptParser.parseTransactionReceipt(receipt);
      log.info("handleTransactionReceipt sdkRetCode:{}", sdkRetCode);
      if (sdkRetCode.getCode() >= 0) {
        return new BaseResponse(ConstantCode.RET_SUCCESS,
            sdkRetCode.getMessage()).toString();
      } else {
        throw new FrontException(sdkRetCode.getCode(), sdkRetCode.getMessage());
      }
    } catch (ContractException e) {
      log.error("handleTransactionReceipt e:[]", e);
      throw new FrontException(e.getErrorCode(), e.getMessage());
    }
  }

  public List<NodeInfo> getNodeList(String groupId) {
    // nodeListWithType 组合多个带有类型的nodeid list
    List<Sealer> sealerList = web3ApiService.getSealerList(groupId);

    List<String> sealerStrList = sealerList.stream().map(Sealer::getNodeID)
        .collect(Collectors.toList());
    ;
    List<String> observerList = web3ApiService.getObserverList(groupId);
    List<String> peerList = web3ApiService.getGroupPeers(groupId);
    // process nodeList
    List<NodeInfo> nodeListWithType = new ArrayList<>();

    // add all sealer and observer in List
    sealerList.forEach(sealer -> nodeListWithType.add(
        new NodeInfo(sealer.getNodeID(), NODE_TYPE_SEALER, sealer.getWeight())));
    observerList.forEach(
        observer -> nodeListWithType.add(new NodeInfo(observer, NODE_TYPE_OBSERVER)));
    // peer not in sealer/observer but connected is remove node(游离节点)
    peerList.stream()
        .filter(peer -> !sealerStrList.contains(peer) && !observerList.contains(peer))
        .forEach(peerToAdd -> nodeListWithType
            .add(new NodeInfo(peerToAdd, NODE_TYPE_REMOVE)));

    return nodeListWithType;
  }

}