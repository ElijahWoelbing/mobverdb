package de.uzl.mobverdb.join.modes.bitvektor;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.BitSet;
import org.apache.log4j.Logger;

import de.uzl.mobverdb.join.JoinUtils;
import de.uzl.mobverdb.join.data.CSVData;
import de.uzl.mobverdb.join.data.Row;
import de.uzl.mobverdb.join.modes.JoinPerf;
import de.uzl.mobverdb.join.modes.MeasurableJoin;
import de.uzl.utils.Threads;

public class BitJoinServer extends UnicastRemoteObject implements IBitJoinServer, MeasurableJoin {

    private static final long serialVersionUID = 9078308810431595227L;
    private final Logger log = Logger.getLogger(this.getClass().getCanonicalName());
    public static final String BIND_NAME = "semiJoin";
    private JoinPerf joinPerf = new JoinPerf(BIND_NAME);

    private IBitJoinClient client;
    private CSVData data;
    private int vektorSize;
    
    public BitJoinServer(File file) throws NumberFormatException, IOException {
        this(file, 10);
    }
    
    public BitJoinServer(File file, int vektorSize) throws NumberFormatException, IOException {
        super();
        this.data = new CSVData(file);
        this.vektorSize = vektorSize;
    }
    
    public void join() throws RemoteException, MalformedURLException {
        Registry reg = LocateRegistry.createRegistry(Registry.REGISTRY_PORT);
        Naming.rebind(BIND_NAME, this);
        
        log.info("Waiting for client to connect");
        while(client == null) {
            Threads.trySleep(1000);
        }
        log.info("Client connected. Beginning to join.");
        joinPerf.totalTime.start();
        
        log.info("Creating bitvektor of local data");
        joinPerf.prepareTime.start();
        BitSet bitSet = new BitSet(vektorSize);
        UniversalHash hashFunc = new UniversalHash(vektorSize);
        for(Row r : data.lines) {
            bitSet.set(hashFunc.hash(r.getKey()), true);
        }
        joinPerf.prepareTime.stop();
        
        log.info("Fetching from client");
        joinPerf.remoteJoinTime.start();
        Row[] remoteJoinedData = client.joinOn(bitSet, hashFunc);
        joinPerf.rmiCall();
        joinPerf.remoteJoinTime.stop();
        
        joinPerf.localJoinTime.start();
        log.info("Doing Local join");
        JoinUtils.nestedLoopJoin(data.lines, remoteJoinedData);
        joinPerf.localJoinTime.stop();
        
        try {
            this.client.shutdown();
            Threads.trySleep(1000);
            Naming.unbind(BIND_NAME);
            UnicastRemoteObject.unexportObject(reg, true);
        } catch(Exception e) {
            // ignore this, we will exit anyway
        }
    }

    @Override
    public void register(IBitJoinClient client) throws RemoteException {
        if(this.client == null) {
            this.client = client;
        }
    }

    @Override
    public JoinPerf getPerf() {
        return this.joinPerf;
    }
    
}


