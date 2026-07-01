package com.happyhearts.util;

import com.happyhearts.enums.Language;

public final class EmailHtmlTemplates {

    private EmailHtmlTemplates() {
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** Minimal escaping for URLs inside double-quoted HTML attributes. */
    private static String escAttr(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("\"", "&quot;");
    }

    /**
     * Branded HTML for login OTP. Plain-text part is sent separately for clients that prefer it.
     */
    public static String loginOtpHtml(Language lang, String otp, int minutes, String brandLogoUrl) {
        String header = brandHeaderBlock(brandLogoUrl);
        String title = switch (lang) {
            case EN -> "Your verification code";
            case FR -> "Votre code de vérification";
            case KI -> "Kode yawe yo kwemeza";
        };
        String intro = switch (lang) {
            case EN -> "Use this one-time code to finish signing in:";
            case FR -> "Utilisez ce code à usage unique pour terminer la connexion :";
            case KI -> "Koresha iyi kode imwe kugirango urangize kwinjira:";
        };
        String expiry = switch (lang) {
            case EN -> "This code expires in <strong>" + minutes + "</strong> minutes. If you did not try to sign in, you can ignore this email.";
            case FR -> "Ce code expire dans <strong>" + minutes + "</strong> minutes. Si vous n'avez pas tenté de vous connecter, ignorez cet e-mail.";
            case KI -> "Iyi kode irarangira mu minota <strong>" + minutes + "</strong>. Niba utagerageje kwinjira, wirengagize iyi imeli.";
        };
        String footer = switch (lang) {
            case EN -> "Happy Hearts System";
            case FR -> "Système Happy Hearts";
            case KI -> "Sisitemu ya Happy Hearts";
        };

        String safeOtp = esc(otp);

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/></head>
                <body style="margin:0;background:#f4f1ec;font-family:'Segoe UI',Roboto,Arial,sans-serif;color:#1a1108;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f4f1ec;padding:24px 12px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" style="max-width:520px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 12px 28px rgba(0,0,0,0.08);border:1px solid #ecddd8;">
                        <tr>
                          <td style="padding:0;background:linear-gradient(90deg,#e8380d 0%%,#2d9b6b 100%%);height:4px;"></td>
                        </tr>
                        %s
                        <tr>
                          <td style="padding:8px 28px 24px;">
                            <h1 style="margin:0 0 12px;font-size:18px;font-weight:700;color:#1a1108;">%s</h1>
                            <p style="margin:0 0 16px;font-size:14px;line-height:1.5;color:#4a403a;">%s</p>
                            <div style="text-align:center;margin:20px 0;">
                              <span style="display:inline-block;font-size:28px;font-weight:800;letter-spacing:8px;padding:14px 22px;border-radius:12px;background:#faf5f3;border:1.5px solid #ecddd8;color:#1a1108;">%s</span>
                            </div>
                            <p style="margin:0;font-size:13px;line-height:1.55;color:#6b5f59;">%s</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:16px 28px 24px;border-top:1px solid #f1e8e4;text-align:center;font-size:11px;color:#9a8f89;">— %s —</td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(header, title, intro, safeOtp, expiry, footer);
    }

    public static String passwordResetHtml(Language lang, String link, String brandLogoUrl) {
        String header = brandHeaderBlock(brandLogoUrl);
        String title = switch (lang) {
            case EN -> "Reset your password";
            case FR -> "Réinitialiser votre mot de passe";
            case KI -> "Subiza ijambo ryawe ry'ibanga";
        };
        String body = switch (lang) {
            case EN -> "Click the secure button below to choose a new password. If you did not request this, you can ignore this email.";
            case FR -> "Cliquez sur le bouton sécurisé ci-dessous pour choisir un nouveau mot de passe. Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail.";
            case KI -> "Kanda kuri buto hepfo kugirango uhitemo ijambo rishya ry'ibanga. Niba atari wowe, wirengagize iyi imeli.";
        };
        String cta = switch (lang) {
            case EN -> "Reset password";
            case FR -> "Réinitialiser le mot de passe";
            case KI -> "Subiza ijambo ry'ibanga";
        };
        String safeLinkText = esc(link);

        return """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"/></head>
                <body style="margin:0;background:#f4f1ec;font-family:'Segoe UI',Roboto,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:24px 12px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" style="max-width:520px;background:#fff;border-radius:16px;border:1px solid #ecddd8;box-shadow:0 12px 28px rgba(0,0,0,0.08);">
                        <tr><td style="height:4px;background:linear-gradient(90deg,#e8380d,#2d9b6b);"></td></tr>
                        %s
                        <tr><td style="padding:0 28px 28px;">
                          <h1 style="margin:0 0 12px;font-size:18px;color:#1a1108;">%s</h1>
                          <p style="margin:0 0 20px;font-size:14px;line-height:1.5;color:#4a403a;">%s</p>
                          <div style="text-align:center;">
                            <a href="%s" style="display:inline-block;padding:12px 22px;border-radius:12px;background:#e8380d;color:#ffffff;font-weight:700;text-decoration:none;font-size:14px;">%s</a>
                          </div>
                          <p style="margin:20px 0 0;font-size:11px;color:#9a8f89;word-break:break-all;">%s</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(header, title, body, link, cta, safeLinkText);
    }

    /**
     * Header block: optional remote logo URL, otherwise text wordmark matching app branding.
     */
    public static String brandHeaderBlock(String brandLogoUrl) {
        if (hasText(brandLogoUrl)) {
            String safe = esc(brandLogoUrl.trim());
            return """
                    <tr>
                      <td style="padding:24px 28px 8px;text-align:center;">
                        <img src="%s" alt="Happy Hearts" width="220" style="max-width:100%%;height:auto;display:block;margin:0 auto;border:0;"/>
                        <div style="font-size:12px;color:#7a6a63;margin-top:8px;">Staff &amp; attendance</div>
                      </td>
                    </tr>
                    """.formatted(safe);
        }
        return """
                <tr>
                  <td style="padding:28px 28px 8px;text-align:center;">
                    <div style="font-size:22px;font-weight:800;letter-spacing:0.5px;">
                      <span style="color:#e8380d;">Happy</span> <span style="color:#2d9b6b;">Hearts</span>
                    </div>
                    <div style="font-size:12px;color:#7a6a63;margin-top:4px;">Staff &amp; attendance</div>
                  </td>
                </tr>
                """;
    }

    /**
     * Professional welcome for new dashboard users; language matches account preference (single locale).
     */
    public static String welcomeUserSetupHtml(
            Language lang,
            String userEmail,
            String setupLink,
            int hoursValid,
            String brandLogoUrl
    ) {
        String safeEmail = esc(userEmail);
        String href = escAttr(setupLink);
        String header = brandHeaderBlock(brandLogoUrl);
        String htmlLang = switch (lang) {
            case EN -> "en";
            case FR -> "fr";
            case KI -> "rw";
        };
        String title = switch (lang) {
            case EN -> "Welcome to Happy Hearts";
            case FR -> "Bienvenue sur Happy Hearts";
            case KI -> "Murakaza neza kuri Happy Hearts";
        };
        String subtitle = switch (lang) {
            case EN -> "Your dashboard access is ready.";
            case FR -> "Votre accès au tableau de bord est prêt.";
            case KI -> "Konti yawe ya dashboard itegetswe.";
        };
        String accountLabel = switch (lang) {
            case EN -> "Account";
            case FR -> "Compte";
            case KI -> "Imeli";
        };
        String cta = switch (lang) {
            case EN -> "Set your password";
            case FR -> "Définir mon mot de passe";
            case KI -> "Shyiraho ijambo ry'ibanga";
        };
        String note = switch (lang) {
            case EN -> "This secure link expires in <strong>" + hoursValid + "</strong> hours. After your first sign-in, you can use "
                    + "<strong>Forgot password</strong> on the login page if you need a new link.";
            case FR -> "Ce lien sécurisé expire dans <strong>" + hoursValid + "</strong> heures. Après votre première connexion, utilisez "
                    + "<strong>Mot de passe oublié</strong> sur la page de connexion si vous avez besoin d'un nouveau lien.";
            case KI -> "Iyi link itekanye irarangira mu masaha <strong>" + hoursValid + "</strong>. Nyuma yo kwinjira, koresha "
                    + "<strong>Wibagiwe ijambo ry'ibanga</strong> niba ukeneye indi link.";
        };
        String boxTitle = switch (lang) {
            case EN -> "What's next";
            case FR -> "Étapes suivantes";
            case KI -> "Ibikurikira";
        };
        String boxBody = switch (lang) {
            case EN -> "Use the button above to choose your password. You will then sign in with your email, password, and a one-time code sent to your inbox.";
            case FR -> "Utilisez le bouton ci-dessus pour choisir votre mot de passe. Vous vous connecterez ensuite avec votre e-mail, votre mot de passe et un code à usage unique envoyé par e-mail.";
            case KI -> "Koresha buto hembere guhitamo ijambo ry'ibanga. Nyuma uzakwinjira ukoreshe imeli, ijambo ry'ibanga, n'kode imwe yoherejwe kuri imeli yawe.";
        };
        String footer = switch (lang) {
            case EN -> "Happy Hearts &mdash; Staff &amp; attendance";
            case FR -> "Happy Hearts &mdash; Personnel &amp; présence";
            case KI -> "Happy Hearts &mdash; Abakozi &amp; kuba hari";
        };
        return """
                <!DOCTYPE html>
                <html lang="%s">
                <head><meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/></head>
                <body style="margin:0;background:#eef2f0;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1a1a1a;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#eef2f0;padding:28px 14px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" style="max-width:520px;background:#ffffff;border-radius:20px;overflow:hidden;box-shadow:0 16px 40px rgba(15,23,42,0.12);border:1px solid #e7e5e4;">
                        <tr>
                          <td style="padding:0;background:linear-gradient(135deg,#e8380d 0%%,#f97316 45%%,#2d9b6b 100%%);height:5px;"></td>
                        </tr>
                        %s
                        <tr>
                          <td style="padding:28px 32px 8px;">
                            <p style="margin:0 0 6px;font-size:11px;font-weight:700;letter-spacing:0.12em;text-transform:uppercase;color:#c2410c;">Happy Hearts</p>
                            <h1 style="margin:0 0 8px;font-size:22px;font-weight:800;line-height:1.25;color:#0f172a;letter-spacing:-0.02em;">%s</h1>
                            <p style="margin:0 0 20px;font-size:14px;line-height:1.5;color:#64748b;">%s</p>
                            <div style="border-radius:14px;background:linear-gradient(180deg,#fffbeb 0%%,#fff7ed 100%%);border:1px solid #fed7aa;padding:16px 18px;margin-bottom:22px;">
                              <p style="margin:0 0 4px;font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:0.06em;color:#9a3412;">%s</p>
                              <p style="margin:0;font-size:15px;font-weight:600;color:#1e293b;word-break:break-all;">%s</p>
                            </div>
                            <div style="text-align:center;margin:0 0 22px;">
                              <a href="%s" style="display:inline-block;padding:15px 32px;border-radius:9999px;background:linear-gradient(180deg,#ea580c 0%%,#c2410c 100%%);color:#ffffff;font-weight:700;text-decoration:none;font-size:15px;box-shadow:0 4px 14px rgba(234,88,12,0.45);">%s</a>
                            </div>
                            <p style="margin:0 0 20px;font-size:13px;line-height:1.6;color:#64748b;">%s</p>
                            <div style="border-radius:14px;border:1px solid #e2e8f0;background:#f8fafc;padding:16px 18px;">
                              <p style="margin:0 0 8px;font-size:12px;font-weight:700;color:#0f172a;">%s</p>
                              <p style="margin:0;font-size:13px;line-height:1.55;color:#475569;">%s</p>
                            </div>
                          </td>
                        </tr>
                        <tr><td style="padding:16px 32px 26px;border-top:1px solid #f1f5f9;text-align:center;">
                          <p style="margin:0;font-size:11px;color:#94a3b8;line-height:1.45;">%s</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                htmlLang,
                header,
                esc(title),
                esc(subtitle),
                esc(accountLabel),
                safeEmail,
                href,
                esc(cta),
                note,
                esc(boxTitle),
                esc(boxBody),
                footer
        );
    }

