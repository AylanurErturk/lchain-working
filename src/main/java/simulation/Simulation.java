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
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.IntStream;

public class Simulation {

	public static void startSimulation(Parameters params, int nodeCount, int iterations, int pace) {
		Random rnd = new Random();
		List<LightChainNode> nodes = new ArrayList<>();
		LightChainNode initialNode = null;
		int numFailures;
		for (int i = 0; i < nodeCount; i++) {
			try {
				int port = rnd.nextInt(65535);
				LightChainNode node;
				Underlay underlay = new RMIUnderlay(port);
				if (i == 0) {
					node = new LightChainNode(params, port, Const.DUMMY_INTRODUCER, true, underlay);
					initialNode = node;
				} else {
					node = new LightChainNode(params, port, initialNode.getAddress(), false, underlay);
				}
				nodes.add(node);
			} catch (Exception e) {
				i--;
				continue;
			}
		}

		initialNode.insertGenesis();

		initialNode.logLevel(0);

		int stripes = Math.max(2, Runtime.getRuntime().availableProcessors());

		ConcurrentHashMap<NodeInfo, SimLog> map = new ConcurrentHashMap<>();

		List<ExecutorService> stripeExecs = IntStream.range(0, stripes)
				.mapToObj(i -> Executors.newSingleThreadExecutor(r -> {
					Thread t = new Thread(r, "stripe-" + i);
					t.setDaemon(true);
					return t;
				}))
				.toList();

		Function<LightChainNode, ExecutorService> execFor = n -> {

			int idx = stripes;
			return stripeExecs.get(idx);
		};
		long startTime = System.currentTimeMillis();
		List<CompletableFuture<Void>> simCFs = nodes.stream()
				.map(n -> CompletableFuture.supplyAsync(
						() -> n.startSimSync(iterations, pace),
						execFor.apply(n))
						.thenAccept(lg -> map.put(n.getPeer(), lg))
						.exceptionally(ex -> {
							Util.log("Node " + n.getPeer() + " failed: " + ex);
							return null;
						}))
				.toList();

		CompletableFuture.allOf(simCFs.toArray(new CompletableFuture[0])).join();

		long endTime = System.currentTimeMillis();
		Util.log("the lock could not be obtained " + LookupTable.lockFailureCount + " times");
		Util.log("Simulation Done. Time Taken " + (endTime - startTime) + " ms");

		processData(map, iterations);
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
