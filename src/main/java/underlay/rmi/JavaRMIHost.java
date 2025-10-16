package underlay.rmi;

import underlay.requests.GenericRequest;
import underlay.responses.GenericResponse;

import java.io.FileNotFoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class JavaRMIHost extends UnicastRemoteObject implements RMIService {

  private final RMIUnderlay underlay;

  // NEW: export on a fixed port
  public JavaRMIHost(RMIUnderlay underlay, int objectPort) throws RemoteException {
    super(objectPort);              // <-- fixed export port here
    this.underlay = underlay;
  }

  // (optional) keep the old ctor if other code uses it
  public JavaRMIHost(RMIUnderlay underlay) throws RemoteException {
    this(underlay, 0);              // ephemeral (not used after we change RMIUnderlay)
  }

  public GenericResponse answer(GenericRequest req) throws FileNotFoundException {
    return underlay.answer(req);
  }
}