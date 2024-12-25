package org.camunda.bpm.getstarted.budget;

import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Worker für das External Task Topic 'budget-ablehnen'.
 */
public class BudgetRejectionWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(BudgetRejectionWorker.class);

    public static void main(String[] args) {
        // SMTP-Konfiguration über Umgebungsvariablen (sicherer)
        String smtpHost = System.getenv("SMTP_HOST") != null ? System.getenv("SMTP_HOST") : "smtp.gmail.com";
        String smtpPort = System.getenv("SMTP_PORT") != null ? System.getenv("SMTP_PORT") : "587";
        String smtpUser = System.getenv("SMTP_USER");
        String smtpPass = System.getenv("SMTP_PASS");
        String fromEmail = System.getenv("FROM_EMAIL");


        // Zieladresse für den Test
        String toEmail = "antonplatonov93@gmail.com"; // <--- Stellen Sie sicher, dass dies definiert ist.

        if (smtpHost == null || smtpPort == null || smtpUser == null || smtpPass == null || fromEmail == null) {
            LOGGER.error("SMTP-Konfiguration ist nicht vollständig. Bitte setze alle notwendigen Umgebungsvariablen.");
            System.exit(1);
        }

        // 1) External Task Client konfigurieren
        ExternalTaskClient client = ExternalTaskClient.create()
                .baseUrl("http://localhost:8080/engine-rest") // URL deiner Camunda Engine
                .asyncResponseTimeout(10000) // Long-Polling Timeout in ms
                //.authentication("username", "password") // Falls Authentifizierung nötig ist
                .build();

        // 2) Auf das Topic 'budget-ablehnen' subscriben
        client.subscribe("budget-ablehnen")
                .lockDuration(5000) // Lock-Dauer in ms
                .handler((ExternalTask externalTask, ExternalTaskService externalTaskService) -> {
                    // Prozessvariablen auslesen
                    String antragId = externalTask.getVariable("antragId"); // Antrag-ID aus Prozessvariable
                    String ablehnungsGrund = externalTask.getVariable("reason"); // Ablehnungsgrund aus Prozessvariable
                    String antragstellerEmail = externalTask.getVariable("antragstellerEmail"); // E-Mail des Antragstellers
                    String subject = "Ihr Budgetantrag wurde abgelehnt"; // Fester Betreff

                    LOGGER.info("Starte Budget-Ablehnung für Antrag-ID: {} mit Grund: {}", antragId, ablehnungsGrund);

                    // E-Mail senden
                    try {
                        sendRejectionEmail(smtpHost, smtpPort, smtpUser, smtpPass, fromEmail, antragstellerEmail, subject, antragId, ablehnungsGrund);
                        LOGGER.info("Ablehnungs-E-Mail erfolgreich an {} gesendet.", antragstellerEmail);
                    } catch (Exception e) {
                        LOGGER.error("Fehler beim Senden der Ablehnungs-E-Mail: {}", e.getMessage());
                        externalTaskService.handleFailure(externalTask, "E-Mail Versand fehlgeschlagen", e.getMessage(), 3, 2000);
                        return;
                    }

                    // External Task als erledigt markieren
                    externalTaskService.complete(externalTask);
                    LOGGER.info("External Task 'budget-ablehnen' für Antrag-ID: {} abgeschlossen.", antragId);
                })
                .open();



        LOGGER.info("BudgetRejectionWorker gestartet und hört auf 'budget-ablehnen' Tasks...");
    }

    /**
     * Methode zum Senden der Ablehnungs-E-Mail.
     *
     * @param host      SMTP-Host
     * @param port      SMTP-Port
     * @param user      SMTP-Benutzername
     * @param pass      SMTP-Passwort
     * @param fromEmail Absender-E-Mail
     * @param toEmail   Empfänger-E-Mail
     * @param subject   E-Mail Betreff
     * @param antragId  Antrag-ID
     * @param reason    Ablehnungsgrund
     * @throws MessagingException
     */
    private static void sendRejectionEmail(String host, String port, String user, String pass,
                                           String fromEmail, String toEmail, String subject,
                                           String antragId, String reason) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true"); // Falls dein SMTP-Server TLS unterstützt
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);

        // Authentifizierung
        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, pass);
            }
        });

        // Nachricht erstellen
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromEmail));
        message.setRecipients(
                Message.RecipientType.TO, InternetAddress.parse(toEmail));
        message.setSubject(subject);
        message.setText("Sehr geehrte/r Antragsteller/in,\n\n" +
                "leider müssen wir Ihnen mitteilen, dass Ihr Budgetantrag mit der ID " +
                antragId + " abgelehnt wurde.\n" +
                "Grund der Ablehnung: " + reason + "\n\n" +
                "Für Rückfragen stehen wir Ihnen gerne zur Verfügung.\n\n" +
                "Mit freundlichen Grüßen,\n" +
                "Ihr Team");

        // E-Mail senden
        Transport.send(message);
    }
}
