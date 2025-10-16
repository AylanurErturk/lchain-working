package simulation;

import blockchain.LightChainNode;
import blockchain.Parameters;
import skipGraph.LookupTable;
import skipGraph.NodeInfo;
import underlay.Underlay;
import underlay.rmi.RMIUnderlay;
import util.Const;
import util.Util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class Simulation {

	public static void startSimulation(Parameters params, int nodeCount, int iterations, int pace) {
		try {
			ArrayList<LightChainNode> nodes = new ArrayList<>();
			LightChainNode initialNode = null;
			Set<Integer> taken = ConcurrentHashMap.newKeySet(); // track chosen ports to avoid collisions

			final int REG_MIN = 20000, REG_MAX = 45000;

			for (int i = 0; i < nodeCount; i++) {
				try {
					int registryPort = findFreePort(REG_MIN, REG_MAX, taken);
					taken.add(registryPort);

					// Prefer objectPort = registryPort + 10000, else find another free one
					int proposedObject = clampPort(registryPort + 10000);
					int objectPort;
					if (proposedObject >= 1024 && !taken.contains(proposedObject)) {
						// try binding to verify it’s free
						try (ServerSocket s = new ServerSocket()) {
							s.setReuseAddress(true);
							s.bind(new InetSocketAddress("0.0.0.0", proposedObject));
							objectPort = proposedObject;
						}
					} else {
						// fallback: pick any other free port (still predictable per node)
						objectPort = findFreePort(45001, 60000, taken);
					}
					taken.add(objectPort);

					Underlay underlay = new RMIUnderlay(registryPort, objectPort);

					LightChainNode node;
					if (i == 0) {
						node = new LightChainNode(params, registryPort, Const.DUMMY_INTRODUCER, true, underlay);
						initialNode = node;
					} else {
						node = new LightChainNode(params, registryPort, initialNode.getAddress(), false, underlay);
					}
					nodes.add(node);
				} catch (Exception e) {
					i--; // retry this index

					continue;
				}
			}

			initialNode.insertGenesis();

			initialNode.logLevel(0);

			ConcurrentHashMap<NodeInfo, SimLog> map = new ConcurrentHashMap<>();
			CountDownLatch latch = new CountDownLatch(nodes.size());
			long startTime = System.currentTimeMillis();
			for (int i = 0; i < nodes.size(); ++i) {
				SimThread sim = new SimThread(nodes.get(i), latch, map, iterations, pace);
				sim.start();
			}
			latch.await();

			long endTime = System.currentTimeMillis();
			Util.log("the lock could not be obtained " + LookupTable.lockFailureCount + " times");
			Util.log("Simulation Done. Time Taken " + (endTime - startTime) + " ms");

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

	private static int findFreePort(int min, int max, Set<Integer> reserved) throws IOException {
		for (int p = min; p <= max; p++) {
			if (reserved.contains(p))
				continue;
			try (ServerSocket s = new ServerSocket()) {
				s.setReuseAddress(true);
				s.bind(new InetSocketAddress("0.0.0.0", p));
				return p;
			} catch (IOException ignore) {
			}
		}
		throw new IOException("No free port in range " + min + "-" + max);
	}

	private static int clampPort(int p) {
		return (p > 65535 ? (p % 65536) : p);
	}

}
