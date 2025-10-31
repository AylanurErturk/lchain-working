package simulation;

import blockchain.LightChainNode;
import blockchain.Parameters;
import skipGraph.NodeInfo;
import underlay.Underlay;
import underlay.rmi.RMIUnderlay;
import util.Const;
import util.Util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;

public class Simulation {

	@SuppressWarnings("null")
	public static void startSimulation(Parameters params, int nodeCount, int iterations, int pace) {
		final ExecutorService pool = Executors.newFixedThreadPool(
				Math.max(2, Runtime.getRuntime().availableProcessors()));
		final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1); // if you use it elsewhere

		try {
			// -- build nodes
			List<LightChainNode> nodes = new ArrayList<>();
			LightChainNode initialNode = null;

			for (int i = 0; i < nodeCount; i++) {
				try {
					LightChainNode node;
					if (i == 0) {
						node = buildNode(params, null, true);
						initialNode = node;
					} else {
						node = buildNode(params, initialNode.getAddress(), false);
					}
					nodes.add(node);
				} catch (Exception e) {
					i--; // retry
				}
			}

			initialNode.insertGenesis();
			initialNode.logLevel(0);

			ConcurrentHashMap<NodeInfo, SimLog> results = new ConcurrentHashMap<>();

			long start = System.currentTimeMillis();

			// -- run every node in parallel on the pool
			List<CompletableFuture<Void>> futures = nodes.stream()
					.map(n -> CompletableFuture
							.supplyAsync(() -> n.startSim(iterations, pace), pool)
							.thenAccept(lg -> results.put(n.getPeer(), lg))
							.exceptionally(ex -> {
								Util.log("Node " + n.getPeer() + " failed: " + ex);
								return null;
							}))
					.toList();

			// wait for all nodes to finish
			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

			long end = System.currentTimeMillis();
			Util.log("Simulation Done. Time Taken " + (end - start) + " ms");

			processData(results, iterations);

		} catch (Exception ee) {
			throw new RuntimeException(ee);
		} finally {
			pool.shutdown();
			scheduler.shutdown();
		}
	}

	private static LightChainNode buildNode(Parameters params, String introducerAddr, boolean isIntroducer)
			throws Exception {
		int attempts = 0;
		while (true) {
			attempts++;
			int port = ThreadLocalRandom.current().nextInt(1024, 65535);
			try {
				Underlay underlay = new RMIUnderlay(port);
				if (isIntroducer) {
					LightChainNode n = new LightChainNode(params, port, Const.DUMMY_INTRODUCER, true, underlay);
					return n;
				} else {
					return new LightChainNode(params, port, introducerAddr, false, underlay);
				}
			} catch (Exception e) {
				if (attempts >= 15)
					throw e;
				try {
					Thread.sleep(ThreadLocalRandom.current().nextInt(5, 25));
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					throw e;
				}
			}
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
