-- Added price fields for connectors.
ALTER TABLE `charge_box`
  ALTER COLUMN connector_1_price SET DEFAULT 0.00;

ALTER TABLE `charge_box`
  ALTER COLUMN connector_2_price SET DEFAULT 0.00;

ALTER TABLE `charge_box`
  ALTER COLUMN connector_3_price SET DEFAULT 0.00;

