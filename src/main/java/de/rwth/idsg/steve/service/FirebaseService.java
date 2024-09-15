package de.rwth.idsg.steve.service;


import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import de.rwth.idsg.steve.SteveConfiguration;
import de.rwth.idsg.steve.repository.ChargePointRepository;
import de.rwth.idsg.steve.repository.TransactionRepository;
import de.rwth.idsg.steve.repository.dto.InsertTransactionParams;
import de.rwth.idsg.steve.repository.dto.TransactionDetails;
import de.rwth.idsg.steve.repository.dto.UpdateTransactionParams;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

import static de.rwth.idsg.steve.SteveConfiguration.CONFIG;

@Slf4j
@Service
public class FirebaseService {

    @Autowired private ChargePointRepository chargePointRepository;

    @Autowired private TransactionRepository transactionRepository;

    private FirebaseDatabase firebaseDatabase;

    private Firestore firestore;

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
            firestore = FirestoreClient.getFirestore();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeToActiveTransaction(String IdActiveTransaction , Map<String, Object> activeTransactionData) {
        DatabaseReference reference = firebaseDatabase.getReference();

        reference
            .child("active_transactions")
            .child(IdActiveTransaction)
            .updateChildrenAsync(activeTransactionData);

            // .set(activeTransactionData, SetOptions.merge());
    }

    public void updateBalance(String ocppTagId, double updateBalanceValue) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> querySnapshot = firestore
                .collection("users_data")
                .whereEqualTo("ocppTagId", ocppTagId)
                .get();

        for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
            document.getReference().update("balance", updateBalanceValue);
        }
    }

    // public void updateData(String ocppTagId, String key, String value) {
    //         ApiFuture<QuerySnapshot> querySnapshot = firestore
    //             .collection("users_data")
    //             .whereEqualTo("ocppTagId", ocppTagId)
    //             .get();
            
    //     for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
    //         document.getReference().update(key,
    //     }
    // }

    public void startTransaction(InsertTransactionParams params) {
        Map<String, Object> transactionData = new HashMap<>();
        transactionData.put("chargeBoxId", params.getChargeBoxId());
        transactionData.put("idTag", params.getIdTag());
        transactionData.put("connectorId", params.getConnectorId());
        transactionData.put("transactionStart", params.getStartTimestamp().toString());

        ApiFuture<WriteResult> future = firestore
            .collection("active_transactions")
            .document(
                params.getIdTag() + "_" +
                params.getChargeBoxId() + "_" +
                params.getConnectorId()
            )
            .set(transactionData);

        DatabaseReference reference = firebaseDatabase.getReference();
        reference
            .child("active_transactions")
            .child(
                params.getChargeBoxId() + "_" +
                params.getConnectorId()
            )
            .setValueAsync(transactionData);

        System.out.println("Firestore startTransaction event");
    }

    public void endTransaction(UpdateTransactionParams params) {
        TransactionDetails transactionDetails = transactionRepository.getDetails(params.getTransactionId());
        System.out.println("Firestore endTransaction step 1");
        Map<String, Object> transactionData = new HashMap<>();

        Double connectorPriceSource = (Double) chargePointRepository.getChargePointPrices(params.getChargeBoxId())
                .get(transactionDetails.getTransaction().getConnectorId() - 1);
        Float connectorPrice = connectorPriceSource.floatValue();

        if (transactionDetails.getTransaction().getConnectorId() == 1) {
            transactionData.put("connector1Price", connectorPrice);
        }
        if (transactionDetails.getTransaction().getConnectorId() == 2) {
            transactionData.put("connector2Price", connectorPrice);
        }
        if (transactionDetails.getTransaction().getConnectorId() == 3) {
            transactionData.put("connector3Price", connectorPrice);
        }

        Float amount = -(Float.parseFloat(transactionDetails.getTransaction().getStopValue()) / 1000) * connectorPrice;

        transactionData.put("chargeBoxId", params.getChargeBoxId());
        transactionData.put("ocppIdTag", transactionDetails.getTransaction().getOcppIdTag());
        transactionData.put("connectorId", transactionDetails.getTransaction().getConnectorId());

        transactionData.put("startTimestamp", transactionDetails.getTransaction().getStartTimestamp().toDate());
        transactionData.put("stopTimestamp", params.getStopTimestamp().toDate());
        transactionData.put("dateTime", params.getStopTimestamp().toDate());

        transactionData.put("startValue", transactionDetails.getTransaction().getStartValue());
        transactionData.put("stopValue", transactionDetails.getTransaction().getStopValue());
        transactionData.put("stopReason", transactionDetails.getTransaction().getStopReason());
        transactionData.put("stopEventActor", transactionDetails.getTransaction().getStopEventActor());
        transactionData.put("amount", amount);

        transactionData.put("id", params.getTransactionId());


        System.out.println("Firestore endTransaction step 2");
        System.out.println("Firestore endTransaction step: transaction that we wan to stop" + String.valueOf(params.getTransactionId()));

        ApiFuture<WriteResult> future = firestore
                .collection("transactions")
                .document(
                        String.valueOf(params.getTransactionId())
                )
                .set(transactionData);

        System.out.println("Firestore endTransaction step 3");

        firestore
            .collection("active_transactions")
            .document(
                transactionDetails.getTransaction().getOcppIdTag() + "_" +
                params.getChargeBoxId() + "_" +
                transactionDetails.getTransaction().getConnectorId()
            )
            .delete();

        DatabaseReference reference = firebaseDatabase.getReference();
        reference
            .child("active_transactions")
            .child(
                params.getChargeBoxId() + "_" +
                transactionDetails.getTransaction().getConnectorId()
            )
            .setValueAsync(null);

        System.out.println("Firestore endTransaction event");
    }
}
