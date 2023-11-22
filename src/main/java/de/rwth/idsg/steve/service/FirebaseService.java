package de.rwth.idsg.steve.service;


import de.rwth.idsg.steve.SteveConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

import static de.rwth.idsg.steve.SteveConfiguration.CONFIG;

@Slf4j
@Service
public class FirebaseService {
    private FirebaseDatabase firebaseDatabase;

    @PostConstruct
    public void initialize() {
        try {
            FileInputStream serviceAccount = new FileInputStream("serviceAccountKey.json");

            FirebaseOptions options = new FirebaseOptions.Builder()
//                    .setProjectId("test2-eco-rozetka")
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setDatabaseUrl(CONFIG.getFirebase().getDatabaseUrl())
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }

            firebaseDatabase = FirebaseDatabase.getInstance();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeToActiveTransaction(String IdActiveTransaction , Map<String, Object> activeTransactionData) {
        DatabaseReference reference = firebaseDatabase.getReference();

        reference
            .child("active_transactions")
            .child(IdActiveTransaction)
            .setValueAsync(activeTransactionData);
    }
}
