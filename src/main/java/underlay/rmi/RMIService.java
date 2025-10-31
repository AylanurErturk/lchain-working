package underlay.rmi;

import java.io.FileNotFoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;

import underlay.requests.GenericRequest;
import underlay.responses.GenericResponse;

/**
 * Represents a Java RMI Service. A RMI service only has a single function that
 * dispatches the received request
 * to the local `RequestHandler` instance.
 */
public interface RMIService extends Remote {
    GenericResponse answer(GenericRequest req) throws FileNotFoundException, RemoteException;
}