    /** Single-language announcement email for dashboard users. */
    public static String announcementEmailHtml(Language lang, String title, String bodyPlain, String brandLogoUrl) {
        String htmlLang = switch (lang) {
            case EN -> "en";
            case FR -> "fr";
            case KI -> "rw";
        };
        String ribbon = switch (lang) {
            case EN -> "Announcement";
            case FR -> "Annonce";
            case KI -> "Itangazo";
        };
        String footer = switch (lang) {
            case EN -> "This message was sent by Happy Hearts administration.";
            case FR -> "Ce message a été envoyé par l'administration Happy Hearts.";
            case KI -> "Ubu butumwa bwoherejwe na Happy Hearts.";
        };
        String header = brandHeaderBlock(brandLogoUrl);
        String safeTitle = esc(title);
        String safeBody = esc(bodyPlain).replace("\n", "<br/>");
        return """
                <!DOCTYPE html>
                <html lang="%s">
                <head><meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/></head>
                <body style="margin:0;background:#f1f5f9;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#0f172a;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:28px 12px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" style="max-width:560px;background:#ffffff;border-radius:20px;overflow:hidden;border:1px solid #e2e8f0;box-shadow:0 20px 45px rgba(15,23,42,0.08);">
                        <tr><td style="height:5px;background:linear-gradient(90deg,#ea580c,#16a34a);"></td></tr>
                        %s
                        <tr><td style="padding:20px 28px 8px;">
                          <span style="display:inline-block;font-size:11px;font-weight:700;letter-spacing:0.14em;text-transform:uppercase;color:#ea580c;">%s</span>
                          <h1 style="margin:10px 0 16px;font-size:20px;font-weight:800;line-height:1.3;color:#0f172a;">%s</h1>
                          <div style="font-size:14px;line-height:1.65;color:#334155;">%s</div>
                        </td></tr>
                        <tr><td style="padding:16px 28px 24px;border-top:1px solid #f1f5f9;">
                          <p style="margin:0;font-size:12px;color:#94a3b8;line-height:1.5;">%s</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(htmlLang, header, esc(ribbon), safeTitle, safeBody, esc(footer));
    }

    /**
     * Branded welcome when an employee record is created (optional branch line).
     */
    public static String employeeWelcomeHtml(Language lang, String firstName, String branchName, String brandLogoUrl) {
        String name = esc(firstName);
        String branch = hasText(branchName) ? esc(branchName) : "";
        String header = brandHeaderBlock(brandLogoUrl);
        String title = switch (lang) {
            case EN -> "You're on the team";
            case FR -> "Bienvenue dans l'équipe";
            case KI -> "Murakaza neza muri kipe";
        };
        String line1 = switch (lang) {
            case EN -> "Hello " + name + ", your staff profile has been created in Happy Hearts.";
            case FR -> "Bonjour " + name + ", votre profil personnel a été créé dans Happy Hearts.";
            case KI -> "Muraho " + name + ", umwirondoro wawe wongerewe muri Happy Hearts.";
        };
        String line2 = switch (lang) {
            case EN -> hasText(branchName)
                    ? "Branch: <strong>" + branch + "</strong>."
                    : "We will contact you with next steps if needed.";
            case FR -> hasText(branchName)
                    ? "Branche : <strong>" + branch + "</strong>."
                    : "Nous vous contacterons si nécessaire.";
            case KI -> hasText(branchName)
                    ? "Ishami: <strong>" + branch + "</strong>."
                    : "Tuzakuvugisha mugihe kibaye ngombwa.";
        };
        return """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"/></head>
                <body style="margin:0;background:#f4f1ec;font-family:'Segoe UI',Roboto,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:24px 12px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" style="max-width:520px;background:#fff;border-radius:16px;border:1px solid #ecddd8;box-shadow:0 12px 28px rgba(0,0,0,0.08);">
                        <tr><td style="height:4px;background:linear-gradient(90deg,#e8380d,#2d9b6b);"></td></tr>
                        %s
                        <tr><td style="padding:8px 28px 28px;">
                          <h1 style="margin:0 0 12px;font-size:18px;color:#1a1108;">%s</h1>
                          <p style="margin:0 0 12px;font-size:14px;line-height:1.5;color:#4a403a;">%s</p>
                          <p style="margin:0;font-size:14px;line-height:1.5;color:#4a403a;">%s</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(header, title, line1, line2);
    }

