CREATE TABLE IF NOT EXISTS roles (
  id BIGINT UNSIGNED PRIMARY KEY,
  code VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT UNSIGNED PRIMARY KEY,
  role_id BIGINT UNSIGNED NOT NULL,
  username VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(200) NOT NULL,
  language VARCHAR(10) NOT NULL DEFAULT 'en',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  last_login_at DATETIME(3) NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uq_users_username (username),
  CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS farmers (
  id BIGINT UNSIGNED PRIMARY KEY,
  farmer_no VARCHAR(50) NOT NULL,
  name VARCHAR(200) NOT NULL,
  phone VARCHAR(50) NOT NULL DEFAULT '',
  village VARCHAR(200) NOT NULL,
  id_number VARCHAR(100) NOT NULL DEFAULT '',
  photo_object_key VARCHAR(500) NULL,
  notes TEXT NULL,
  created_by BIGINT UNSIGNED NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uq_farmers_no (farmer_no),
  KEY idx_farmers_name (name),
  KEY idx_farmers_phone (phone),
  CONSTRAINT fk_farmers_creator FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS products (
  id BIGINT UNSIGNED PRIMARY KEY,
  code VARCHAR(50) NOT NULL,
  name_en VARCHAR(200) NOT NULL,
  name_fr VARCHAR(200) NOT NULL,
  default_unit VARCHAR(30) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uq_products_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS quality_indicators (
  id BIGINT UNSIGNED PRIMARY KEY,
  product_id BIGINT UNSIGNED NOT NULL,
  code VARCHAR(50) NOT NULL,
  name_en VARCHAR(200) NOT NULL,
  name_fr VARCHAR(200) NOT NULL,
  data_type VARCHAR(20) NOT NULL,
  unit VARCHAR(30) NOT NULL DEFAULT '',
  required BOOLEAN NOT NULL DEFAULT FALSE,
  options_json JSON NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  UNIQUE KEY uq_indicators_product_code (product_id, code),
  CONSTRAINT fk_indicators_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS purchases (
  id BIGINT UNSIGNED PRIMARY KEY,
  purchase_no VARCHAR(50) NOT NULL,
  farmer_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  purchase_date DATE NOT NULL,
  weight DECIMAL(14,3) NOT NULL,
  unit VARCHAR(30) NOT NULL,
  bag_count INT NULL,
  notes TEXT NULL,
  status VARCHAR(20) NOT NULL,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NULL,
  updated_at DATETIME(3) NULL,
  confirmed_at DATETIME(3) NULL,
  voided_at DATETIME(3) NULL,
  voided_by BIGINT UNSIGNED NULL,
  void_reason TEXT NULL,
  UNIQUE KEY uq_purchases_no (purchase_no),
  KEY idx_purchases_date (purchase_date),
  KEY idx_purchases_farmer_date (farmer_id, purchase_date),
  KEY idx_purchases_product_date (product_id, purchase_date),
  KEY idx_purchases_status (status),
  CONSTRAINT fk_purchases_farmer FOREIGN KEY (farmer_id) REFERENCES farmers(id),
  CONSTRAINT fk_purchases_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT fk_purchases_creator FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_purchases_voider FOREIGN KEY (voided_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS purchase_quality_values (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  purchase_id BIGINT UNSIGNED NOT NULL,
  indicator_id BIGINT UNSIGNED NOT NULL,
  value_text VARCHAR(500) NOT NULL,
  indicator_name_snapshot VARCHAR(200) NOT NULL,
  unit_snapshot VARCHAR(30) NOT NULL DEFAULT '',
  created_at DATETIME(3) NULL,
  KEY idx_quality_purchase (purchase_id),
  KEY idx_quality_indicator (indicator_id),
  CONSTRAINT fk_quality_purchase FOREIGN KEY (purchase_id) REFERENCES purchases(id) ON DELETE CASCADE,
  CONSTRAINT fk_quality_indicator FOREIGN KEY (indicator_id) REFERENCES quality_indicators(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS purchase_images (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  purchase_id BIGINT UNSIGNED NOT NULL,
  object_key VARCHAR(500) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NULL,
  KEY idx_images_purchase (purchase_id),
  CONSTRAINT fk_images_purchase FOREIGN KEY (purchase_id) REFERENCES purchases(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS purchase_signatures (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  purchase_id BIGINT UNSIGNED NOT NULL,
  object_key VARCHAR(500) NOT NULL,
  signed_at DATETIME(3) NULL,
  created_at DATETIME(3) NULL,
  UNIQUE KEY uq_signatures_purchase (purchase_id),
  CONSTRAINT fk_signatures_purchase FOREIGN KEY (purchase_id) REFERENCES purchases(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
