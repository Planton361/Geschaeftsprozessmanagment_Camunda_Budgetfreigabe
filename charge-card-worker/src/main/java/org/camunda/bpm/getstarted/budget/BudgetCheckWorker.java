package org.camunda.bpm.getstarted.budget;

import org.camunda.bpm.client.ExternalTaskClient;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class BudgetCheckWorker {

    private static final Logger logger = LoggerFactory.getLogger(BudgetCheckWorker.class);

    public static void main(String[] args) {
        logger.info("Initializing BudgetCheckWorker...");

        // Initialize Camunda External Task Client
        ExternalTaskClient client = ExternalTaskClient.create()
                .baseUrl("http://localhost:8080/engine-rest")
                .asyncResponseTimeout(10000)
                .workerId("budget-check-worker")
                .build();

        // Initialize Drools KIE services
        KieServices kieServices = KieServices.Factory.get();
        KieContainer kieContainer = kieServices.getKieClasspathContainer();

        StatelessKieSession kieSession;
        try {
            kieSession = kieContainer.newStatelessKieSession("BudgetSession");
        } catch (Exception e) {
            logger.error("Failed to initialize KIE session 'BudgetSession': Check kmodule.xml and DRL rules.", e);
            return;
        }

        // Add listener to log rule execution
        kieSession.addEventListener(new DefaultAgendaEventListener() {
            @Override
            public void afterMatchFired(AfterMatchFiredEvent event) {
                logger.debug("Rule fired: " + event.getMatch().getRule().getName());
            }
        });

        // Set global variables in the session
        try {
            kieSession.setGlobal("LOW_BUDGET_THRESHOLD", 5000);
            kieSession.setGlobal("MEDIUM_BUDGET_THRESHOLD", 10000);
            kieSession.setGlobal("logger", logger);
        } catch (Exception e) {
            logger.error("Error setting global variables for KIE session.", e);
            return;
        }

        // Subscribe to the Camunda topic
        client.subscribe("check-budget")
                .lockDuration(10000)
                .handler((externalTask, externalTaskService) -> {
                    try {
                        // Extract variables from the external task
                        Object budgetAmountObject = externalTask.getVariable("budgetAmount");
                        Double budgetAmount = validateAndConvertAmount(budgetAmountObject);

                        BudgetCheck budgetCheck = new BudgetCheck();
                        budgetCheck.setAmount(budgetAmount);
                        logger.info("BudgetCheck before Drools execution: amount={}, decision={}",
                                budgetCheck.getAmount(), budgetCheck.getDecision());

                        logger.info("Inserting budget check fact into Drools session...");
                        kieSession.execute(budgetCheck);

                        // Retrieve and validate the decision
                        String decision = budgetCheck.getDecision();
                        System.out.println("Decision Value: " + decision);
                        if (decision == null || decision.isEmpty()) {
                            throw new IllegalStateException("Decision was not set by Drools rules.");
                        }

                        // Complete the task with the decision
                        externalTaskService.complete(externalTask, Map.of(
                                "decision", budgetCheck.getDecision(), // decision wird hier an Camunda übergeben
                                "budgetAmount", budgetAmount
                        ));




                        logger.info("Budget decision processed: {}", decision);
                    } catch (Exception e) {
                        logger.error("Error processing budget check task", e);
                        externalTaskService.handleFailure(
                                externalTask,
                                e.getMessage(),
                                e.getLocalizedMessage(),
                                0,
                                1000
                        );
                    }
                })
                .open();

        logger.info("BudgetCheckWorker started and listening to 'check-budget' topic...");
    }

    /**
     * Validates and converts the budgetAmount variable.
     */
    private static Double validateAndConvertAmount(Object budgetAmountObject) {
        if (budgetAmountObject instanceof Integer) {
            return ((Integer) budgetAmountObject).doubleValue();
        } else if (budgetAmountObject instanceof Double) {
            return (Double) budgetAmountObject;
        } else {
            throw new IllegalArgumentException("Invalid or missing 'budgetAmount': " + budgetAmountObject);
        }
    }
}