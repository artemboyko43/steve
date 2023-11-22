/*
 * SteVe - SteckdosenVerwaltung - https://github.com/steve-community/steve
 * Copyright (C) 2013-2023 SteVe Community Team
 * All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.rwth.idsg.steve.service;

import de.rwth.idsg.steve.ocpp.OcppProtocol;
import de.rwth.idsg.steve.ocpp.OcppTransport;
import de.rwth.idsg.steve.repository.*;
import de.rwth.idsg.steve.repository.dto.*;
import de.rwth.idsg.steve.repository.dto.TransactionDetails.MeterValues;
import de.rwth.idsg.steve.service.notification.OccpStationBooted;
import de.rwth.idsg.steve.service.notification.OcppStationStatusFailure;
import de.rwth.idsg.steve.service.notification.OcppTransactionEnded;
import de.rwth.idsg.steve.service.notification.OcppTransactionStarted;
import de.rwth.idsg.steve.web.dto.OcppTagForm;
import de.rwth.idsg.steve.web.dto.ocpp.RemoteStopTransactionParams;
import jooq.steve.db.enums.TransactionStopEventActor;
import lombok.extern.slf4j.Slf4j;
import ocpp.cs._2015._10.AuthorizationStatus;
import ocpp.cs._2015._10.AuthorizeRequest;
import ocpp.cs._2015._10.AuthorizeResponse;
import ocpp.cs._2015._10.BootNotificationRequest;
import ocpp.cs._2015._10.BootNotificationResponse;
import ocpp.cs._2015._10.ChargePointStatus;
import ocpp.cs._2015._10.DataTransferRequest;
import ocpp.cs._2015._10.DataTransferResponse;
import ocpp.cs._2015._10.DataTransferStatus;
import ocpp.cs._2015._10.DiagnosticsStatusNotificationRequest;
import ocpp.cs._2015._10.DiagnosticsStatusNotificationResponse;
import ocpp.cs._2015._10.FirmwareStatusNotificationRequest;
import ocpp.cs._2015._10.FirmwareStatusNotificationResponse;
import ocpp.cs._2015._10.HeartbeatRequest;
import ocpp.cs._2015._10.HeartbeatResponse;
import ocpp.cs._2015._10.IdTagInfo;
import ocpp.cs._2015._10.MeterValuesRequest;
import ocpp.cs._2015._10.MeterValuesResponse;
import ocpp.cs._2015._10.RegistrationStatus;
import ocpp.cs._2015._10.StartTransactionRequest;
import ocpp.cs._2015._10.StartTransactionResponse;
import ocpp.cs._2015._10.StatusNotificationRequest;
import ocpp.cs._2015._10.StatusNotificationResponse;
import ocpp.cs._2015._10.StopTransactionRequest;
import ocpp.cs._2015._10.StopTransactionResponse;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * @author Sevket Goekay <sevketgokay@gmail.com>
 * @since 13.03.2018
 */
@Slf4j
@Service
public class CentralSystemService16_Service {

    @Autowired private OcppServerRepository ocppServerRepository;
    @Autowired private SettingsRepository settingsRepository;
    @Autowired private TransactionRepository transactionRepository;

    @Autowired private FirebaseService firebaseService;

    @Autowired private ChargePointRepository chargePointRepository;

    @Autowired private OcppTagService ocppTagService;
    @Autowired private ApplicationEventPublisher applicationEventPublisher;
    @Autowired private ChargePointHelperService chargePointHelperService;

    @Autowired private ChargePointService16_Client chargePointService16Client;

