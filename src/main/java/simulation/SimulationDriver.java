package simulation;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import blockchain.Parameters;
import util.PropertyManager;

public class SimulationDriver {
    private static final Logger logger = LogManager.getLogger(SimulationDriver.class);

    public static void main(String[] args) {
        initializeLogger();
        propMng = new PropertyManager("simulation.config");
        Parameters params = new Parameters();
        params.setAlpha(getIntProperty("alpha", "12"));
        params.setTxMin(getIntProperty("txmin", "5"));
        params.setSignaturesThreshold(getIntProperty("signaturesThreshold", "5"));
        params.setInitialBalance(getIntProperty("initialBalance", "20"));
        params.setLevels(getIntProperty("levels", "30"));
        params.setValidationFees(getIntProperty("validationFees", "1"));
        params.setMode(getBoolProperty("Mode", "True"));
        params.setInitialToken(getIntProperty("token", "20"));
        params.setChain(getBoolProperty("ContractMode", "True"));
        int nodeCount = getIntProperty("nodeCount", "20");
        int iterations = getIntProperty("iterations", "50");
        int pace = getIntProperty("pace", "1");

        logger.info("Starting simulation with parameters: " + params + " Number of nodes: " + nodeCount
                + "\n Number of iterations: " + iterations + "\n Pace: " + pace);

        simulation.Simulation.startSimulation(params, nodeCount, iterations, pace);
        System.exit(0);
    }

    private static void initializeLogger() {
        System.setProperty("log4j2.contextSelector",
                "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");
        //URI cfg = Paths.get("/src/main/resources/").toUri();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        //ctx.setConfigLocation(cfg);
        ctx.updateLoggers();
        //logger.debug("LOG_TEST: config loaded from {}", cfg);
        
        //var cfg2 = ctx.getConfiguration();
        //System.out.println("EFFECTIVE_ROOT_LEVEL=" + cfg2.getRootLogger().getLevel());
        //cfg2.getLoggers().forEach((n, l) -> System.out.println("LOGGER_LEVEL " + n + " = " + l.getLevel()));
        //cfg2.getAppenders()
                //.forEach((n, a) -> System.out.println("APPENDER " + n + " => " + a.getClass().getSimpleName()));

    }

    private static PropertyManager propMng;

    private static int getIntProperty(String key, String def) {
        return Integer.parseInt(propMng.getProperty(key, def));
    }

    private static boolean getBoolProperty(String key, String def) {
        return Boolean.parseBoolean(propMng.getProperty(key, def));
    }
}
