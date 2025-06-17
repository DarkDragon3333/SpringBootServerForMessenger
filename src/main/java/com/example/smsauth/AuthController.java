package com.example.smsauth;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final Map<String, String> codeStorage = new HashMap<>();

    @Value("${smsc.login}")
    private String smscLogin;

    @Value("${smsc.password}")
    private String smscPassword;

    @PostConstruct
    public void initFirebase() throws IOException {
//        FileInputStream serviceAccount = new FileInputStream("src/main/resources/mymessenger-73602-firebase-adminsdk-8ogzt-33709015c0.json");
//        FirebaseOptions options = new FirebaseOptions.Builder()
//                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
//                .build();
//        FirebaseApp.initializeApp(options);

        String firebaseKeyJson = System.getenv("FIREBASE_KEY_JSON");
        if (firebaseKeyJson == null || firebaseKeyJson.isEmpty()) {
            throw new IllegalStateException("FIREBASE_KEY_JSON is not set");
        }

        InputStream serviceAccount = new ByteArrayInputStream(firebaseKeyJson.getBytes(StandardCharsets.UTF_8));
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();

        FirebaseApp.initializeApp(options);

    }

    @PostMapping("/send-code")
    public ResponseEntity<String> sendCode(@RequestBody Map<String, String> request) throws IOException, InterruptedException {
        String phone = request.get("phone");
        String code = String.valueOf((int)(Math.random() * 900000) + 100000);
        codeStorage.put(phone, code);

        String url = String.format(
                "https://smsc.ru/sys/send.php?login=%s&psw=%s&phones=%s&mes=%s&fmt=3",
                URLEncoder.encode(smscLogin, StandardCharsets.UTF_8),
                URLEncoder.encode(smscPassword, StandardCharsets.UTF_8),
                URLEncoder.encode(phone, StandardCharsets.UTF_8),
                URLEncoder.encode("Ваш код: " + code, StandardCharsets.UTF_8)
        );

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest smsRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();
        client.send(smsRequest, HttpResponse.BodyHandlers.ofString());

        return ResponseEntity.ok("Код отправлен: " + codeStorage.get(phone));
    }

    @PostMapping("/verify-code")
    public ResponseEntity<?> verifyCode(@RequestBody Map<String, String> request) throws FirebaseAuthException {
        String phone = request.get("phone");
        String code = request.get("code");

        if (!code.equals(codeStorage.get(phone))) {
            return ResponseEntity.status(401).body("Неверный код");
        }

        String uid = "phone:" + phone;
        String customToken = FirebaseAuth.getInstance().createCustomToken(uid);
        return ResponseEntity.ok(Map.of("token", customToken));
    }
}
