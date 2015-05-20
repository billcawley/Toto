package com.azquo.rmi;

import com.azquo.memorydb.DatabaseAccessToken;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Created by cawley on 20/05/15.
 *
 * it occurs that maybe the server interface itself could be exposed?
 *
 */
public class RMIClient {

    // I'm not sure if this is best practice but I was writing functions that were just passing through to this so I'll make it availiable here
    // todo - check singleton pattern which I never use properly

    public final RMIInterface serverInterface;

    public RMIClient() throws Exception{
            Registry registry = LocateRegistry.getRegistry("localhost", 12345);
            this.serverInterface = (RMIInterface) registry.lookup(RMIInterface.serviceName);
            System.out.println("Rmi client set up");
    }
}
