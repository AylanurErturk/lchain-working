package simulation;

import blockchain.Block;
import blockchain.LightChainNode;
import blockchain.Parameters;
import net.sf.saxon.lib.Logger;
import shard.ShardManager;
import skipGraph.LookupTable;
import skipGraph.NodeConfig;
import skipGraph.NodeInfo;
import underlay.InterfaceType;
import underlay.Underlay;
import underlay.rmi.RMIUnderlay;
import util.Const;
import util.Util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class ShardedSimulation {

    public static void startSimulation(Parameters params, int nodeCount, int iterations, int pace) {
        try {
            Random rnd = new Random();
            int port = rnd.nextInt(65000);
            ArrayList<LightChainNode> nodes = new ArrayList<>();


            // 2) Create the FIRST node with isInitial = true, dummy introducer
            //Block globalGenesis = ulsg.createGlobalGenesisBlock(params.getLevels(), sm);

            LightChainNode[] shardIntroducers = new LightChainNode[params.getMaxShards()];

            
            for (int shardID = 0; shardID < params.getMaxShards(); shardID++) {
                int introducerPort = rnd.nextInt(55535) + 10000; // Random valid port
                Underlay underlay = new RMIUnderlay(introducerPort);

                shardIntroducers[shardID] = new LightChainNode(
                        params,
                        introducerPort,
                        Const.DUMMY_INTRODUCER,
                        true, // first node (introducer) for every shard
                        underlay);

                
                nodes.add(shardIntroducers[shardID]);
                
            }
            
            for (int i = params.getMaxShards(); i < nodeCount; i++) {
                port++;
                Underlay underlay = new RMIUnderlay(port + i);

                LightChainNode node = new LightChainNode(
                        params,
                        port,
                        Const.DUMMY_INTRODUCER, 
                        false,
                        underlay);

                node.setIntroducerAfter(shardIntroducers[node.getShardID()].getAddress());
                nodes.add(node);

            }
            
            for (int shardID = 0; shardID < params.getMaxShards(); shardID++) {
                shardIntroducers[shardID].insertGenesis();
                shardIntroducers[shardID].logLevel(0);
            }

            ConcurrentHashMap<NodeInfo, SimLog> map = new ConcurrentHashMap<>();
            CountDownLatch latch = new CountDownLatch(nodes.size());
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < nodes.size(); ++i) {
                SimThread sim = new SimThread(nodes.get(i), latch, map, iterations, pace);
                sim.start();
            }
            latch.await();

            long endTime = System.currentTimeMillis();
            Util.log("The lock could not be obtained " + LookupTable.lockFailureCount + " times");
            Util.log("Sharded Simulation Done. Time Taken " + (endTime - startTime) + " ms");

            processData(map, iterations);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void processData(ConcurrentHashMap<NodeInfo, SimLog> map, int iterations) {
        processTransactions(map, iterations);
        processMineAttempts(map, iterations);
    }

    private static void processMineAttempts(ConcurrentHashMap<NodeInfo, SimLog> map, int iterations) {

        try {
            String logPath = System.getProperty("user.dir") + File.separator + "Logs" + File.separator
                    + "MineAttempts.csv";
            File logFile = new File(logPath);

            logFile.getParentFile().mkdirs();
            PrintWriter writer;

            writer = new PrintWriter(logFile);

            StringBuilder sb = new StringBuilder();

            sb.append("NumID," + "Honest," + "total time," + "foundTxMin," + "Validation time," + "successful\n");

            int successSum = 0;

            for (NodeInfo cur : map.keySet()) {
                SimLog log = map.get(cur);
                List<MineAttemptLog> validMine = log.getValidMineAttemptLog();
                List<MineAttemptLog> failedMine = log.getFailedMineAttemptLog();

                sb.append(cur.getNumID() + "," + log.getMode() + ",");
                for (int i = 0; i < validMine.size(); i++) {
                    if (i != 0)
                        sb.append(",,");
                    sb.append(validMine.get(i));
                }
                successSum += validMine.size();
                for (int i = 0; i < failedMine.size(); i++) {
                    sb.append(",,");
                    sb.append(failedMine.get(i));
                }
                sb.append('\n');
            }
            double successRate = (double) successSum / (1.0 * iterations * map.keySet().size()) * 100;
            sb.append("Success Rate = " + successRate + "\n");

            writer.write(sb.toString());
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static void processTransactions(ConcurrentHashMap<NodeInfo, SimLog> map, int iterations) {

        try {
            String logPath = System.getProperty("user.dir") + File.separator + "Logs" + File.separator
                    + "TransactionValidationAttempts.csv";
            File logFile = new File(logPath);

            logFile.getParentFile().mkdirs();
            PrintWriter writer;

            writer = new PrintWriter(logFile);

            StringBuilder sb = new StringBuilder();

            sb.append("NumID," + "Honest," + "Transaction Trials," + "Transaction Success,"
                    + "Transaction time(per)," + "Authenticated count," + "Sound count," + "Correct count,"
                    + "Has Balance count," + "Successful," + "Timer Per Validator(ms)\n");

            int successSum = 0;

            for (NodeInfo cur : map.keySet()) {
                SimLog log = map.get(cur);
                List<TransactionLog> validTransactions = log.getValidTransactions();
                List<TransactionLog> failedTransactions = log.getFailedTransactions();

                sb.append(cur.getNumID() + "," + log.getMode() + "," + log.getTotalTransactionTrials() + ","
                        + log.getValidTransactionTrials() + ",");
                for (int i = 0; i < validTransactions.size(); i++) {
                    if (i != 0)
                        sb.append(",,,,");
                    sb.append(validTransactions.get(i));
                }
                successSum += validTransactions.size();
                for (int i = 0; i < failedTransactions.size(); i++) {
                    sb.append(",,,,");
                    sb.append(failedTransactions.get(i));
                }
                sb.append('\n');
            }
            double successRate = (double) (successSum * 100.0) / (1.0 * iterations * map.keySet().size());
            sb.append("Success Rate = " + successRate + "\n");
            writer.write(sb.toString());
            writer.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    
}
