package com.realestate.services;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.realestate.util.JsonUtil;

/**
 * Email Validation (Syntax + Typo Check + Disposable Domain Guard + DNS MX Lookup)
 * and Zero-Dependency Real SMTP (STARTTLS / SSL) + Resend API Email Delivery Service.
 */
public final class EmailService {

    private static final Pattern STRICT_EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9](?:[A-Za-z0-9._%+-]{0,62}[A-Za-z0-9])?@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*\\.[A-Za-z]{2,63}$"
    );

    private static final Set<String> DISPOSABLE_DOMAINS = new HashSet<>(Arrays.asList(
        "mailinator.com", "guerrillamail.com", "tempmail.com", "10minutemail.com",
        "yopmail.com", "trashmail.com", "throwawaymail.com", "fakeinbox.com", "sharklasers.com"
    ));

    private static final Set<String> DEMO_SAFE_DOMAINS = new HashSet<>(Arrays.asList(
        "realestate.com", "example.com"
    ));

    private static final Map<String, String> DOMAIN_TYPOS = new HashMap<>();
    static {
        DOMAIN_TYPOS.put("gmial.com", "gmail.com");
        DOMAIN_TYPOS.put("gmal.com", "gmail.com");
        DOMAIN_TYPOS.put("gamil.com", "gmail.com");
        DOMAIN_TYPOS.put("gmail.con", "gmail.com");
        DOMAIN_TYPOS.put("gmail.co", "gmail.com");
        DOMAIN_TYPOS.put("yaho.com", "yahoo.com");
        DOMAIN_TYPOS.put("yahooo.com", "yahoo.com");
        DOMAIN_TYPOS.put("hotmial.com", "hotmail.com");
        DOMAIN_TYPOS.put("outlok.com", "outlook.com");
        DOMAIN_TYPOS.put("outllok.com", "outlook.com");
    }

    public static class EmailValidationResult {
        public final boolean valid;
        public final String normalizedEmail;
        public final String message;
        public final String suggestion;
        public final boolean mxVerified;

        public EmailValidationResult(boolean valid, String normalizedEmail, String message, String suggestion, boolean mxVerified) {
            this.valid = valid;
            this.normalizedEmail = normalizedEmail;
            this.message = message;
            this.suggestion = suggestion;
            this.mxVerified = mxVerified;
        }
    }

    public static class EmailSendResult {
        public final boolean sentToInbox;
        public final String deliveryMode; // "SMTP_SENT", "RESEND_SENT", "DEMO_FALLBACK"
        public final String infoMessage;

        public EmailSendResult(boolean sentToInbox, String deliveryMode, String infoMessage) {
            this.sentToInbox = sentToInbox;
            this.deliveryMode = deliveryMode;
            this.infoMessage = infoMessage;
        }
    }

    private EmailService() {}

    /**
     * Full multi-layer email validator: syntax, consecutive dots, typos, disposable domains, and DNS MX lookup.
     */
    public static EmailValidationResult validateEmail(String rawEmail) {
        if (rawEmail == null || rawEmail.trim().isEmpty()) {
            return new EmailValidationResult(false, "", "Email address is required.", null, false);
        }

        String email = rawEmail.trim().toLowerCase();
        if (email.length() > 120) {
            return new EmailValidationResult(false, email, "Email address is too long.", null, false);
        }
        if (email.contains("..")) {
            return new EmailValidationResult(false, email, "Email address cannot contain consecutive dots.", null, false);
        }
        if (!STRICT_EMAIL_PATTERN.matcher(email).matches()) {
            return new EmailValidationResult(false, email, "Invalid email format (e.g. name@domain.com).", null, false);
        }

        int atIdx = email.indexOf('@');
        String local = email.substring(0, atIdx);
        String domain = email.substring(atIdx + 1);

        if (DOMAIN_TYPOS.containsKey(domain)) {
            String fixed = local + "@" + DOMAIN_TYPOS.get(domain);
            return new EmailValidationResult(false, email, "Did you mean " + fixed + "?", fixed, false);
        }

        if (DISPOSABLE_DOMAINS.contains(domain)) {
            return new EmailValidationResult(false, email, "Disposable temporary email domains are not allowed.", null, false);
        }

        if (DEMO_SAFE_DOMAINS.contains(domain)) {
            return new EmailValidationResult(true, email, "Valid email address.", null, true);
        }

        boolean mxOk = hasMailRecord(domain);
        if (!mxOk) {
            return new EmailValidationResult(false, email, "Email domain '" + domain + "' does not appear to accept mail (no MX/DNS record found).", null, false);
        }

        return new EmailValidationResult(true, email, "Valid & deliverable email domain verified.", null, true);
    }

    private static boolean hasMailRecord(String domain) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", "3000");
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(domain, new String[]{"MX"});
            if (attrs != null && attrs.get("MX") != null && attrs.get("MX").size() > 0) {
                return true;
            }
            Attributes aAttrs = ctx.getAttributes(domain, new String[]{"A"});
            if (aAttrs != null && aAttrs.get("A") != null && aAttrs.get("A").size() > 0) {
                return true;
            }
        } catch (Exception ignored) {
            try {
                InetAddress addr = InetAddress.getByName(domain);
                return addr != null;
            } catch (Exception ignored2) {
                return false;
            }
        }
        return false;
    }

    /**
     * Sends a 6-digit password reset OTP email to the recipient's respective email address.
     * Supports SMTP (STARTTLS 587 / SSL 465) and Resend HTTP API via .env configuration.
     */
    public static EmailSendResult sendOtpEmail(String toEmail, String recipientName, String otp) {
        Map<String, String> cfg = loadDotEnv();
        String resendKey = getConfig("RESEND_API_KEY", cfg);
        String smtpHost = getConfig("SMTP_HOST", cfg);
        String smtpPortStr = getConfig("SMTP_PORT", cfg);
        String smtpUser = getConfig("SMTP_USER", cfg);
        String smtpPass = getConfig("SMTP_PASS", cfg);
        String smtpFrom = getConfig("SMTP_FROM", cfg);

        String displayName = (recipientName != null && !recipientName.trim().isEmpty()) ? recipientName.trim() : "Valued User";
        String subject = "Your Password Reset OTP - Real Estate Management System";
        String htmlBody = buildOtpHtmlEmail(displayName, otp);

        // Option 1: Resend API
        if (resendKey != null && !resendKey.isEmpty()) {
            try {
                String fromAddr = (smtpFrom != null && !smtpFrom.isEmpty()) ? smtpFrom : "Real Estate <onboarding@resend.dev>";
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("from", fromAddr);
                payload.put("to", Collections.singletonList(toEmail));
                payload.put("subject", subject);
                payload.put("html", htmlBody);

                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + resendKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.toJson(payload), StandardCharsets.UTF_8))
                    .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    System.out.println("[EmailService] OTP email sent via Resend to " + toEmail);
                    return new EmailSendResult(true, "RESEND_SENT", "A 6-digit OTP has been sent to " + maskEmail(toEmail) + ".");
                }
                System.err.println("[EmailService] Resend API error (" + resp.statusCode() + "): " + resp.body());
            } catch (Exception e) {
                System.err.println("[EmailService] Resend send failed: " + e.getMessage());
            }
        }

        // Option 2: Direct SMTP (STARTTLS on 587 or SSL on 465)
        if (smtpHost != null && !smtpHost.isEmpty() && smtpUser != null && !smtpUser.isEmpty() && smtpPass != null && !smtpPass.isEmpty()) {
            int port = 587;
            try { if (smtpPortStr != null && !smtpPortStr.isEmpty()) port = Integer.parseInt(smtpPortStr); } catch (Exception ignored) {}
            String fromAddr = (smtpFrom != null && !smtpFrom.isEmpty()) ? smtpFrom : smtpUser;
            try {
                sendViaSmtp(smtpHost, port, smtpUser, smtpPass, fromAddr, toEmail, subject, htmlBody);
                System.out.println("[EmailService] OTP email sent via SMTP (" + smtpHost + ") to " + toEmail);
                return new EmailSendResult(true, "SMTP_SENT", "A 6-digit OTP has been sent to " + maskEmail(toEmail) + ". Please check your inbox (and spam folder).");
            } catch (Exception e) {
                System.err.println("[EmailService] SMTP send failed: " + e.getMessage());
                return new EmailSendResult(false, "DEMO_FALLBACK",
                    "SMTP delivery failed (" + e.getMessage() + "). Verify SMTP_USER/SMTP_PASS in .env. Temporary dev OTP provided below.");
            }
        }

        // Fallback when SMTP is not configured in .env yet
        System.out.println("=================================================================");
        System.out.println("[OTP EMAIL - SMTP NOT CONFIGURED IN .env]");
        System.out.println("  To      : " + toEmail);
        System.out.println("  OTP Code: " + otp + " (Valid for 5 minutes)");
        System.out.println("  Tip     : Set SMTP_USER and SMTP_PASS (Gmail App Password) in .env");
        System.out.println("=================================================================");
        return new EmailSendResult(false, "DEMO_FALLBACK",
            "OTP generated for " + maskEmail(toEmail) + ". Configure SMTP_USER & SMTP_PASS in .env for live inbox delivery.");
    }

    private static void sendViaSmtp(String host, int port, String user, String pass, String from, String to, String subject, String htmlBody) throws IOException {
        Socket socket = null;
        try {
            if (port == 465) {
                socket = SSLSocketFactory.getDefault().createSocket(host, port);
            } else {
                socket = new Socket(host, port);
            }
            socket.setSoTimeout(15000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            expectCode(reader, "220");
            sendCmd(writer, reader, "EHLO localhost", "250");

            if (port != 465) {
                sendCmd(writer, reader, "STARTTLS", "220");
                SSLSocket tlsSocket = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                    .createSocket(socket, host, port, true);
                tlsSocket.startHandshake();
                socket = tlsSocket;
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                sendCmd(writer, reader, "EHLO localhost", "250");
            }

            sendCmd(writer, reader, "AUTH LOGIN", "334");
            sendCmd(writer, reader, Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8)), "334");
            sendCmd(writer, reader, Base64.getEncoder().encodeToString(pass.getBytes(StandardCharsets.UTF_8)), "235");

            String cleanFrom = extractEmail(from);
            sendCmd(writer, reader, "MAIL FROM:<" + cleanFrom + ">", "250");
            sendCmd(writer, reader, "RCPT TO:<" + to + ">", "250");
            sendCmd(writer, reader, "DATA", "354");

            StringBuilder msg = new StringBuilder();
            msg.append("From: Real Estate Management <").append(cleanFrom).append(">\r\n");
            msg.append("To: <").append(to).append(">\r\n");
            msg.append("Subject: ").append(subject).append("\r\n");
            msg.append("MIME-Version: 1.0\r\n");
            msg.append("Content-Type: text/html; charset=UTF-8\r\n");
            msg.append("Content-Transfer-Encoding: base64\r\n\r\n");
            String b64Body = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(htmlBody.getBytes(StandardCharsets.UTF_8));
            msg.append(b64Body).append("\r\n.\r\n");

            writer.write(msg.toString());
            writer.flush();
            expectCode(reader, "250");

            try { sendCmd(writer, reader, "QUIT", "221"); } catch (Exception ignored) {}
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static void sendCmd(BufferedWriter writer, BufferedReader reader, String cmd, String expectedPrefix) throws IOException {
        writer.write(cmd + "\r\n");
        writer.flush();
        expectCode(reader, expectedPrefix);
    }

    private static void expectCode(BufferedReader reader, String expectedPrefix) throws IOException {
        String line;
        String lastLine = null;
        while ((line = reader.readLine()) != null) {
            lastLine = line;
            if (line.length() < 4 || line.charAt(3) != '-') {
                break;
            }
        }
        if (lastLine == null || !lastLine.startsWith(expectedPrefix)) {
            throw new IOException("SMTP error response: " + lastLine);
        }
    }

    private static String extractEmail(String from) {
        int lt = from.indexOf('<');
        int gt = from.indexOf('>');
        if (lt >= 0 && gt > lt) return from.substring(lt + 1, gt).trim();
        return from.trim();
    }

    private static String buildOtpHtmlEmail(String name, String otp) {
        return "<!DOCTYPE html><html><body style='margin:0;padding:24px;background:#f4f7f5;font-family:Segoe UI,Arial,sans-serif;'>" +
               "<div style='max-width:520px;margin:0 auto;background:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #e5e7eb;'>" +
               "<div style='background:#0a0a0a;padding:24px 28px;color:#ffffff;'>" +
               "<span style='color:#10b981;font-weight:800;font-size:20px;letter-spacing:0.5px;'>REAL ESTATE</span>" +
               "<div style='color:#a1a1aa;font-size:12px;margin-top:4px;'>Find. Buy. Sell. Rent. Build.</div>" +
               "</div>" +
               "<div style='padding:28px;color:#111111;'>" +
               "<h2 style='margin:0 0 12px 0;font-size:20px;color:#0a0a0a;'>Password Reset Verification</h2>" +
               "<p style='margin:0 0 18px 0;color:#3f3f46;font-size:14px;line-height:1.6;'>Hello " + escapeHtml(name) + ",<br>Use the 6-digit One-Time Password (OTP) below to reset your password:</p>" +
               "<div style='background:#ecfdf5;border:2px dashed #059669;border-radius:12px;padding:18px;text-align:center;margin:20px 0;'>" +
               "<span style='font-size:34px;font-weight:800;letter-spacing:10px;color:#065f46;font-family:monospace;'>" + otp + "</span>" +
               "</div>" +
               "<p style='margin:0 0 8px 0;color:#52525b;font-size:13px;'>This OTP is valid for <strong>5 minutes</strong>. Do not share this code with anyone.</p>" +
               "<p style='margin:0;color:#71717a;font-size:12px;'>If you did not request a password reset, you can safely ignore this email.</p>" +
               "</div>" +
               "<div style='background:#0a0a0a;padding:14px 28px;color:#71717a;font-size:11px;text-align:center;'>&copy; 2026 Real Estate Management System</div>" +
               "</div></body></html>";
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) return local.charAt(0) + "***" + domain;
        return local.substring(0, 2) + "***" + domain;
    }

    private static String getConfig(String key, Map<String, String> dotEnv) {
        String envVal = System.getenv(key);
        if (envVal != null && !envVal.trim().isEmpty()) return envVal.trim();
        String fileVal = dotEnv.get(key);
        return fileVal != null ? fileVal.trim() : null;
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> map = new HashMap<>();
        File envFile = new File(".env");
        if (!envFile.exists() || !envFile.isFile()) return map;
        try (BufferedReader reader = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String k = line.substring(0, eq).trim();
                    String v = line.substring(eq + 1).trim();
                    if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                        v = v.substring(1, v.length() - 1);
                    }
                    map.put(k, v);
                }
            }
        } catch (IOException ignored) {}
        return map;
    }
}
