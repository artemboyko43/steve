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
package de.rwth.idsg.steve.web.dto;

import de.rwth.idsg.steve.web.validation.IdTag;
import io.swagger.annotations.ApiModelProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.joda.time.LocalDateTime;

import javax.validation.constraints.Future;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Objects;

/**
 * @author Sevket Goekay <sevketgokay@gmail.com>
 * @since 15.08.2014
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class RemoteStartTransaction {


    @ApiModelProperty(value = "Will be used in create/insert flows. Will be ignored in update flows.")
    @NotEmpty(message = "ID Tag is required")
    @IdTag
    private String idTag;

//    @ApiModelProperty(value = "A date/time without timezone. Example: 2022-10-10T09:00")
//    @Future(message = "Expiry Date/Time must be in future")
//    private LocalDateTime expiryDate;

    @ApiModelProperty(value = "Will be used in starting remote start transaction.")
    @NotNull(message= "Connector Id is required")
    private Integer connectorId;

    @ApiModelProperty(value = "Will be used in starting remote start transaction.")
    @NotEmpty(message = "Charge Box Id is required")
    private String chargeBoxId;

    /**
     * As specified in V0_9_9__update.sql default value is 1.
     */
//    public Integer getMaxActiveTransactionCount() {
//        return Objects.requireNonNullElse(maxActiveTransactionCount, 1);
//    }
}
