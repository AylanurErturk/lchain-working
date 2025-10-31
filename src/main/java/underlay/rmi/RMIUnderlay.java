package underlay.rmi;

import blockchain.LightChainInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import skipGraph.SkipGraphNode;
import skipGraph.SkipNode;
import underlay.Underlay;
import underlay.requests.GenericRequest;
import underlay.requests.lightchain.PoVRequest;
import underlay.requests.skipgraph.*;
import underlay.responses.*;
import util.Util;

import java.io.FileNotFoundException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;

public class RMIUnderlay extends Underlay {

  private final String IP;
  private final int port;
  private final String address;
  private JavaRMIHost host;

  private static final Logger logger = LogManager.getLogger(RMIUnderlay.class);

  private final ConcurrentHashMap<String, RMIService> stubCache = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Integer> consecutiveErrors = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> circuitOpenUntil = new ConcurrentHashMap<>();

  private static final int CONNECT_TIMEOUT_MS = 1500;
  private static final int READ_TIMEOUT_MS = 10_000;
  private static final int ERROR_THRESHOLD = 5;
  private static final long OPEN_MILLIS = 30_000L;

  public RMIUnderlay(int port) {
    this.IP = Util.grabIP();
    this.port = port;
    this.address = IP + ":" + port;

    initRMI();

    try {
      host = new JavaRMIHost(this);
      try {
        LocateRegistry.createRegistry(port).rebind("RMIImpl", host);
      } catch (ExportException already) {
        LocateRegistry.getRegistry(port).rebind("RMIImpl", host);
      }
      logger.info("RMI bound at {}:{}", IP, port);
    } catch (RemoteException e) {
      logger.error("[RMIUnderlay] Registry bind failed on port {}", port, e);
      throw new RuntimeException(e);
    }
  }

  private static volatile boolean socketFactorySet = false;

  private void ensureTimeoutSocketFactory() {
    if (socketFactorySet)
      return;
    synchronized (RMIUnderlay.class) {
      if (socketFactorySet)
        return;
      try {
        RMISocketFactory.setSocketFactory(new RMISocketFactory() {
          @Override
          public java.net.Socket createSocket(String host, int port) throws java.io.IOException {
            var s = new java.net.Socket();
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            s.setSoTimeout(READ_TIMEOUT_MS);
            return s;
          }

          @Override
          public java.net.ServerSocket createServerSocket(int port) throws java.io.IOException {
            return new java.net.ServerSocket(port);
          }
        });
        socketFactorySet = true;
      } catch (Throwable t) {
        logger.debug("RMISocketFactory set skipped: {}", t.toString());
      }
    }
  }

  public GenericResponse answer(GenericRequest req) throws FileNotFoundException, RemoteException {
    return processRequest(this.skipNode, this.lightChainNode, req);
  }

  public static GenericResponse processRequest(SkipGraphNode skipGraphNode, LightChainInterface lightChainNode,
      GenericRequest req)
      throws FileNotFoundException, RemoteException {
    switch (req.type) {
      case PingRequest:
        skipGraphNode.ping();
        return new EmptyResponse();
      case SetLeftNodeRequest: {
        var r = (SetLeftNodeRequest) req;
        return new BooleanResponse(skipGraphNode.setLeftNode(r.num, r.level, r.newNode, r.oldNode));
      }
      case SetRightNodeRequest: {
        var r = (SetRightNodeRequest) req;
        return new BooleanResponse(skipGraphNode.setRightNode(r.num, r.level, r.newNode, r.oldNode));
      }
      case SearchByNumIDRequest: {
        var r = (SearchByNumIDRequest) req;
        return new NodeInfoResponse(skipGraphNode.searchByNumID(r.num));
      }
      case SearchByNameIDRequest: {
        var r = (SearchByNameIDRequest) req;
        return new NodeInfoResponse(skipGraphNode.searchByNameID(r.targetString));
      }
      case GetRightNodeRequest: {
        var r = (GetRightNodeRequest) req;
        return new NodeInfoResponse(skipGraphNode.getRightNode(r.level, r.num));
      }
      case GetLeftNodeRequest: {
        var r = (GetLeftNodeRequest) req;
        return new NodeInfoResponse(skipGraphNode.getLeftNode(r.level, r.num));
      }
      case GetNumIDRequest:
        return new IntegerResponse(skipGraphNode.getNumID());
      case InsertSearchRequest: {
        var r = (InsertSearchRequest) req;
        return new NodeInfoResponse(skipGraphNode.insertSearch(r.level, r.direction, r.num, r.target));
      }
      case SearchNumIDRequest: {
        SearchNumIDRequest r = (SearchNumIDRequest) req;
        return new NodeInfoListResponse(
            skipGraphNode.searchNumID(r.numID, r.searchTarget, r.level, r.lst));
      }
      case SearchNameRequest: {
        var r = (SearchNameRequest) req;
        return new NodeInfoResponse(skipGraphNode.searchName(r.numID, r.searchTarget, r.level, r.direction));
      }
      case GetRightNumIDRequest: {
        var r = (GetRightNumIDRequest) req;
        return new IntegerResponse(skipGraphNode.getRightNumID(r.level, r.num));
      }
      case GetLeftNumIDRequest: {
        var r = (GetLeftNumIDRequest) req;
        return new IntegerResponse(skipGraphNode.getLeftNumID(r.level, r.num));
      }
      case GetNodeRequest: {
        var r = (GetNodeRequest) req;
        return new NodeInfoResponse(skipGraphNode.getNode(r.num));
      }
      case RemoveFlagNodeRequest: {
        lightChainNode.removeFlagNode();
        return new EmptyResponse();
      }
      case SearchNumIDStepRequest: {
        var r = (SearchNumIDStepRequest) req;
        return skipGraphNode.searchNumIDStep(r.curNumID, r.searchTarget, r.level, r.dir);
      }

      case GetNodesWithNameIDPageRequest: {
        var r = (GetNodesWithNameIDPageRequest) req;
        var slice = skipGraphNode.getNodesWithNameIDPage(r.nameID, r.offset, r.limit);
        boolean hasMore = slice.size() == Math.min(Math.max(r.limit, 0), SkipNode.PAGE_LIMIT_CAP);
        int next = r.offset + slice.size();
        return new NodeInfoPageResponse(slice, hasMore, next);
      }

      case PoVRequest: {
        var r = (PoVRequest) req;
        return new SignatureResponse(r.blk != null ? lightChainNode.PoV(r.blk) : lightChainNode.PoV(r.t));
      }
      case GetPublicKeyRequest:
        return new PublicKeyResponse(lightChainNode.getPublicKey());
      case GetModeRequest:
        return new BooleanResponse(lightChainNode.getMode());
      case GetTokenRequest:
        return new IntegerResponse(lightChainNode.getToken());
      default:
        throw new RemoteException("Unknown request type: " + req.type);
    }
  }

