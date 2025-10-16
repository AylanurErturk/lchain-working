package underlay.rmi;

import underlay.requests.GenericRequest;
import underlay.responses.GenericResponse;

import java.io.FileNotFoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class JavaRMIHost extends UnicastRemoteObject implements RMIService {
  private final RMIUnderlay underlay;
  public JavaRMIHost(RMIUnderlay underlay, int objectPort) throws RemoteException {
    super(objectPort);            
    this.underlay = underlay;
  }
  public GenericResponse answer(GenericRequest req) throws FileNotFoundException {
    return underlay.answer(req);
  }
}
