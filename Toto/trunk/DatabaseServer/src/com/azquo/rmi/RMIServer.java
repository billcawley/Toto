package com.azquo.rmi;

import javax.annotation.PreDestroy;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 20/05/15.
 *
 * Just the minimum to make RMI work. Creating a registry seems to avoid all sorts or problems.
 */
public class RMIServer {

    // could I make the implementation in spring and just bind it here? Tomayto tomahto.

    private RMIImplementation rmiImplementation;
    private Registry registry;
    public RMIServer() {
        try {
            rmiImplementation = new RMIImplementation();
            RMIInterface stub = (RMIInterface) UnicastRemoteObject.exportObject(rmiImplementation, 0); // makes constructor not thread safe, dunno if we care
            registry = LocateRegistry.createRegistry(12345); // I'm not fording a registry port, do we care?
            registry.rebind(RMIInterface.serviceName, stub);
            System.out.println("RMI Server bound");
        } catch (RemoteException e) {
            System.out.println("Problem setting up RMI . . .");
            e.printStackTrace();
        }
    }

    @PreDestroy
    private void stopRMI(){
        System.out.println("=====================================destroying rmi server object");
        if (registry != null){
            try {
                registry.unbind(RMIInterface.serviceName);
                UnicastRemoteObject.unexportObject(rmiImplementation, true);
            } catch (RemoteException | NotBoundException e) {
                e.printStackTrace();
            }
        }
    }
}