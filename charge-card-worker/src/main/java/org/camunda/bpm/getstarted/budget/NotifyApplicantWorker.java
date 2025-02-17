package org.camunda.bpm.getstarted.budget;

import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Worker für das External Task Topic 'notify-applicant'.
 */
public class NotifyApplicantWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotifyApplicantWorker.class);

    public static void main(String[] args) {
        // SMTP-Konfiguration über Umgebungsvariablen (sicherer Ansatz)
        // SMTP-Daten aus Umgebungsvariablen auslesen
        String smtpHost = "smtp.gmail.com";
        String smtpPort = "587";
        String smtpUser = "antonplatonov93@gmail.com";
        String smtpPass = "kubu ramp evpx imwv";
        String fromEmail = "antonplatonov93@gmail.com";



        if (smtpHost == null || smtpPort == null || smtpUser == null || smtpPass == null || fromEmail == null) {
            LOGGER.error("SMTP-Konfiguration ist unvollständig. Setze alle erforderlichen Umgebungsvariablen.");
            System.exit(1);
        }

        // External Task Client konfigurieren
        ExternalTaskClient client = ExternalTaskClient.create()
                .baseUrl("http://localhost:8080/engine-rest")
                .asyncResponseTimeout(10000) // Long-Polling Timeout
                .build();

        // Auf das Topic 'notify-applicant' subscriben
        client.subscribe("notify-applicant")
                .lockDuration(5000) // Lockdauer in ms
                .handler((externalTask, externalTaskService) -> {
                    // Prozessvariablen auslesen
                    try {
                        String antragID = externalTask.getVariable("antragID");
                        String antragstellerEmail = externalTask.getVariable("antragstellerEmail");
                        String decision = externalTask.getVariable("budgetDecision");
                        String budgetAmount = externalTask.getVariable("budgetAmount").toString();

                        if (antragstellerEmail == null || antragstellerEmail.isEmpty()) {
                            throw new IllegalArgumentException("Die Antragsteller-E-Mail-Adresse fehlt.");
                        }

                        // Betreff und Nachricht erstellen
                        String subject = "Aktualisierung zu Ihrem Budgetantrag";
                        String messageContent = generateEmailContent(antragID, budgetAmount, decision);

                        // E-Mail senden
                        sendEmail(smtpHost, smtpPort, smtpUser, smtpPass, fromEmail, antragstellerEmail, subject, messageContent);
                        LOGGER.info("Benachrichtigungs-E-Mail erfolgreich an {} gesendet.", antragstellerEmail);

                        // External Task als abgeschlossen markieren
                        externalTaskService.complete(externalTask);
                    } catch (Exception e) {
                        LOGGER.error("Fehler beim Prozessieren des Tasks: {}", e.getMessage());
                        externalTaskService.handleFailure(externalTask, "Fehler beim Schreiben einer E-Mail", e.getMessage(), 3, 2000);
                    }
                })
                .open();

        LOGGER.info("NotifyApplicantWorker gestartet...");
    }

    private static String generateEmailContent(String antragID, String budgetAmount, String antragsteller) {
        return "Sehr geehrte/r "+ antragsteller + ",\n\n" +
                "wir möchten Sie über den Status Ihres Budgetantrags informieren.\n\n" +
                "Antragsdetails:\n" +
                "- Antrag-ID: " + antragID + "\n" +
                "- Betrag: " + budgetAmount + " EUR\n" +
                "- Status: genehmigt \n" +
                "Vielen Dank für Ihr Vertrauen.\n\n" +
                "Mit freundlichen Grüßen,\n" +
                "Ihr Budget-Team";
    }

    private static void sendEmail(String host, String port, String user, String pass,
                                  String fromEmail, String toEmail, String subject, String content) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.debug", "true"); // Debugging-Informationen aktivieren

        // Authentifizierung
        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, pass);
            }
        });

        // E-Mail erstellen und senden
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromEmail));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        message.setSubject(subject);
        message.setText(content);

        Transport.send(message);
    }
}