    /**
     * Branch lead / second teacher: new staff member registered (language = recipient leader's preference).
     */
    public static String newEmployeeLeaderNoticeHtml(
            Language lang,
            String leaderFirstName,
            String newEmployeeFullName,
            String branchCode,
            String branchName,
            String brandLogoUrl
    ) {
        String greet = esc(leaderFirstName);
        String who = esc(newEmployeeFullName);
        String code = esc(branchCode);
        String bname = esc(branchName);
        String header = brandHeaderBlock(brandLogoUrl);
        String title = switch (lang) {
            case EN -> "New team member";
            case FR -> "Nouveau membre d'équipe";
            case KI -> "Umunyamuryango mushya";
        };
        String body = switch (lang) {
            case EN -> "Hello " + greet + ",<br/><br/><strong>" + who + "</strong> has been added to the Happy Hearts system for branch <strong>"
                    + code + "</strong> (" + bname + ").";
            case FR -> "Bonjour " + greet + ",<br/><br/><strong>" + who + "</strong> a été ajouté(e) au système Happy Hearts pour la branche <strong>"
                    + code + "</strong> (" + bname + ").";
            case KI -> "Muraho " + greet + ",<br/><br/><strong>" + who + "</strong> yongewe muri sisitemu ya Happy Hearts ku ishami <strong>"
                    + code + "</strong> (" + bname + ").";
        };
        return """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"/></head>
                <body style="margin:0;background:#f4f1ec;font-family:'Segoe UI',Roboto,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:24px 12px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" style="max-width:520px;background:#fff;border-radius:16px;border:1px solid #ecddd8;box-shadow:0 12px 28px rgba(0,0,0,0.08);">
                        <tr><td style="height:4px;background:linear-gradient(90deg,#e8380d,#2d9b6b);"></td></tr>
                        %s
                        <tr><td style="padding:8px 28px 28px;">
                          <h1 style="margin:0 0 12px;font-size:18px;color:#1a1108;">%s</h1>
                          <p style="margin:0;font-size:14px;line-height:1.55;color:#4a403a;">%s</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(header, title, body);
    }

    /** Feedback / evaluation notice (plain content escaped; line breaks preserved). */
    public static String portalFeedbackHtml(
            Language lang,
            String senderLabel,
            String typeLabel,
            String visibilityLabel,
            String contentPlain,
            String portalUrl,
            String brandLogoUrl
    ) {
        String header = brandHeaderBlock(brandLogoUrl);
        String title = switch (lang) {
            case EN -> "New feedback — Happy Hearts";
            case FR -> "Nouveau feedback — Happy Hearts";
            case KI -> "Ubutumwa bushya — Happy Hearts";
        };
        String intro = switch (lang) {
            case EN -> "You have received feedback in the Happy Hearts portal.";
            case FR -> "Vous avez reçu un feedback dans le portail Happy Hearts.";
            case KI -> "Wakiriye ubutumwa muri portale ya Happy Hearts.";
        };
        String fromLine = switch (lang) {
            case EN -> "From";
            case FR -> "De";
            case KI -> "Kuva";
        };
        String typeLine = switch (lang) {
            case EN -> "Type";
            case FR -> "Type";
            case KI -> "Ubwoko";
        };
        String visLine = switch (lang) {
            case EN -> "Visibility";
            case FR -> "Visibilité";
            case KI -> "Uko biboneka";
        };
        String linkLabel = switch (lang) {
            case EN -> "Open portal";
            case FR -> "Ouvrir le portail";
            case KI -> "Fungura portale";
        };
        String safeSender = esc(senderLabel);
        String safeType = esc(typeLabel);
        String safeVis = esc(visibilityLabel);
        String safeBody = esc(contentPlain).replace("\n", "<br/>");
        String safeUrl = escAttr(portalUrl);
        return """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"/></head>
                <body style="margin:0;background:#f4f1ec;font-family:'Segoe UI',Roboto,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:24px 12px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" style="max-width:520px;background:#fff;border-radius:16px;border:1px solid #ecddd8;box-shadow:0 12px 28px rgba(0,0,0,0.08);">
                        <tr><td style="height:4px;background:linear-gradient(90deg,#e8380d,#2d9b6b);"></td></tr>
                        %s
                        <tr><td style="padding:8px 28px 28px;">
                          <h1 style="margin:0 0 12px;font-size:18px;color:#1a1108;">%s</h1>
                          <p style="margin:0 0 16px;font-size:14px;line-height:1.5;color:#4a403a;">%s</p>
                          <p style="margin:0 0 6px;font-size:13px;color:#6b5f59;"><strong>%s:</strong> %s</p>
                          <p style="margin:0 0 6px;font-size:13px;color:#6b5f59;"><strong>%s:</strong> %s</p>
                          <p style="margin:0 0 16px;font-size:13px;color:#6b5f59;"><strong>%s:</strong> %s</p>
                          <div style="font-size:14px;line-height:1.55;color:#1a1108;border-left:3px solid #e8380d;padding-left:12px;margin:12px 0;">%s</div>
                          <p style="margin:16px 0 0;"><a href="%s" style="color:#e8380d;font-weight:700;">%s</a></p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(
                header,
                title,
                intro,
                fromLine,
                safeSender,
                typeLine,
                safeType,
                visLine,
                safeVis,
                safeBody,
                safeUrl,
                linkLabel
        );
    }