  /**
   * This method returns the underlying RMI of the given address based on the
   * type.
   *
   * @param adrs A string which specifies the address of the target node.
   */
  public RMIService getRMI(String adrs) throws RemoteException, NotBoundException, MalformedURLException {
    if (!Util.validateIP(adrs)) {
      logger.debug("Error in lookup up RMI. Address " + adrs + " is not a valid address");
    }
    long now = System.currentTimeMillis();
    Long until = circuitOpenUntil.get(adrs);
    if (until != null && until > now) {
      throw new RemoteException("Circuit open for " + adrs + " until " + until);
    }

    RMIService cached = stubCache.get(adrs);
    if (cached != null)
      return cached;

    try {
      ensureTimeoutSocketFactory();
      String[] hostPort = adrs.split(":");
      String host = hostPort[0];
      int regPort = (hostPort.length > 1) ? Integer.parseInt(hostPort[1]) : 1099;

      Registry reg = LocateRegistry.getRegistry(host, regPort);
      RMIService stub = (RMIService) reg.lookup("RMIImpl");

      stubCache.put(adrs, stub);
      consecutiveErrors.remove(adrs);
      return stub;

    } catch (RemoteException | NotBoundException e) {
      bumpAndMaybeOpenCircuit(adrs, e);
      throw e;
    } catch (Exception e) {
      bumpAndMaybeOpenCircuit(adrs, e);
      throw new RemoteException("Lookup failed for " + adrs, e);
    }
  }

  private void bumpAndMaybeOpenCircuit(String adrs, Exception e) {
    int n = consecutiveErrors.merge(adrs, 1, Integer::sum);
    logger.warn("RMI failure for {} (consecutive={}): {}", adrs, n, e.toString());
    if (n >= ERROR_THRESHOLD) {
      long until = System.currentTimeMillis() + OPEN_MILLIS;
      circuitOpenUntil.put(adrs, until);
      logger.warn("Circuit OPEN for {} for {} ms", adrs, OPEN_MILLIS);
    }
  }

  private void invalidate(String adrs) {
    stubCache.remove(adrs);
  }

  public GenericResponse sendMessage(GenericRequest req, String targetAddress) {
    try {
      RMIService remote = getRMI(targetAddress);
      GenericResponse resp = remote.answer(req);
      if (resp == null)
        throw new RemoteException("Protocol violation: null response");
      return resp;
    } catch (Exception e) {
      invalidate(targetAddress);
      bumpAndMaybeOpenCircuit(targetAddress, e instanceof Exception ? (Exception) e : new Exception(e));
      // ÜST KATMAN NPE almasın; açık hata dönüyoruz:
      throw new RuntimeException("RMI send failed to " + targetAddress, e);
    }
  }

  /**
   * This method initializes all the RMI system properties required for proper
   * functionality
   */
  protected void initRMI() {
    System.setProperty("java.rmi.server.hostname", IP);
    System.setProperty("java.rmi.server.useLocalHostname", "false");
    System.out.println("RMI Server property set. Inet4Address: " + IP + ":" + port);
  }

  public boolean terminate() {
    try {
      Naming.unbind("//" + address + "/RMIImpl");
    } catch (Exception e) {
      logger.warn("[JavaRMIUnderlay] Could not unbind {}", address, e);
    }
    try {
      if (host != null)
        UnicastRemoteObject.unexportObject(host, true);
    } catch (Exception e) {
      logger.warn("[JavaRMIUnderlay] Could not unexport host", e);
    }
    return true;
  }
}
