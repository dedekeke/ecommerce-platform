package com.ecommerce.integration;

import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.reporters.Summariser;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Programmatic JMeter test runner for load testing
 *
 * This class creates and executes JMeter test plans programmatically
 * for automated load testing of the order flow.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LoadTestRunner {

    private static final String JMETER_HOME = System.getenv().getOrDefault("JMETER_HOME", "/usr/local/Cellar/jmeter");
    private static final String RESULTS_DIR = "target/jmeter-results";

    @BeforeAll
    void setup() throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("JMeter LOAD TEST RUNNER");
        System.out.println("=".repeat(80));

        // Create results directory
        Files.createDirectories(Paths.get(RESULTS_DIR));

        // Initialize JMeter
        File jmeterHome = new File(JMETER_HOME);
        if (jmeterHome.exists()) {
            JMeterUtils.setJMeterHome(JMETER_HOME);
            JMeterUtils.loadJMeterProperties(JMETER_HOME + "/bin/jmeter.properties");
            JMeterUtils.initLocale();
            System.out.println("✓ JMeter initialized from: " + JMETER_HOME);
        } else {
            System.out.println("⚠ JMeter home not found: " + JMETER_HOME);
            System.out.println("  Set JMETER_HOME environment variable or install JMeter");
            System.out.println("  On macOS: brew install jmeter");
        }
    }

    @Test
    void runOrderFlowLoadTest() throws Exception {
        System.out.println("\n[LOAD TEST] Running Order Flow Load Test");
        System.out.println("  Configuration:");
        System.out.println("    - Users: 1000");
        System.out.println("    - Ramp-up: 60 seconds");
        System.out.println("    - Iterations: 1 per user");
        System.out.println("    - Total requests: ~4000");

        try {
            // Create Test Plan
            TestPlan testPlan = new TestPlan("Order Flow Load Test");
            testPlan.setFunctionalMode(false);
            testPlan.setSerialized(false);

            // Create Thread Group
            ThreadGroup threadGroup = new ThreadGroup();
            threadGroup.setName("Order Flow Users");
            threadGroup.setNumThreads(1000);
            threadGroup.setRampUp(60);

            // Create Loop Controller
            LoopController loopController = new LoopController();
            loopController.setLoops(1);
            loopController.setFirst(true);
            loopController.initialize();
            threadGroup.setSamplerController(loopController);

            // Create HTTP Header Manager
            HeaderManager headerManager = new HeaderManager();
            headerManager.add(new org.apache.jmeter.protocol.http.control.Header("Content-Type", "application/json"));

            // Create HTTP Samplers
            HTTPSamplerProxy createUserSampler = createHTTPSampler(
                    "Create User",
                    "localhost",
                    8081,
                    "/api/users",
                    "POST",
                    "{\"auth0Id\":\"load-test-${__UUID()}\",\"email\":\"loadtest${__threadNum()}@example.com\",\"firstName\":\"Load\",\"lastName\":\"Test${__threadNum()}\"}"
            );

            HTTPSamplerProxy addToCartSampler = createHTTPSampler(
                    "Add to Cart",
                    "localhost",
                    8083,
                    "/api/cart/${userId}/items",
                    "POST",
                    "{\"productId\":\"${productId}\",\"productName\":\"Test Product\",\"price\":49.99,\"quantity\":1}"
            );

            HTTPSamplerProxy createOrderSampler = createHTTPSampler(
                    "Create Order",
                    "localhost",
                    8084,
                    "/api/orders",
                    "POST",
                    "{\"userId\":\"${userId}\",\"shippingAddress\":{\"street\":\"123 Test St\",\"city\":\"Test\",\"state\":\"TS\",\"postalCode\":\"12345\",\"country\":\"USA\"}}"
            );

            // Create Summariser for results
            Summariser summer = null;
            String summariserName = JMeterUtils.getPropDefault("summariser.name", "summary");
            if (summariserName.length() > 0) {
                summer = new Summariser(summariserName);
            }

            // Create Result Collector
            ResultCollector logger = new ResultCollector(summer);
            logger.setFilename(RESULTS_DIR + "/order-flow-load-test-results.jtl");

            // Build Test Plan Tree
            HashTree testPlanTree = new HashTree();
            HashTree threadGroupHashTree = testPlanTree.add(testPlan, threadGroup);
            threadGroupHashTree.add(headerManager);
            threadGroupHashTree.add(createUserSampler);
            threadGroupHashTree.add(addToCartSampler);
            threadGroupHashTree.add(createOrderSampler);
            testPlanTree.add(testPlanTree.getArray()[0], logger);

            // Run Test
            StandardJMeterEngine jmeter = new StandardJMeterEngine();
            jmeter.configure(testPlanTree);

            System.out.println("\n  Starting load test...");
            jmeter.run();

            System.out.println("\n✓ Load test completed");
            System.out.println("  Results saved to: " + RESULTS_DIR + "/order-flow-load-test-results.jtl");

        } catch (Exception e) {
            System.err.println("✗ Load test failed: " + e.getMessage());
            System.err.println("  Make sure:");
            System.err.println("    1. JMeter is installed (brew install jmeter)");
            System.err.println("    2. JMETER_HOME environment variable is set");
            System.err.println("    3. All services are running (docker-compose up)");
            throw e;
        }
    }

    private HTTPSamplerProxy createHTTPSampler(String name, String domain, int port, String path, String method, String body) {
        HTTPSamplerProxy sampler = new HTTPSamplerProxy();
        sampler.setName(name);
        sampler.setDomain(domain);
        sampler.setPort(port);
        sampler.setPath(path);
        sampler.setMethod(method);
        sampler.setFollowRedirects(true);
        sampler.setUseKeepAlive(true);

        if (body != null && !body.isEmpty()) {
            sampler.setPostBodyRaw(true);
            sampler.addNonEncodedArgument("", body, "");
        }

        return sampler;
    }

    @Test
    void printLoadTestInstructions() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("JMETER LOAD TEST INSTRUCTIONS");
        System.out.println("=".repeat(80));
        System.out.println("\nOption 1: Run tests programmatically (above)");
        System.out.println("  This test creates and runs JMeter tests using the JMeter API");
        System.out.println("\nOption 2: Run tests with JMeter CLI");
        System.out.println("  1. Install JMeter:");
        System.out.println("     brew install jmeter  # macOS");
        System.out.println();
        System.out.println("  2. Start all services:");
        System.out.println("     docker-compose up -d");
        System.out.println();
        System.out.println("  3. Run the load test:");
        System.out.println("     jmeter -n \\");
        System.out.println("       -t integration-tests/src/test/resources/jmeter/OrderFlowLoadTest.jmx \\");
        System.out.println("       -l results/results.jtl \\");
        System.out.println("       -e -o results/report");
        System.out.println();
        System.out.println("  4. View HTML report:");
        System.out.println("     open results/report/index.html");
        System.out.println();
        System.out.println("\nOption 3: Use JMeter GUI");
        System.out.println("  1. Start JMeter GUI:");
        System.out.println("     jmeter");
        System.out.println();
        System.out.println("  2. Create or open test plan");
        System.out.println("  3. Configure thread groups and HTTP requests");
        System.out.println("  4. Run test and view results");
        System.out.println();
        System.out.println("\nLoad Test Scenarios:");
        System.out.println("  - Order Flow Load Test: 1000 concurrent users");
        System.out.println("  - Sustained Load Test: 100 users for 10 minutes");
        System.out.println("  - Spike Test: Sudden spike to 500 users");
        System.out.println("=".repeat(80));
    }
}