    public BootNotificationResponse bootNotification(BootNotificationRequest parameters, String chargeBoxIdentity,
                                                     OcppProtocol ocppProtocol) {

        Optional<RegistrationStatus> status = chargePointHelperService.getRegistrationStatus(chargeBoxIdentity);
        applicationEventPublisher.publishEvent(new OccpStationBooted(chargeBoxIdentity, status));
        DateTime now = DateTime.now();

        if (status.isEmpty()) {
            // Applies only to stations not in db (regardless of the registration_status field from db)
            log.error("The chargebox '{}' is NOT in database.", chargeBoxIdentity);
        } else {
            // Applies to all stations in db (even with registration_status Rejected)
            log.info("The boot of the chargebox '{}' with registration status '{}' is acknowledged.", chargeBoxIdentity, status);
            UpdateChargeboxParams params =
                    UpdateChargeboxParams.builder()
                                         .ocppProtocol(ocppProtocol)
                                         .vendor(parameters.getChargePointVendor())
                                         .model(parameters.getChargePointModel())
                                         .pointSerial(parameters.getChargePointSerialNumber())
                                         .boxSerial(parameters.getChargeBoxSerialNumber())
                                         .fwVersion(parameters.getFirmwareVersion())
                                         .iccid(parameters.getIccid())
                                         .imsi(parameters.getImsi())
                                         .meterType(parameters.getMeterType())
                                         .meterSerial(parameters.getMeterSerialNumber())
                                         .chargeBoxId(chargeBoxIdentity)
                                         .heartbeatTimestamp(now)
                                         .build();

            ocppServerRepository.updateChargebox(params);
        }

        return new BootNotificationResponse()
                .withStatus(status.orElse(RegistrationStatus.REJECTED))
                .withCurrentTime(now)
                .withInterval(settingsRepository.getHeartbeatIntervalInSeconds());
    }

    public FirmwareStatusNotificationResponse firmwareStatusNotification(
            FirmwareStatusNotificationRequest parameters, String chargeBoxIdentity) {
        String status = parameters.getStatus().value();
        ocppServerRepository.updateChargeboxFirmwareStatus(chargeBoxIdentity, status);
        return new FirmwareStatusNotificationResponse();
    }

    public StatusNotificationResponse statusNotification(
            StatusNotificationRequest parameters, String chargeBoxIdentity) {
        // Optional field
        DateTime timestamp = parameters.isSetTimestamp() ? parameters.getTimestamp() : DateTime.now();

        InsertConnectorStatusParams params =
                InsertConnectorStatusParams.builder()
                                           .chargeBoxId(chargeBoxIdentity)
                                           .connectorId(parameters.getConnectorId())
                                           .status(parameters.getStatus().value())
                                           .errorCode(parameters.getErrorCode().value())
                                           .timestamp(timestamp)
                                           .errorInfo(parameters.getInfo())
                                           .vendorId(parameters.getVendorId())
                                           .vendorErrorCode(parameters.getVendorErrorCode())
                                           .build();

        ocppServerRepository.insertConnectorStatus(params);

        if (parameters.getStatus() == ChargePointStatus.FAULTED) {
            applicationEventPublisher.publishEvent(new OcppStationStatusFailure(
                    chargeBoxIdentity, parameters.getConnectorId(), parameters.getErrorCode().value()));
        }

        return new StatusNotificationResponse();
    }