    /** Internal messaging copy sent by email — minimal, professional layout (no large logo). */
    public static String internalMessageHtml(
            Language lang,
            String senderLabel,
            String subjectLine,
            String contentPlain,
            String portalUrl,
            String brandLogoUrl
    ) {
        String badge = switch (lang) {
            case EN -> "Internal message";
            case FR -> "Message interne";
            case KI -> "Ubutumwa muri sisitemu";
        };
        String linkLabel = switch (lang) {
            case EN -> "Open in portal";
            case FR -> "Ouvrir dans le portail";
            case KI -> "Fungura muri portale";
        };
        String subLabel = switch (lang) {
            case EN -> "Subject";
            case FR -> "Objet";
            case KI -> "Insanganyamatsiko";
        };
        String fromLabel = switch (lang) {
            case EN -> "From";
            case FR -> "De";
            case KI -> "Kuva";
        };
        String footer = switch (lang) {
            case EN -> "Happy Hearts — automatic notification. Please do not reply to this email.";
            case FR -> "Happy Hearts — notification automatique. Ne pas répondre à cet e-mail.";
            case KI -> "Happy Hearts — ubutumwa bwoherejwe na sisitemu. Ntugasubize kuri iyi imeyili.";
        };
        String safeSub = esc(subjectLine);
        String safeSender = esc(senderLabel);
        String safeBody = esc(contentPlain).replace("\n", "<br/>");
        String safeUrl = escAttr(portalUrl);
        return """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/></head>
                <body style="margin:0;padding:0;background:#f3f4f6;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:32px 16px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" style="max-width:520px;background:#ffffff;border:1px solid #e5e7eb;border-radius:8px;">
                        <tr><td style="padding:20px 28px 0;">
                          <p style="margin:0;font-size:11px;font-weight:600;letter-spacing:0.06em;text-transform:uppercase;color:#9ca3af;">Happy Hearts · %s</p>
                        </td></tr>
                        <tr><td style="padding:12px 28px 24px;">
                          <p style="margin:0 0 14px;font-size:13px;color:#6b7280;"><strong style="color:#374151;">%s:</strong> %s</p>
                          <p style="margin:0 0 18px;font-size:13px;color:#6b7280;"><strong style="color:#374151;">%s:</strong> %s</p>
                          <div style="font-size:15px;line-height:1.6;color:#1f2937;padding:16px 18px;background:#f9fafb;border-radius:6px;border:1px solid #f3f4f6;">%s</div>
                          <p style="margin:22px 0 0;">
                            <a href="%s" style="display:inline-block;padding:10px 18px;background:#ea580c;color:#ffffff;text-decoration:none;border-radius:6px;font-size:13px;font-weight:600;">%s</a>
                          </p>
                          <p style="margin:20px 0 0;font-size:11px;color:#9ca3af;line-height:1.5;">%s</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(
                badge,
                subLabel,
                safeSub,
                fromLabel,
                safeSender,
                safeBody,
                safeUrl,
                linkLabel,
                footer
        );
    }

    /** Professional staff email (SMTP tab) — logo, orange accent, signature block. */
    public static String professionalStaffEmailHtml(
            Language lang,
            String subjectLine,
            String bodyPlain,
            String signatureBlock,
            String brandLogoUrl
    ) {
        String header = brandHeaderBlock(brandLogoUrl);
        String safeSub = esc(subjectLine);
        String safeBody = esc(bodyPlain).replace("\n", "<br/>");
        String safeSig = esc(signatureBlock).replace("\n", "<br/>");
        String footer = switch (lang) {
            case EN -> "Happy Hearts — internal HR communication.";
            case FR -> "Happy Hearts — communication RH interne.";
            case KI -> "Happy Hearts — itumanaho rya HR mu biro.";
        };
        return """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/></head>
                <body style="margin:0;padding:0;background:#f3f4f6;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:32px 16px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" style="max-width:560px;background:#ffffff;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;">
                        <tr><td style="background:linear-gradient(135deg,#ff6b35,#ea580c);padding:4px 0;"></td></tr>
                        <tr><td style="padding:24px 28px 8px;">%s</td></tr>
                        <tr><td style="padding:8px 28px 0;">
                          <h1 style="margin:0;font-size:20px;color:#1f2937;">%s</h1>
                        </td></tr>
                        <tr><td style="padding:20px 28px;">
                          <div style="font-size:15px;line-height:1.65;color:#374151;">%s</div>
                          <p style="margin:24px 0 0;font-size:14px;line-height:1.6;color:#6b7280;border-top:1px solid #f3f4f6;padding-top:16px;">%s</p>
                          <p style="margin:20px 0 0;font-size:11px;color:#9ca3af;">%s</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(header, safeSub, safeBody, safeSig, footer);
    }
}
