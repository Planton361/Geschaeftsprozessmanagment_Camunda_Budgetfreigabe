package org.camunda.bpm.getstarted.budget;

import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;

import java.util.logging.Logger;

public class BudgetPaymentWorker {

    private static final Logger LOGGER = Logger.getLogger(BudgetPaymentWorker.class.getName());

    public static void main(String[] args) {
        // External Task Client konfigurieren
        ExternalTaskClient client = ExternalTaskClient.create()
                .baseUrl("http://localhost:8080/engine-rest") // URL der Camunda Engine
                .asyncResponseTimeout(10000) // Long-Polling Timeout
                .build();

        // Auf das Topic 'process-payment' subscriben
        client.subscribe("process-payment")
                .lockDuration(5000) // Lock-Dauer in ms
                .handler((ExternalTask externalTask, ExternalTaskService externalTaskService) -> {
                    // Prozessvariablen abrufen
                    String antragsteller = externalTask.getVariable("antragsteller"); // Antragsteller

                    // Betrag sicher konvertieren
                    Object budgetAmountObject = externalTask.getVariable("budgetAmount");
                    Double budgetAmount = null;
                    if (budgetAmountObject instanceof Integer) {
                        budgetAmount = ((Integer) budgetAmountObject).doubleValue();
                    } else if (budgetAmountObject instanceof Double) {
                        budgetAmount = (Double) budgetAmountObject;
                    } else {
                        externalTaskService.handleFailure(
                                externalTask,
                                "Ungültiger Betrag",
                                "Die Variable 'budgetAmount' muss vom Typ Integer oder Double sein.",
                                0,
                                0
                        );
                        return;
                    }

                    LOGGER.info("Starte Auszahlung...");
                    LOGGER.info("Antragsteller: " + antragsteller);
                    LOGGER.info("Betrag: " + budgetAmount + " EUR");

                    // Nachricht generieren
                    try {
                        generatePaymentMessage(antragsteller, budgetAmount);
                        LOGGER.info("Nachricht erfolgreich generiert.");
                    } catch (Exception e) {
                        LOGGER.severe("Fehler beim Generieren der Nachricht: " + e.getMessage());
                        externalTaskService.handleFailure(
                                externalTask,
                                "Nachrichtenerstellung fehlgeschlagen",
                                e.getMessage(),
                                3,
                                2000
                        );
                        return;
                    }

                    // External Task als abgeschlossen markieren
                    externalTaskService.complete(externalTask);
                    LOGGER.info("External Task 'process-payment' abgeschlossen.");
                })
                .open();
    }

    /**
     * Generiert eine Auszahlungsmeldung.
     *
     * @param antragsteller Antragsteller
     * @param amount        Betrag
     * @throws Exception Wenn ein Fehler auftritt
     */
    private static void generatePaymentMessage(String antragsteller, Double amount) throws Exception {
        if (antragsteller == null || antragsteller.isEmpty()) {
            throw new Exception("Antragsteller ist ungültig.");
        }

        if (amount == null || amount <= 0) {
            throw new Exception("Betrag ist ungültig.");
        }

        // Simulierte Nachricht
        LOGGER.info("Der Betrag von " + amount + " EUR wird an " + antragsteller + " ausgezahlt.");
    }
}
