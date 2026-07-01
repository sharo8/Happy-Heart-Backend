package com.happyhearts.service;

import com.happyhearts.config.AuthOtpProperties;
import com.happyhearts.config.MailBrandingProperties;
import com.happyhearts.config.SendGridProperties;
import com.happyhearts.dto.response.EmailDeliveryResult;
import com.happyhearts.enums.Language;
import com.happyhearts.enums.NotificationStatus;
import com.happyhearts.enums.NotificationType;
import com.happyhearts.enums.ScanType;
import com.happyhearts.model.Branch;
import com.happyhearts.model.EmailNotification;
import com.happyhearts.model.AttendanceExcuse;
import com.happyhearts.model.Employee;
import com.happyhearts.model.GracePeriodRequest;
import com.happyhearts.model.User;
import com.happyhearts.repository.BranchRepository;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.exception.BusinessException;
import com.happyhearts.repository.EmailNotificationRepository;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import com.happyhearts.util.EmailHtmlTemplates;
import com.happyhearts.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final SendGridProperties sendGridProperties;
    private final AuthOtpProperties authOtpProperties;
    private final MailBrandingProperties mailBrandingProperties;
    private final EmailNotificationRepository emailNotificationRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final BranchRepository branchRepository;
    private final EmployeeRepository employeeRepository;

    public String getBrandLogoUrl() {
        return mailBrandingProperties.getBrandLogoUrl();
    }

    /**
     * Sends login OTP immediately (same thread) so failures surface to the caller.
     */
    public void sendLoginOtp(User user, String plainOtp) {
        Language lang = user.getPreferredLanguage();
        String subject = switch (lang) {
            case EN -> "Happy Hearts – your login verification code";
            case FR -> "Happy Hearts – votre code de connexion";
            case KI -> "Happy Hearts – kode yo kwinjira";
        };
        String body = switch (lang) {
            case EN -> """
                    Hello,

                    Your one-time verification code is: %s

                    It expires in %d minutes. If you did not try to sign in, ignore this email.

                    — Happy Hearts System
                    """.formatted(plainOtp, authOtpProperties.getOtpExpirationMinutes());
            case FR -> """
                    Bonjour,

                    Votre code de vérification à usage unique est : %s

                    Il expire dans %d minutes. Si vous n'avez pas tenté de vous connecter, ignorez ce message.

                    — Happy Hearts System
                    """.formatted(plainOtp, authOtpProperties.getOtpExpirationMinutes());
            case KI -> """
                    Muraho,

                    Kode yawe yo kwemeza ni: %s

                    Irarangira mu minota %d. Niba utagerageje kwinjira, wirengagize ubu butumwa.

                    — Happy Hearts System
                    """.formatted(plainOtp, authOtpProperties.getOtpExpirationMinutes());
        };
        String html = EmailHtmlTemplates.loginOtpHtml(
                lang,
                plainOtp,
                authOtpProperties.getOtpExpirationMinutes(),
                mailBrandingProperties.getBrandLogoUrl()
        );
        if (persistAndSend(user.getEmail(), subject, body, html, lang, NotificationType.LOGIN_OTP) != NotificationStatus.SENT) {
            throw new BusinessException("error.auth.otp.email.failed");
        }
    }

    /**
     * Welcome + first password setup link. Content language matches {@link User#getPreferredLanguage()}.
     */
    public void sendWelcomeAndPasswordSetup(User user, String rawSetupToken) {
        String base = authOtpProperties.getFrontendBaseUrl().replaceAll("/$", "");
        String link = base + "/auth/set-password?token=" + URLEncoder.encode(rawSetupToken, StandardCharsets.UTF_8);
        int hours = authOtpProperties.getSetupTokenExpirationHours();
        Language lang = user.getPreferredLanguage();
        String subject = switch (lang) {
            case EN -> "Happy Hearts – Your account is ready";
            case FR -> "Happy Hearts – Votre compte est prêt";
            case KI -> "Happy Hearts – Konti yawe itegetswe";
        };
        String body = switch (lang) {
            case EN -> """
                    Hello,

                    An administrator created your Happy Hearts dashboard account for: %s

                    Before you sign in (email, password, and one-time security code sent by email), set your password using this link:
                    %s

                    This link expires in %d hours. If it expires, ask your administrator for a new invitation.

                    — Happy Hearts
                    """.formatted(user.getEmail(), link, hours);
            case FR -> """
                    Bonjour,

                    Un administrateur a créé votre accès au tableau de bord Happy Hearts pour l'adresse : %s

                    Avant votre première connexion (e-mail, mot de passe, puis code de sécurité envoyé par e-mail), définissez votre mot de passe via ce lien :
                    %s

                    Ce lien expire dans %d heures. S'il expire, demandez à votre administrateur une nouvelle invitation.

                    — Happy Hearts
                    """.formatted(user.getEmail(), link, hours);
            case KI -> """
                    Muraho,

                    Umuyobozi yakuye konti yawe ya Happy Hearts kuri iyi imeli: %s

                    Mbere yo kwinjira (imeli, ijambo ry'ibanga, hanyuma kode y'umutekano), shyiraho ijambo ryawe ukoreshe iyi link:
                    %s

                    Iyi link irarangira mu masaha %d. Nirarangira, saba umuyobozi kongeza ubutumwa.

                    — Happy Hearts
                    """.formatted(user.getEmail(), link, hours);
        };
        String html = EmailHtmlTemplates.welcomeUserSetupHtml(
                lang,
                user.getEmail(),
                link,
                hours,
                mailBrandingProperties.getBrandLogoUrl()
        );
        persistAndSend(user.getEmail(), subject, body, html, lang, NotificationType.WELCOME_USER);
    }

    public void sendPasswordResetLink(User user, String rawResetToken) {
        String base = authOtpProperties.getFrontendBaseUrl().replaceAll("/$", "");
        String link = base + "/reset-password?token=" + URLEncoder.encode(rawResetToken, StandardCharsets.UTF_8);
        String subject = switch (user.getPreferredLanguage()) {
            case EN -> "Happy Hearts - Password reset";
            case FR -> "Happy Hearts - Reinitialisation du mot de passe";
            case KI -> "Happy Hearts - Gusubizaho ijambo ry'ibanga";
        };
        String body = switch (user.getPreferredLanguage()) {
            case EN -> """
                    Hello,

                    We received a password reset request for your Happy Hearts account.
                    Reset your password using this secure link:
                    %s

                    If you did not request this, ignore this email.
                    """.formatted(link);
            case FR -> """
                    Bonjour,

                    Nous avons recu une demande de reinitialisation de mot de passe.
                    Reinitialisez votre mot de passe avec ce lien securise :
                    %s

                    Si vous n'etes pas a l'origine de cette demande, ignorez cet e-mail.
                    """.formatted(link);
            case KI -> """
                    Muraho,

                    Twakiriye ubusabe bwo gusubizaho ijambo ry'ibanga rya konti yawe.
                    Koresha iyi link itekanye:
                    %s

                    Niba atari wowe wabikoze, wirengagize iyi imeli.
                    """.formatted(link);
        };
        String html = EmailHtmlTemplates.passwordResetHtml(
                user.getPreferredLanguage(),
                link,
                mailBrandingProperties.getBrandLogoUrl()
        );
        persistAndSend(user.getEmail(), subject, body, html, user.getPreferredLanguage(), NotificationType.PASSWORD_RESET);
    }

    @Async
    public void sendLateAlertToManager(User manager, Employee employee, Branch branch, LocalDate date) {
        Language lang = manager.getPreferredLanguage();
        String dateStr = date.format(DATE_FMT);
        String subject = switch (lang) {
            case EN -> "Late arrival notice - " + employee.getFirstName() + " " + employee.getLastName() + " - " + dateStr;
            case FR -> "Retard - " + employee.getFirstName() + " " + employee.getLastName() + " - " + dateStr;
            case KI -> "Kuba mu isaha - " + employee.getFirstName() + " " + employee.getLastName() + " - " + dateStr;
        };
        String body = switch (lang) {
            case EN -> employee.getFirstName() + " " + employee.getLastName()
                    + " checked in late at " + branch.getName() + ".";
            case FR -> employee.getFirstName() + " " + employee.getLastName()
                    + " s'est enregistré(e) en retard à " + branch.getName() + ".";
            case KI -> employee.getFirstName() + " " + employee.getLastName()
                    + " yinjiye mu isaha kuri " + branch.getName() + ".";
        };
        persistAndSend(manager.getEmail(), subject, body, null, lang, NotificationType.LATE_ALERT);
    }

    @Async
    public void sendLateAlert(Employee employee, Branch branch, LocalDate date) {
        Language lang = employee.getPreferredLanguage();
        String dateStr = date.format(DATE_FMT);
        String subject = switch (lang) {
            case EN -> "You arrived late today - " + dateStr;
            case FR -> "Vous êtes arrivé(e) en retard aujourd'hui - " + dateStr;
            case KI -> "Wageze guhera mu isaha - " + dateStr;
        };
        String body = switch (lang) {
            case EN -> "Hello " + employee.getFirstName() + ", your entry at " + branch.getName()
                    + " was recorded after the allowed time.";
            case FR -> "Bonjour " + employee.getFirstName() + ", votre entrée à " + branch.getName()
                    + " a été enregistrée après l'heure autorisée.";
            case KI -> "Muraho " + employee.getFirstName() + ", kwinjira kwa muri " + branch.getName()
                    + " kwanditswe nyuma y'igihe cyemewe.";
        };
        String to = resolveEmployeeEmail(employee);
        if (!StringUtils.hasText(to)) {
            return;
        }
        persistAndSend(to, subject, body, null, lang, NotificationType.LATE_ALERT);
    }

    @Async
    public void sendAttendanceScanConfirmation(Employee employee, Branch branch, Instant scannedAt, ScanType scanType) {
        String to = resolveEmployeeEmail(employee);
        if (!StringUtils.hasText(to)) {
            log.debug("No email for employee {} — skip attendance confirmation", employee.getId());
            return;
        }
        Language lang = employee.getPreferredLanguage() != null ? employee.getPreferredLanguage() : Language.EN;
        String dateTime = formatKigaliDateTime(scannedAt);
        String dateOnly = TimeUtils.toKigaliDate(scannedAt).format(DATE_FMT);
        String branchName = branch != null ? branch.getName() : "Happy Hearts";
        String name = employee.getFirstName() + " " + employee.getLastName();

        boolean checkIn = scanType == ScanType.ENTRY;
        String subject = switch (lang) {
            case EN -> checkIn
                    ? "Check-in confirmed — " + dateOnly
                    : "Check-out confirmed — " + dateOnly;
            case FR -> checkIn
                    ? "Entrée confirmée — " + dateOnly
                    : "Sortie confirmée — " + dateOnly;
            case KI -> checkIn
                    ? "Kwinjira kwemejwe — " + dateOnly
                    : "Gusohoka kwemejwe — " + dateOnly;
        };
        String body = switch (lang) {
            case EN -> checkIn
                    ? """
                    Hello %s,

                    Your check-in has been recorded successfully.

                    Branch: %s
                    Date & time: %s

                    Welcome! Have a great day.

                    — Happy Hearts Attendance System
                    """.formatted(name, branchName, dateTime)
                    : """
                    Hello %s,

                    Your check-out has been recorded successfully.

                    Branch: %s
                    Date & time: %s

                    Thank you for your work today.

                    — Happy Hearts Attendance System
                    """.formatted(name, branchName, dateTime);
            case FR -> checkIn
                    ? """
                    Bonjour %s,

                    Votre pointage d'entrée a bien été enregistré.

                    Site : %s
                    Date et heure : %s

                    Bonne journée !

                    — Système de présence Happy Hearts
                    """.formatted(name, branchName, dateTime)
                    : """
                    Bonjour %s,

                    Votre pointage de sortie a bien été enregistré.

                    Site : %s
                    Date et heure : %s

                    Merci pour votre journée de travail.

                    — Système de présence Happy Hearts
                    """.formatted(name, branchName, dateTime);
            case KI -> checkIn
                    ? """
                    Muraho %s,

                    Kwinjira kwawe kwanditswe neza.

                    Ishami: %s
                    Itariki n'isaha: %s

                    Umunsi mwiza!

                    — Sisitemu y'ubwitabire Happy Hearts
                    """.formatted(name, branchName, dateTime)
                    : """
                    Muraho %s,

                    Gusohoka kwawe kwanditswe neza.

                    Ishami: %s
                    Itariki n'isaha: %s

                    Urakoze ku musi wawe w'akazi.

                    — Sisitemu y'ubwitabire Happy Hearts
                    """.formatted(name, branchName, dateTime);
        };
        NotificationType type = checkIn ? NotificationType.ATTENDANCE_CHECK_IN : NotificationType.ATTENDANCE_CHECK_OUT;
        persistAndSend(to, subject, body, null, lang, type);
    }

    private String formatKigaliDateTime(Instant instant) {
        return DateTimeFormatter.ofPattern("EEEE dd MMM yyyy, HH:mm")
                .withZone(TimeUtils.kigali())
                .format(instant);
    }

    @Async
    public void sendAbsenceAlert(Employee employee, LocalDate date) {
        Language lang = employee.getPreferredLanguage();
        String dateStr = date.format(DATE_FMT);
        String subject = switch (lang) {
            case EN -> "Absence recorded today - " + dateStr;
            case FR -> "Absence enregistrée aujourd'hui - " + dateStr;
            case KI -> "Gutunga kwawe kwanditswe uno munsi - " + dateStr;
        };
        String body = switch (lang) {
            case EN -> "Hello " + employee.getFirstName() + ", no entry scan was recorded for you today by 10:00.";
            case FR -> "Bonjour " + employee.getFirstName() + ", aucune entrée n'a été enregistrée avant 10h00.";
            case KI -> "Muraho " + employee.getFirstName() + ", nta scan yo kwinjira yanditswe kugeza saa 10:00.";
        };
        String to = resolveEmployeeEmail(employee);
        if (!StringUtils.hasText(to)) {
            return;
        }
        persistAndSend(to, subject, body, null, lang, NotificationType.ABSENCE_ALERT);
    }

    @Async
    public void sendWelcome(Employee employee) {
        Language lang = employee.getPreferredLanguage();
        String subject = switch (lang) {
            case EN -> "Welcome to Happy Hearts System";
            case FR -> "Bienvenue dans le système Happy Hearts";
            case KI -> "Murakaza neza mu sisitemu ya Happy Hearts";
        };
        String body = switch (lang) {
            case EN -> "Hello " + employee.getFirstName() + ", your staff profile has been created.";
            case FR -> "Bonjour " + employee.getFirstName() + ", votre profil personnel a été créé.";
            case KI -> "Muraho " + employee.getFirstName() + ", umwirondoro wawe wakoze.";
        };
        String to = resolveEmployeeEmail(employee);
        if (!StringUtils.hasText(to)) {
            return;
        }
        String branchName = employee.getBranch() != null ? employee.getBranch().getName() : "";
        String html = EmailHtmlTemplates.employeeWelcomeHtml(
                lang,
                employee.getFirstName(),
                branchName,
                mailBrandingProperties.getBrandLogoUrl()
        );
        persistAndSend(to, subject, body, html, lang, NotificationType.WELCOME);
    }

    /**
     * Notifies branch lead teacher and second teacher (if set, with email) that a new employee was registered.
     * Each email uses the leader's {@link Employee#getPreferredLanguage()}.
     * Loads data inside a transaction to avoid lazy-loading issues in async execution.
     */
    @Async
    @Transactional
    public void notifyBranchLeadersNewEmployee(UUID newHireId, UUID branchId) {
        Employee newHire = employeeRepository.findWithBranchById(newHireId).orElse(null);
        Branch branch = branchRepository.findWithLeadersById(branchId).orElse(null);
        if (newHire == null || branch == null) {
            return;
        }
        String newName = (newHire.getFirstName() + " " + newHire.getLastName()).trim();
        Set<String> seenEmails = new HashSet<>();
        for (Employee leaderRef : List.of(branch.getLeadTeacher(), branch.getSecondTeacher())) {
            if (leaderRef == null) {
                continue;
            }
            if (leaderRef.getId().equals(newHireId)) {
                continue;
            }
            Employee leader = employeeRepository.findWithUserById(leaderRef.getId()).orElse(leaderRef);
            String to = resolveEmployeeEmail(leader);
            if (!StringUtils.hasText(to)) {
                continue;
            }
            String key = to.trim().toLowerCase();
            if (!seenEmails.add(key)) {
                continue;
            }
            Language lang = leader.getPreferredLanguage();
            String leaderFirst = StringUtils.hasText(leader.getFirstName()) ? leader.getFirstName().trim() : "";
            String subject = switch (lang) {
                case EN -> "Happy Hearts — new staff member at " + branch.getCode();
                case FR -> "Happy Hearts — nouveau membre (" + branch.getCode() + ")";
                case KI -> "Happy Hearts — umukozi mushya kuri " + branch.getCode();
            };
            String body = switch (lang) {
                case EN -> "Hello " + leaderFirst + ",\n\n" + newName + " has been added to the system for branch "
                        + branch.getCode() + " (" + branch.getName() + ").\n";
                case FR -> "Bonjour " + leaderFirst + ",\n\n" + newName + " a été ajouté(e) au système pour la branche "
                        + branch.getCode() + " (" + branch.getName() + ").\n";
                case KI -> "Muraho " + leaderFirst + ",\n\n" + newName + " yongewe muri sisitemu ku ishami "
                        + branch.getCode() + " (" + branch.getName() + ").\n";
            };
            String html = EmailHtmlTemplates.newEmployeeLeaderNoticeHtml(
                    lang,
                    leaderFirst,
                    newName,
                    branch.getCode(),
                    branch.getName(),
                    mailBrandingProperties.getBrandLogoUrl()
            );
            persistAndSend(to, subject, body, html, lang, NotificationType.NEW_EMPLOYEE_LEADER_NOTICE);
        }
    }

    @Async
    public void sendRfidAssigned(Employee employee) {
        Language lang = employee.getPreferredLanguage();
        String subject = switch (lang) {
            case EN -> "Your RFID card has been activated";
            case FR -> "Votre carte RFID a été activée";
            case KI -> "Ikarita yawe ya RFID yafunguwe";
        };
        String body = switch (lang) {
            case EN -> "Hello " + employee.getFirstName() + ", your RFID card is now active.";
            case FR -> "Bonjour " + employee.getFirstName() + ", votre carte RFID est maintenant active.";
            case KI -> "Muraho " + employee.getFirstName() + ", ikarita yawe ya RFID irakora ubu.";
        };
        String to = resolveEmployeeEmail(employee);
        if (!StringUtils.hasText(to)) {
            return;
        }
        persistAndSend(to, subject, body, null, lang, NotificationType.RFID_ASSIGNED);
    }

    @Async
    public void sendDailyReport(User manager, Branch branch, LocalDate date, List<String> present, List<String> absent, List<String> late) {
        Language lang = manager.getPreferredLanguage();
        String dateStr = date.format(DATE_FMT);
        String subject = switch (lang) {
            case EN -> "Happy Hearts [" + branch.getCode() + "] - Daily Attendance Report - " + dateStr;
            case FR -> "Happy Hearts [" + branch.getCode() + "] - Rapport de présence du " + dateStr;
            case KI -> "Happy Hearts [" + branch.getCode() + "] - Raporo y'ubwitange ya " + dateStr;
        };
        String body = buildDailyBody(lang, present, absent, late);
        persistAndSend(manager.getEmail(), subject, body, null, lang, NotificationType.DAILY_REPORT);
    }

    private String buildDailyBody(Language lang, List<String> present, List<String> absent, List<String> late) {
        return switch (lang) {
            case EN -> "Present:\n- " + String.join("\n- ", present)
                    + "\n\nAbsent:\n- " + String.join("\n- ", absent)
                    + "\n\nLate:\n- " + String.join("\n- ", late);
            case FR -> "Présents:\n- " + String.join("\n- ", present)
                    + "\n\nAbsents:\n- " + String.join("\n- ", absent)
                    + "\n\nEn retard:\n- " + String.join("\n- ", late);
            case KI -> "Bahari:\n- " + String.join("\n- ", present)
                    + "\n\nBadahari:\n- " + String.join("\n- ", absent)
                    + "\n\nBagereje:\n- " + String.join("\n- ", late);
        };
    }

    private String resolveEmployeeEmail(Employee employee) {
        if (StringUtils.hasText(employee.getEmail())) {
            return employee.getEmail();
        }
        if (employee.getUser() != null && StringUtils.hasText(employee.getUser().getEmail())) {
            return employee.getUser().getEmail();
        }
        return null;
    }

    /**
     * Broadcast announcement to one dashboard user; subject/body language follows {@link User#getPreferredLanguage()}.
     * When {@code optionalSubjectOverride} is blank, a localized default subject is used.
     */
    public void sendAnnouncementEmail(User user, String optionalSubjectOverride, String title, String bodyPlain) {
        Language lang = user.getPreferredLanguage();
        String subject = StringUtils.hasText(optionalSubjectOverride)
                ? optionalSubjectOverride
                : switch (lang) {
                    case EN -> "Happy Hearts — Announcement: " + title;
                    case FR -> "Happy Hearts — Annonce : " + title;
                    case KI -> "Happy Hearts — Itangazo: " + title;
                };
        String html = EmailHtmlTemplates.announcementEmailHtml(
                lang,
                title,
                bodyPlain,
                mailBrandingProperties.getBrandLogoUrl()
        );
        persistAndSend(user.getEmail(), subject, bodyPlain, html, lang, NotificationType.ANNOUNCEMENT);
    }

    /**
     * Generic portal email (feedback copy, internal message copy) with optional CC (e.g. supervisors).
     */
    @Transactional
    public NotificationStatus sendPortalNotificationEmail(
            String to,
            List<String> carbonCopy,
            String subject,
            String plainBody,
            String htmlBody,
            Language lang,
            NotificationType type
    ) {
        List<String> cc = new ArrayList<>();
        if (carbonCopy != null) {
            for (String c : carbonCopy) {
                if (StringUtils.hasText(c) && !c.equalsIgnoreCase(to)) {
                    cc.add(c.trim());
                }
            }
        }
        return persistAndSend(to, cc, subject, plainBody, htmlBody, lang, type);
    }

    /**
     * Professional staff email from the Emails tab — real SMTP/SendGrid delivery with Reply-To set to the sender.
     * Uses the same From header as other portal emails (Happy Hearts System) for reliable Gmail delivery.
     */
    @Transactional
    public EmailDeliveryResult sendProfessionalStaffEmail(
            User sender,
            String to,
            List<String> carbonCopy,
            String subject,
            String bodyPlain,
            Language lang
    ) {
        Language effectiveLang = lang != null ? lang : Language.FR;
        String senderName = emailUserLabel(sender);
        String outboundSubject = formatStaffEmailSubject(effectiveLang, senderName, subject);
        String signature = switch (effectiveLang) {
            case EN -> "Best regards,\n" + senderName + "\nHappy Hearts System";
            case KI -> "Muraho neza,\n" + senderName + "\nHappy Hearts System";
            case FR -> "Cordialement,\n" + senderName + "\nHappy Hearts System";
        };
        String intro = switch (effectiveLang) {
            case EN -> "You received a message from " + senderName + " (" + sender.getEmail() + ") via Happy Hearts:\n\n";
            case KI -> "Wakiriye ubutumwa bwa " + senderName + " (" + sender.getEmail() + ") binyuze muri Happy Hearts:\n\n";
            case FR -> "Vous avez reçu un message de " + senderName + " (" + sender.getEmail() + ") via Happy Hearts :\n\n";
        };
        String plain = intro + bodyPlain + "\n\n" + signature;
        String html = EmailHtmlTemplates.professionalStaffEmailHtml(
                effectiveLang,
                subject,
                intro.replace("\n\n", "") + "\n\n" + bodyPlain,
                signature,
                getBrandLogoUrl()
        );
        List<String> cc = new ArrayList<>();
        if (carbonCopy != null) {
            for (String c : carbonCopy) {
                if (StringUtils.hasText(c) && !c.equalsIgnoreCase(to)) {
                    cc.add(c.trim());
                }
            }
        }
        String provider = StringUtils.hasText(sendGridProperties.getApiKey()) ? "SENDGRID" : "SMTP";
        log.info(
                "[staff-email] Preparing send provider={} from={} replyTo={} to={} subject={}",
                provider,
                sendGridProperties.getFromEmail(),
                sender.getEmail(),
                to,
                outboundSubject
        );
        EmailDeliveryResult result = persistAndSendWithReplyTo(
                to,
                cc,
                outboundSubject,
                plain,
                html,
                effectiveLang,
                NotificationType.STAFF_EMAIL,
                sender.getEmail(),
                null
        );
        if (result.isSent()) {
            log.info(
                    "[staff-email] SENT notificationId={} provider={} to={} subject={}",
                    result.getNotificationId(),
                    result.getProvider(),
                    to,
                    outboundSubject
            );
        } else {
            log.error(
                    "[staff-email] FAILED notificationId={} provider={} to={} subject={} error={}",
                    result.getNotificationId(),
                    result.getProvider(),
                    to,
                    outboundSubject,
                    result.getErrorMessage()
            );
        }
        return result;
    }

    private static String formatStaffEmailSubject(Language lang, String senderName, String subject) {
        String trimmed = subject != null ? subject.trim() : "";
        String safeSender = senderName != null ? senderName.trim() : "";
        if (trimmed.regionMatches(true, 0, "Happy Hearts", 0, "Happy Hearts".length())) {
            return trimmed;
        }
        return switch (lang) {
            case EN -> "Happy Hearts \u2013 Message from " + safeSender + ": " + trimmed;
            case FR -> "Happy Hearts \u2013 Message de " + safeSender + " : " + trimmed;
            case KI -> "Happy Hearts \u2013 Ubutumwa bwa " + safeSender + ": " + trimmed;
        };
    }

    private static String emailUserLabel(User u) {
        String fn = u.getFirstName() != null ? u.getFirstName().trim() : "";
        String ln = u.getLastName() != null ? u.getLastName().trim() : "";
        String both = (fn + " " + ln).trim();
        return both.isEmpty() ? u.getEmail() : both;
    }

    private EmailDeliveryResult persistAndSendWithReplyTo(
            String to,
            List<String> carbonCopy,
            String subject,
            String body,
            String htmlBody,
            Language lang,
            NotificationType type,
            String replyTo,
            String fromDisplayName
    ) {
        String provider = StringUtils.hasText(sendGridProperties.getApiKey()) ? "SENDGRID" : "SMTP";
        EmailNotification row = EmailNotification.builder()
                .recipientEmail(to)
                .subject(subject)
                .body(body)
                .language(lang)
                .notificationType(type)
                .build();
        row = emailNotificationRepository.save(row);
        try {
            if ("SENDGRID".equals(provider)) {
                sendWithSendGrid(to, carbonCopy, subject, body, htmlBody, replyTo, fromDisplayName);
            } else {
                sendWithSmtp(to, carbonCopy, subject, body, htmlBody, replyTo, fromDisplayName);
            }
            row.setStatus(NotificationStatus.SENT);
            row.setSentAt(Instant.now());
            emailNotificationRepository.save(row);
            return EmailDeliveryResult.builder()
                    .status(NotificationStatus.SENT)
                    .notificationId(row.getId())
                    .provider(provider)
                    .recipientEmail(to)
                    .outboundSubject(subject)
                    .build();
        } catch (Exception e) {
            log.error("[staff-email] SMTP/SendGrid exception to={} subject={}", to, subject, e);
            row.setStatus(NotificationStatus.FAILED);
            emailNotificationRepository.save(row);
            return EmailDeliveryResult.builder()
                    .status(NotificationStatus.FAILED)
                    .notificationId(row.getId())
                    .provider(provider)
                    .recipientEmail(to)
                    .outboundSubject(subject)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private NotificationStatus persistAndSend(
            String to,
            String subject,
            String body,
            String htmlBody,
            Language lang,
            NotificationType type
    ) {
        return persistAndSend(to, List.of(), subject, body, htmlBody, lang, type);
    }

    private NotificationStatus persistAndSend(
            String to,
            List<String> carbonCopy,
            String subject,
            String body,
            String htmlBody,
            Language lang,
            NotificationType type
    ) {
        EmailNotification row = EmailNotification.builder()
                .recipientEmail(to)
                .subject(subject)
                .body(body)
                .language(lang)
                .notificationType(type)
                .build();
        row = emailNotificationRepository.save(row);
        try {
            if (StringUtils.hasText(sendGridProperties.getApiKey())) {
                sendWithSendGrid(to, carbonCopy, subject, body, htmlBody, null);
            } else {
                sendWithSmtp(to, carbonCopy, subject, body, htmlBody, null);
            }
            row.setStatus(NotificationStatus.SENT);
            row.setSentAt(Instant.now());
        } catch (Exception e) {
            log.error("Failed to send email to {}", to, e);
            row.setStatus(NotificationStatus.FAILED);
        }
        emailNotificationRepository.save(row);
        return row.getStatus();
    }

    private void sendWithSendGrid(String to, List<String> carbonCopy, String subject, String plainText, String htmlBody, String replyTo)
            throws IOException {
        sendWithSendGrid(to, carbonCopy, subject, plainText, htmlBody, replyTo, null);
    }

    private void sendWithSendGrid(String to, List<String> carbonCopy, String subject, String plainText, String htmlBody, String replyTo, String fromDisplayName)
            throws IOException {
        String displayName = StringUtils.hasText(fromDisplayName) ? fromDisplayName : sendGridProperties.getFromName();
        Email from = new Email(sendGridProperties.getFromEmail(), displayName);
        Mail mail = new Mail();
        mail.setFrom(from);
        mail.setSubject(subject);
        if (StringUtils.hasText(replyTo)) {
            mail.setReplyTo(new Email(replyTo.trim()));
        }
        Personalization p = new Personalization();
        p.addTo(new Email(to));
        if (carbonCopy != null) {
            for (String cc : carbonCopy) {
                if (StringUtils.hasText(cc)) {
                    p.addCc(new Email(cc.trim()));
                }
            }
        }
        mail.addPersonalization(p);
        mail.addContent(new Content("text/plain", plainText));
        if (StringUtils.hasText(htmlBody)) {
            mail.addContent(new Content("text/html", htmlBody));
        }
        SendGrid sg = new SendGrid(sendGridProperties.getApiKey());
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());
        Response response = sg.api(request);
        if (response.getStatusCode() >= 300) {
            throw new IllegalStateException("SendGrid error: " + response.getStatusCode() + " " + response.getBody());
        }
    }

    private void sendWithSmtp(String to, List<String> carbonCopy, String subject, String plainText, String htmlBody, String replyTo)
            throws Exception {
        sendWithSmtp(to, carbonCopy, subject, plainText, htmlBody, replyTo, null);
    }

    private void sendWithSmtp(String to, List<String> carbonCopy, String subject, String plainText, String htmlBody, String replyTo, String fromDisplayName)
            throws Exception {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException("No email provider configured: missing SendGrid API key and SMTP sender");
        }
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
        helper.setTo(to);
        if (StringUtils.hasText(replyTo)) {
            helper.setReplyTo(replyTo.trim());
        }
        if (carbonCopy != null && !carbonCopy.isEmpty()) {
            helper.setCc(carbonCopy.toArray(new String[0]));
        }
        helper.setSubject(subject);
        if (StringUtils.hasText(sendGridProperties.getFromEmail())) {
            String display = StringUtils.hasText(fromDisplayName) ? fromDisplayName : sendGridProperties.getFromName();
            if (StringUtils.hasText(display)) {
                helper.setFrom(sendGridProperties.getFromEmail(), display);
            } else {
                helper.setFrom(sendGridProperties.getFromEmail());
            }
        }
        if (StringUtils.hasText(htmlBody)) {
            helper.setText(plainText, htmlBody);
        } else {
            helper.setText(plainText, false);
        }
        mailSender.send(mime);
    }

    @Async
    public void sendGracePeriodApproved(GracePeriodRequest grace) {
        Employee employee = grace.getEmployee();
        String to = resolveEmployeeEmail(employee);
        if (!StringUtils.hasText(to)) {
            return;
        }
        Language lang = employee.getPreferredLanguage() != null ? employee.getPreferredLanguage() : Language.EN;
        String granter = grace.getGrantedBy() != null ? grace.getGrantedBy().getEmail() : "Happy Hearts";
        String scope = grace.getDateFrom().equals(grace.getDateTo())
                ? grace.getDateFrom().format(DATE_FMT)
                : grace.getDateFrom().format(DATE_FMT) + " → " + grace.getDateTo().format(DATE_FMT);
        String subject = switch (lang) {
            case EN -> "Grace period approved — " + scope;
            case FR -> "Période de grâce accordée — " + scope;
            case KI -> "Igihe cyihutirwa cyemewe — " + scope;
        };
        String body = switch (lang) {
            case EN -> """
                    Hello %s,

                    A grace period has been approved for your attendance record.

                    Period: %s
                    Approved by: %s
                    Justification: %s

                    This day will be recorded as GRACE instead of LATE.

                    — Happy Hearts Attendance System
                    """.formatted(
                    employee.getFirstName(),
                    scope,
                    granter,
                    grace.getApproverExplanation() != null ? grace.getApproverExplanation() : "—"
            );
            case FR -> """
                    Bonjour %s,

                    Une période de grâce a été accordée pour votre présence.

                    Période : %s
                    Accordée par : %s
                    Justification : %s

                    Cette journée sera enregistrée comme GRÂCE et non comme RETARD.

                    — Système de présence Happy Hearts
                    """.formatted(
                    employee.getFirstName(),
                    scope,
                    granter,
                    grace.getApproverExplanation() != null ? grace.getApproverExplanation() : "—"
            );
            case KI -> """
                    Muraho %s,

                    Igihe cyihutirwa cyemewe ku kwitabira kwawe.

                    Igihe: %s
                    Byemewe na: %s
                    Impamvu: %s

                    — Happy Hearts
                    """.formatted(
                    employee.getFirstName(),
                    scope,
                    granter,
                    grace.getApproverExplanation() != null ? grace.getApproverExplanation() : "—"
            );
        };
        persistAndSend(to, subject, body, null, lang, NotificationType.GRACE_PERIOD_APPROVED);
    }

    @Async
    public void sendExcuseGranted(AttendanceExcuse excuse) {
        Employee employee = excuse.getEmployee();
        String to = resolveEmployeeEmail(employee);
        if (!StringUtils.hasText(to)) {
            return;
        }
        Language lang = employee.getPreferredLanguage() != null ? employee.getPreferredLanguage() : Language.EN;
        String granter = excuse.getGrantedBy().getEmail();
        String scope = excuse.getDateFrom().equals(excuse.getDateTo())
                ? excuse.getDateFrom().format(DATE_FMT)
                : excuse.getDateFrom().format(DATE_FMT) + " → " + excuse.getDateTo().format(DATE_FMT);
        String typeLabel = excuse.getExcuseType().name().replace('_', ' ');
        String subject = switch (lang) {
            case EN -> "Absence excused — " + scope;
            case FR -> "Absence excusée — " + scope;
            case KI -> "Kureka kwitabira — " + scope;
        };
        String body = switch (lang) {
            case EN -> """
                    Hello %s,

                    Your absence has been recorded as EXCUSED (not counted as absent).

                    Period: %s
                    Type: %s
                    Granted by: %s
                    Reason: %s

                    — Happy Hearts Attendance System
                    """.formatted(employee.getFirstName(), scope, typeLabel, granter, excuse.getReason());
            case FR -> """
                    Bonjour %s,

                    Votre absence a été enregistrée comme EXCUSÉE (non comptée comme absente).

                    Période : %s
                    Type : %s
                    Accordée par : %s
                    Motif : %s

                    — Système de présence Happy Hearts
                    """.formatted(employee.getFirstName(), scope, typeLabel, granter, excuse.getReason());
            case KI -> """
                    Muraho %s,

                    Kutitabira kwawe kwanditswe nk' EXCUSED.

                    Igihe: %s
                    Ubwoko: %s
                    Byatanzwe na: %s
                    Impamvu: %s

                    — Happy Hearts
                    """.formatted(employee.getFirstName(), scope, typeLabel, granter, excuse.getReason());
        };
        persistAndSend(to, subject, body, null, lang, NotificationType.ATTENDANCE_EXCUSE_GRANTED);
    }

    @Transactional
    public NotificationStatus sendExplanationRequest(String to, String subject, String body, Employee employee) {
        Language lang = employee.getPreferredLanguage() != null ? employee.getPreferredLanguage() : Language.EN;
        String plain = body + "\n\n— Happy Hearts HR";
        return persistAndSend(to, subject, plain, null, lang, NotificationType.EXPLANATION_REQUEST);
    }

    public void sendAccessRequestDecision(
            String email,
            String firstName,
            Language lang,
            boolean approved,
            String adminMessage,
            String assignedRoleLabel
    ) {
        Language l = lang != null ? lang : Language.EN;
        String greeting = StringUtils.hasText(firstName) ? firstName : email;
        if (approved) {
            String subject = switch (l) {
                case EN -> "Happy Hearts – Your access request was approved";
                case FR -> "Happy Hearts – Votre demande d'accès a été approuvée";
                case KI -> "Happy Hearts – Ubusabe bwawe bwemewe";
            };
            String body = switch (l) {
                case EN -> """
                        Hello %s,

                        Your request to access the Happy Hearts portal has been approved.
                        Role: %s

                        %s

                        You will receive a separate email with a link to set your password and activate your account.

                        — Happy Hearts
                        """.formatted(
                        greeting,
                        assignedRoleLabel != null ? assignedRoleLabel.replace('_', ' ') : "—",
                        StringUtils.hasText(adminMessage) ? "Message from the administrator:\n" + adminMessage : "");
                case FR -> """
                        Bonjour %s,

                        Votre demande d'accès au portail Happy Hearts a été approuvée.
                        Rôle : %s

                        %s

                        Vous recevrez un autre e-mail avec un lien pour définir votre mot de passe et activer votre compte.

                        — Happy Hearts
                        """.formatted(
                        greeting,
                        assignedRoleLabel != null ? assignedRoleLabel.replace('_', ' ') : "—",
                        StringUtils.hasText(adminMessage) ? "Message de l'administrateur :\n" + adminMessage : "");
                case KI -> """
                        Muraho %s,

                        Ubusabe bwawe bwo kwinjira kuri Happy Hearts bwemewe.
                        Uruhare: %s

                        %s

                        Uzahabwa iandi imeyili irimo link yo gushyiraho ijambo ry'ibanga.

                        — Happy Hearts
                        """.formatted(
                        greeting,
                        assignedRoleLabel != null ? assignedRoleLabel.replace('_', ' ') : "—",
                        StringUtils.hasText(adminMessage) ? adminMessage : "");
            };
            persistAndSend(email, subject, body, null, l, NotificationType.WELCOME_USER);
        } else {
            String subject = switch (l) {
                case EN -> "Happy Hearts – Your access request was declined";
                case FR -> "Happy Hearts – Votre demande d'accès a été refusée";
                case KI -> "Happy Hearts – Ubusabe bwawe bwanze";
            };
            String body = switch (l) {
                case EN -> """
                        Hello %s,

                        Your request to access the Happy Hearts portal was not approved at this time.

                        Reason from the administrator:
                        %s

                        If you have questions, please contact your school administration directly.

                        — Happy Hearts
                        """.formatted(greeting, adminMessage != null ? adminMessage : "—");
                case FR -> """
                        Bonjour %s,

                        Votre demande d'accès au portail Happy Hearts n'a pas été approuvée pour le moment.

                        Motif de l'administrateur :
                        %s

                        Pour toute question, contactez directement l'administration de votre école.

                        — Happy Hearts
                        """.formatted(greeting, adminMessage != null ? adminMessage : "—");
                case KI -> """
                        Muraho %s,

                        Ubusabe bwawe bwo kwinjira kuri Happy Hearts ntibwemewe.

                        Impamvu:
                        %s

                        — Happy Hearts
                        """.formatted(greeting, adminMessage != null ? adminMessage : "—");
            };
            persistAndSend(email, subject, body, null, l, NotificationType.EXPLANATION_REQUEST);
        }
    }
}
