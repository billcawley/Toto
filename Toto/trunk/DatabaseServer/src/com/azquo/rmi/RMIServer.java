package com.azquo.rmi;

import com.azquo.app.magento.DSDataLoadService;
import com.azquo.dataimport.DSImportService;
import com.azquo.memorydb.service.DSAdminService;
import com.azquo.spreadsheet.DSSpreadsheetService;
import com.azquo.spreadsheet.JSTreeService;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/**
 * Created by cawley on 20/05/15.
 */
public class RMIServer {

    // could I make the implementation in spring and just bind it here? Tomayto tomahto.

    private RMIImplementation rmiImplementation;
    public RMIServer(DSSpreadsheetService dsSpreadsheetService, DSAdminService dsAdminService, DSDataLoadService dsDataLoadService, DSImportService dsImportService, JSTreeService jsTreeService) {
        try {
            rmiImplementation = new RMIImplementation(dsSpreadsheetService, dsAdminService, dsDataLoadService, dsImportService, jsTreeService);
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
