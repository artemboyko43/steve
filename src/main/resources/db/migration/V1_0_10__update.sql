-- Added price fields for 4 connector.
ALTER TABLE `charge_box`
  ADD connector_4_price REAL(12,2) NOT NULL DEFAULT 0.00;