    public MeterValuesResponse meterValues(MeterValuesRequest parameters, String chargeBoxIdentity) throws ExecutionException, InterruptedException, TimeoutException {
        ocppServerRepository.insertMeterValues(
                chargeBoxIdentity,
                parameters.getMeterValue(),
                parameters.getConnectorId(),
                parameters.getTransactionId()
        );

        TransactionDetails transactionDetail = transactionRepository.getDetails(parameters.getTransactionId());
        var transactionMeterValues = transactionDetail.getValues();
        // Logic for stopping transaction by balance
        // 1. Get Different in prev and current MeterValue from Energy.Active.Import.Register type Measurand.
        if (transactionMeterValues.size() > 0) {
            String ocppIdTag = transactionDetail.getTransaction().getOcppIdTag();
            int connectorId = (int) transactionDetail.getTransaction().getConnectorId();

            int meterValueSize = transactionMeterValues.size();
            float currentValue = 0;
            float connectorPrice = 0;
            boolean foundCurrentValue = false;
            boolean foundPreviousValue = false;
            float previousValue = 0;
            String nameMeasure = "Energy.Active.Import.Register";

            for (int i = meterValueSize - 1; i >= 0; i--) {
                log.info("i=debug");
                log.info(Integer.toString(i));

                if (foundCurrentValue == true && foundPreviousValue == false && transactionMeterValues.get(i).getMeasurand().equals(nameMeasure)) {
                    previousValue = Float.parseFloat(transactionMeterValues.get(i).getValue());
                    foundPreviousValue = true;
                }
                if (foundCurrentValue == false && transactionMeterValues.get(i).getMeasurand().equals(nameMeasure)) {
                    currentValue = Float.parseFloat(transactionMeterValues.get(i).getValue());
                    foundCurrentValue = true;
                }

                if (foundCurrentValue == true && foundPreviousValue == true) {
                    break;
                }
            }

            // float currentValue = Float.parseFloat(
            //     transactionMeterValues.get(transactionMeterValues.size() - 1).getValue());

            log.info("currentValue after loop");
            log.info(Float.toString(currentValue));

            log.info("previousValue after loop");
            log.info(Float.toString(previousValue));

            // float previousValue = Float.parseFloat(
            //     transactionMeterValues.get(transactionMeterValues.size() - 2).getValue());

//            log.info("transactionMeterValues.get(transactionMeterValues.size() - 2).getMeasurand()");
//            log.info(transactionMeterValues.get(transactionMeterValues.size() - 2).getMeasurand().toString());

            if (foundCurrentValue == true && foundPreviousValue == true) {

                Double connectorPriceSource = (Double) chargePointRepository.getChargePointPrices(chargeBoxIdentity)
                        .get(connectorId - 1);
                connectorPrice = connectorPriceSource.floatValue();

                if (connectorPrice > 0) {
                    float differenceValue = (currentValue - previousValue) / 1000 * connectorPrice;

                    log.info("different value =");
                    log.info(Float.toString(differenceValue));

                    double idTagBalance = ocppTagService.getBalance(ocppIdTag);

                    if (differenceValue < idTagBalance) {
                        // 2. Update OcppTag decrease balance of Kwt.
                        ocppTagService.decreaseBalanceOcppTag(ocppIdTag, differenceValue);

                        Map<String, Object> dataTransaction = new HashMap<>();
                        dataTransaction.put("alreadyCharged", Float.toString(currentValue));
                        dataTransaction.put("idTag", ocppIdTag);
                        dataTransaction.put("connectorId", connectorId);
                        dataTransaction.put("chargeBoxId", chargeBoxIdentity);

                        String idActiveTransaction = chargeBoxIdentity + "_" + connectorId;
                        // Write to firebase.
                        firebaseService.writeToActiveTransaction(idActiveTransaction, dataTransaction);
                    } else {
                        ocppTagService.decreaseBalanceOcppTag(ocppIdTag, differenceValue);

                        // 3. Stop Transaction by OcppClient by Remote Stop transaction task.
                        RemoteStopTransactionParams remoteStopTransactionParams = new RemoteStopTransactionParams();
                        remoteStopTransactionParams.setTransactionId(parameters.getTransactionId());

                        List<ChargePointSelect> chargePointSelectList = new ArrayList<>();
                        ChargePointSelect chargePointSelectListItem = new ChargePointSelect(OcppTransport.fromValue("J"), chargeBoxIdentity);
                        chargePointSelectList.add(chargePointSelectListItem);

                        remoteStopTransactionParams.setChargePointSelectList(chargePointSelectList);

                        // Stop transaction by charge point client.
                        chargePointService16Client.remoteStopTransaction(remoteStopTransactionParams);
                    }
                }
            }
        }

        return new MeterValuesResponse();
    }

    public DiagnosticsStatusNotificationResponse diagnosticsStatusNotification(
            DiagnosticsStatusNotificationRequest parameters, String chargeBoxIdentity) {
        String status = parameters.getStatus().value();
        ocppServerRepository.updateChargeboxDiagnosticsStatus(chargeBoxIdentity, status);
        return new DiagnosticsStatusNotificationResponse();
    }

