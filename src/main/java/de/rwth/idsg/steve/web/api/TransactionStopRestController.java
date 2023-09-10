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
package de.rwth.idsg.steve.web.api;

import de.rwth.idsg.steve.ocpp.OcppTransport;
import de.rwth.idsg.steve.repository.TransactionRepository;
import de.rwth.idsg.steve.repository.dto.ChargePointSelect;
import de.rwth.idsg.steve.service.ChargePointService16_Client;
import de.rwth.idsg.steve.web.api.ApiControllerAdvice.ApiErrorResponse;
import de.rwth.idsg.steve.web.dto.RemoteStartTransaction;
import de.rwth.idsg.steve.web.dto.ocpp.RemoteStartTransactionParams;
import de.rwth.idsg.steve.web.dto.ocpp.RemoteStopTransactionParams;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Sevket Goekay <sevketgokay@gmail.com>
 * @since 13.09.2022
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/v1/transaction_stop", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class TransactionStopRestController {

    private final TransactionRepository transactionRepository;

//    private final CentralSystemService16_Service centralSystemService16Service;

    private final ChargePointService16_Client chargePointService16Client;

    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK"),
        @ApiResponse(code = 201, message = "Created"),
        @ApiResponse(code = 400, message = "Bad Request", response = ApiErrorResponse.class),
        @ApiResponse(code = 401, message = "Unauthorized", response = ApiErrorResponse.class),
        @ApiResponse(code = 422, message = "Unprocessable Entity", response = ApiErrorResponse.class),
        @ApiResponse(code = 404, message = "Not Found", response = ApiErrorResponse.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ApiErrorResponse.class)}
    )
    @PostMapping
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public int post(@RequestBody @Valid RemoteStartTransaction params) {
        // log.debug("Read request for query: {}", params);

        var activeTransactions = transactionRepository.getActiveTransactionByConnectorIds(
            params.getChargeBoxId(),
            params.getConnectorId()
        );

        // Check if transaction already exist and active.
        if (activeTransactions.size() == 1) {
            int transactionId = activeTransactions.get(0);
            RemoteStopTransactionParams remoteStopTransactionParams = new RemoteStopTransactionParams();
            remoteStopTransactionParams.setTransactionId(transactionId);

            List<ChargePointSelect> chargePointSelectList = new ArrayList<>();
            ChargePointSelect chargePointSelectListItem = new ChargePointSelect(OcppTransport.fromValue("J"), params.getChargeBoxId());
            chargePointSelectList.add(chargePointSelectListItem);

            remoteStopTransactionParams.setChargePointSelectList(chargePointSelectList);

            // Stop transaction by charge point client.
            chargePointService16Client.remoteStopTransaction(remoteStopTransactionParams);

            // @todo perhaps implement stopping for transaction in mysql after testing with real charger.

            return 1;
        }
        else {
            // Transaction already exist and active return zero.
            return 0;
        }
    }
}
