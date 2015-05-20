package com.azquo.rmi;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/**
 * Created by cawley on 20/05/15.
 */
public class RMIServer {

    public RMIServer() {
        try {
            RMIImplementation rmiImplementation = new RMIImplementation();
            RMIInterface stub = (RMIInterface) UnicastRemoteObject.exportObject(rmiImplementation, 0); // makes constructor not thread safe, dunno if we care
            Registry registry = LocateRegistry.createRegistry(12345); // I'm not fording a registry port, do we care?
            registry.rebind(RMIInterface.serviceName, stub);
            System.out.println("RMI Server bound");
        } catch (RemoteException e) {
            System.out.println("Problem setting up RMI . . .");
            e.printStackTrace();
        }
    }

}
