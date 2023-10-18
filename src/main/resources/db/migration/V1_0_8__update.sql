-- Added price fields for connectors.
ALTER TABLE `charge_box`
  ADD connector_1_price REAL(12,2) NOT NULL;

ALTER TABLE `charge_box`
  ADD connector_2_price REAL(12,2) NOT NULL;

ALTER TABLE `charge_box`
  ADD connector_3_price REAL(12,2) NOT NULL;
