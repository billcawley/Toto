package com.azquo.rmi;

import com.azquo.app.magento.DSDataLoadService;
import com.azquo.dataimport.DSImportService;
import com.azquo.memorydb.core.MemoryDBManager;
import com.azquo.memorydb.service.DSAdminService;
import com.azquo.spreadsheet.DSSpreadsheetService;
import com.azquo.spreadsheet.JSTreeService;

import javax.annotation.PreDestroy;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/**
 * Created by cawley on 20/05/15.
 *
 * Just the minimum to make RMI work. Creating a restiry seems to avoid all sorts or problems.
 */
public class RMIServer {

    // could I make the implementation in spring and just bind it here? Tomayto tomahto.

    private RMIImplementation rmiImplementation;
    private Registry registry;
    public RMIServer(DSSpreadsheetService dsSpreadsheetService, DSAdminService dsAdminService, DSDataLoadService dsDataLoadService
            , DSImportService dsImportService, JSTreeService jsTreeService, MemoryDBManager memoryDBManager) {
        try {
            rmiImplementation = new RMIImplementation(dsSpreadsheetService, dsAdminService, dsDataLoadService, dsImportService, jsTreeService, memoryDBManager);
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
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (NotBoundException e) {
                e.printStackTrace();
            }
        }
    }

}
