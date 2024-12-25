package org.camunda.bpm.getstarted.budget;

import org.camunda.bpm.client.ExternalTaskClient;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;

import java.io.File;
import java.io.IOException;

public class BudgetDocumentationWorker {

    public static void main(String[] args) {
        // External Task Client konfigurieren
        ExternalTaskClient client = ExternalTaskClient.create()
                .baseUrl("http://localhost:8080/engine-rest") // Camunda-Engine-URL
                .asyncResponseTimeout(10000) // Long-Polling Timeout
                .build();

        // Topic abonnieren
        client.subscribe("create-budget-doc")
                .lockDuration(10000) // Lock-Dauer in ms
                .handler((externalTask, externalTaskService) -> {
                    // Prozessvariablen abrufen
                    String antragId = externalTask.getVariable("antragId");
                    String antragsteller = externalTask.getVariable("antragsteller");

                    // Sichere Konvertierung von budgetAmount
                    Double budgetAmount = null;
                    Object budgetAmountObject = externalTask.getVariable("budgetAmount");
                    if (budgetAmountObject instanceof Integer) {
                        budgetAmount = ((Integer) budgetAmountObject).doubleValue();
                    } else if (budgetAmountObject instanceof Double) {
                        budgetAmount = (Double) budgetAmountObject;
                    } else {
                        externalTaskService.handleFailure(
                                externalTask,
                                "Invalid budgetAmount",
                                "Variable budgetAmount must be Integer or Double",
                                0,
                                0
                        );
                        return;
                    }

                    String decision = externalTask.getVariable("budgetDecision");

                    // Dokumentationspfad definieren
                    String filePath = "BudgetDocumentation_" + antragId + ".pdf";

                    // Dokument erstellen
                    try {
                        createBudgetDocument(filePath, antragId, antragsteller, budgetAmount, decision);
                        System.out.println("Budgetdokumentation erstellt: " + filePath);
                    } catch (IOException e) {
                        System.err.println("Fehler beim Erstellen der Budgetdokumentation: " + e.getMessage());
                        externalTaskService.handleFailure(externalTask, "Dokumenterstellung fehlgeschlagen", e.getMessage(), 0, 0);
                        return;
                    }

                    // Aufgabe abschließen
                    externalTaskService.complete(externalTask);
                })
                .open();

    }

    private static void createBudgetDocument(String filePath, String antragId, String antragsteller, Double budgetAmount, String decision) throws IOException {
        // PDF-Schreiber initialisieren
        PdfWriter writer = new PdfWriter(new File(filePath));
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        // Dokumentinhalte hinzufügen
        document.add(new Paragraph("Budgetdokumentation"));
        document.add(new Paragraph("Antrag-ID: " + antragId));
        document.add(new Paragraph("Antragsteller: " + antragsteller));
        document.add(new Paragraph("Betrag: " + budgetAmount + " EUR"));
        document.add(new Paragraph("Entscheidung: " + decision));
        document.add(new Paragraph("Datum: " + java.time.LocalDate.now()));

        // Dokument schließen
        document.close();
    }
}