    public StartTransactionResponse startTransaction(StartTransactionRequest parameters, String chargeBoxIdentity) {
        // Get the authorization info of the user, before making tx changes (will affectAuthorizationStatus)
        IdTagInfo info = ocppTagService.getIdTagInfo(
                parameters.getIdTag(),
                true,
                () -> new IdTagInfo().withStatus(AuthorizationStatus.INVALID) // IdTagInfo is required
        );

        InsertTransactionParams params =
                InsertTransactionParams.builder()
                                       .chargeBoxId(chargeBoxIdentity)
                                       .connectorId(parameters.getConnectorId())
                                       .idTag(parameters.getIdTag())
                                       .startTimestamp(parameters.getTimestamp())
                                       .startMeterValue(Integer.toString(parameters.getMeterStart()))
                                       .reservationId(parameters.getReservationId())
                                       .eventTimestamp(DateTime.now())
                                       .build();

        int transactionId = ocppServerRepository.insertTransaction(params);

        applicationEventPublisher.publishEvent(new OcppTransactionStarted(transactionId, params));

        return new StartTransactionResponse()
                .withIdTagInfo(info)
                .withTransactionId(transactionId);
    }

    public StopTransactionResponse stopTransaction(StopTransactionRequest parameters, String chargeBoxIdentity) {
        int transactionId = parameters.getTransactionId();
        String stopReason = parameters.isSetReason() ? parameters.getReason().value() : null;

        // Get the authorization info of the user, before making tx changes (will affectAuthorizationStatus)
        IdTagInfo idTagInfo = ocppTagService.getIdTagInfo(
                parameters.getIdTag(),
                false,
                () -> null
        );

        UpdateTransactionParams params =
                UpdateTransactionParams.builder()
                                       .chargeBoxId(chargeBoxIdentity)
                                       .transactionId(transactionId)
                                       .stopTimestamp(parameters.getTimestamp())
                                       .stopMeterValue(Integer.toString(parameters.getMeterStop()))
                                       .stopReason(stopReason)
                                       .eventTimestamp(DateTime.now())
                                       .eventActor(TransactionStopEventActor.station)
                                       .build();

        ocppServerRepository.updateTransaction(params);

        ocppServerRepository.insertMeterValues(chargeBoxIdentity, parameters.getTransactionData(), transactionId);

        applicationEventPublisher.publishEvent(new OcppTransactionEnded(params));

        return new StopTransactionResponse().withIdTagInfo(idTagInfo);
    }

    public HeartbeatResponse heartbeat(HeartbeatRequest parameters, String chargeBoxIdentity) {
        DateTime now = DateTime.now();
        ocppServerRepository.updateChargeboxHeartbeat(chargeBoxIdentity, now);

        return new HeartbeatResponse().withCurrentTime(now);
    }

    public AuthorizeResponse authorize(AuthorizeRequest parameters, String chargeBoxIdentity) {
        // Get the authorization info of the user
        IdTagInfo idTagInfo = ocppTagService.getIdTagInfo(
                parameters.getIdTag(),
                false,
                () -> new IdTagInfo().withStatus(AuthorizationStatus.INVALID)
        );

        return new AuthorizeResponse().withIdTagInfo(idTagInfo);
    }

    /**
     * Dummy implementation. This is new in OCPP 1.5. It must be vendor-specific.
     */
    public DataTransferResponse dataTransfer(DataTransferRequest parameters, String chargeBoxIdentity) {
        log.info("[Data Transfer] Charge point: {}, Vendor Id: {}", chargeBoxIdentity, parameters.getVendorId());
        if (parameters.isSetMessageId()) {
            log.info("[Data Transfer] Message Id: {}", parameters.getMessageId());
        }
        if (parameters.isSetData()) {
            log.info("[Data Transfer] Data: {}", parameters.getData());
        }

        // OCPP requires a status to be set. Since this is a dummy impl, set it to "Accepted".
        // https://github.com/steve-community/steve/pull/36
        return new DataTransferResponse().withStatus(DataTransferStatus.ACCEPTED);
    }
}
