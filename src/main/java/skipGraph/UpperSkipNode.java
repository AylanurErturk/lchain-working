package skipGraph;

import java.util.concurrent.ConcurrentHashMap;

import hashing.Hasher;
import hashing.HashingTools;
import org.apache.log4j.Logger;
import signature.DigitalSignature;
import signature.SignedBytes;
import simulation.SimLog;
import skipGraph.NodeInfo;
import skipGraph.SkipNode;
import underlay.InterfaceType;
import underlay.Underlay;
import underlay.rmi.RMIUnderlay;
import underlay.requests.lightchain.GetPublicKeyRequest;
import underlay.requests.lightchain.PoVRequest;
import underlay.requests.lightchain.RemoveFlagNodeRequest;
import underlay.responses.PublicKeyResponse;
import underlay.responses.SignatureResponse;
import util.Const;
import util.Util;

import java.io.FileNotFoundException;
import java.security.PublicKey;
import java.util.*;

import static underlay.responses.PublicKeyResponse.PublicKeyResponseOf;
import static underlay.responses.SignatureResponse.SignatureResponseOf;
import blockchain.*;
import blockchain.ContractCV;
import blockchain.LightChainCV;

public class UpperSkipNode extends LightChainNode {

  private static final long serialVersionUID = 1L;
  private List<Transaction> transactions;
  private DigitalSignature digitalSignature;
  private Hasher hasher;
  private Validator validator;
  public View view;
  public boolean mode;
  private int balance = 20;
  private SimLog simLog = new SimLog(true);
  public Logger logger;
  private Parameters params;
  protected int token; // Is used to store the value of tokens owned by a node.
  private CorrectnessVerifier cv;
  public int Tmode; // Defines the mode for every node eg. 1 -> consumer | 2 -> producer
  private Underlay underlay;
  protected LookupTable topLookup;
  private final Map<Integer, LookupTable> lookups = new ConcurrentHashMap<>();
  protected final ConcurrentHashMap<Integer, String> shardIntroducerAddresses = new ConcurrentHashMap<>();
  protected static final ConcurrentHashMap<Integer, UpperSkipNode> shardIntroducerNodes = new ConcurrentHashMap<>();

  public Underlay getUnderlay() {
    return underlay;
  }

  public UpperSkipNode(Parameters params, int port, String introducer, boolean isInitial, Underlay underlay,
      int shardID) {
    super(params, port, introducer, isInitial, underlay);
    this.params = params;
    this.digitalSignature = new DigitalSignature();
    this.hasher = new HashingTools();
    this.transactions = new ArrayList<>();
    this.view = new View();
    this.mode = params.getMode();
    this.token = params.getInitialToken();
    this.logger = Logger.getLogger(port + "");
    Tmode = (int) Math.round(Math.random());
    String name = hasher.getHash(digitalSignature.getPublicKey().getEncoded(), params.getLevels());
    super.setNumID(Integer.parseInt(name, 2));
    name = hasher.getHash(name, params.getLevels());
    super.setNameID(name);
    super.setShardID(assignToShard(super.getNumID()));
    logger.info("On LCN constructor.");

    NodeInfo peer = new NodeInfo(address, numID, nameID, shardID);
    addPeerNode(peer);

    this.underlay = underlay;
    underlay.setLightChainNode(this);

    if (isInitial) {
      logger.info("on USN constructor");
      isInserted = true;
      topLookup = new LookupTable(params.getLevels());
    }

    logger.info("putting the initial to shard introducers");
    shardIntroducerAddresses.put(shardID, peer.getAddress());
    shardIntroducerNodes.put(shardID, this);
    logger.info("putting the initial to top lookup");
    topLookup.initializeNode(peer);
    topLookup.finalizeNode();

    if (!isInitial) {

      insertNode(peer);
    }

    view.updateToken(getNumID(), this.token);
    if (params.getChain() == params.CONTRACT_MODE) {
      cv = new ContractCV(this); // LightChainCV extends CorrectnessVerifier for native LightChain
    } else {
      cv = new LightChainCV(this); // ContractCV extends CorrectnessVerifier for the contract mode
    }
  }

  protected LookupTable lt(int shardID, int maxLevels) {
    return lookups.computeIfAbsent(shardID, s -> new LookupTable(maxLevels));
  }

  protected Map<Integer, LookupTable> getLookupTables() {
    return lookups;
  }

  public ConcurrentHashMap<Integer, String> getShardIntroducerAddresses() {
    return shardIntroducerAddresses;
  }

  protected String getShardIntroducer(int shardID) {
    return shardIntroducerAddresses.get(shardID);
  }

  protected LookupTable getTopLookup() {
    return topLookup;
  }

  protected static UpperSkipNode getShardIntroducerNode(int shardID) {
    return shardIntroducerNodes.get(shardID);
  }

}